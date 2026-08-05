package it.unicas.chronogram.auth;

import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.PasswordResetToken;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.repository.PasswordResetTokenRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    private ChronogramProperties props;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        props = new ChronogramProperties();
        props.getReset().setTokenTtlMinutes(30);
        props.getReset().setAppBaseUrl("https://app.local/chronogram");
        props.getReset().setRequestCooldownSeconds(60);
        service = newService();
    }

    private PasswordResetService newService() {
        return new PasswordResetService(
                userAuthRepository, tokenRepository, emailService, passwordEncoder, props);
    }

    private UserAuth activeUser() {
        UserAuth user = new UserAuth();
        user.setUserId(11);
        user.setEmail("ada@example.com");
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    // ---- initiatePasswordReset ----

    @Test
    void initiateCreatesTokenAndSendsEmailUsingConfiguredBaseUrl() {
        UserAuth user = activeUser();
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(11)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        service.initiatePasswordReset("ada@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken token = tokenCaptor.getValue();
        assertThat(token.getUserId()).isEqualTo(11);
        assertThat(token.getSelector()).isNotBlank();
        assertThat(token.getVerifierHash()).isEqualTo("verifier-hash");
        assertThat(token.getExpirationTime()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> fullTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(
                eq("ada@example.com"), fullTokenCaptor.capture(), eq("https://app.local/chronogram"));
        // full token is selector:verifier
        assertThat(fullTokenCaptor.getValue()).contains(":").startsWith(token.getSelector() + ":");
    }

    @Test
    void initiateStripsTrailingSlashFromConfiguredBaseUrl() {
        props.getReset().setAppBaseUrl("https://app.local/chronogram/");
        service = newService();
        UserAuth user = activeUser();
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(11)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        service.initiatePasswordReset("ada@example.com");

        // The link must be <base>/reset-password, never <base>//reset-password.
        verify(emailService).sendPasswordResetEmail(
                anyString(), anyString(), eq("https://app.local/chronogram"));
    }

    @Test
    void initiateFailsWhenBaseUrlIsNotConfigured() {
        props.getReset().setAppBaseUrl("   ");
        service = newService();
        UserAuth user = activeUser();
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(11)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        assertThatThrownBy(() -> service.initiatePasswordReset("ada@example.com"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("app-base-url");
        verifyNoInteractions(emailService);
    }

    @Test
    void initiateReusesExistingTokenRow() {
        UserAuth user = activeUser();
        PasswordResetToken existing = new PasswordResetToken();
        existing.setTokenId(99);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(11)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        service.initiatePasswordReset("ada@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenId()).isEqualTo(99); // same row updated
    }

    @Test
    void initiateThrottledWhenExistingTokenIsWithinCooldown() {
        UserAuth user = activeUser();
        PasswordResetToken recent = new PasswordResetToken();
        recent.setTokenId(99);
        recent.setUserId(11);
        // Issued 10s ago; cooldown is 60s, so this request must be ignored.
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(11)).thenReturn(Optional.of(recent));

        service.initiatePasswordReset("ada@example.com");

        // No new token persisted, no new email sent.
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void initiateAllowedWhenCooldownWindowExpired() {
        UserAuth user = activeUser();
        PasswordResetToken stale = new PasswordResetToken();
        stale.setTokenId(99);
        stale.setUserId(11);
        // Issued 61s ago; cooldown is 60s, so a fresh token must be issued.
        stale.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUserId(11)).thenReturn(Optional.of(stale));
        when(passwordEncoder.encode(anyString())).thenReturn("verifier-hash");

        service.initiatePasswordReset("ada@example.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getTokenId()).isEqualTo(99); // same row, refreshed
        assertThat(tokenCaptor.getValue().getCreatedAt()).isAfter(LocalDateTime.now().minusSeconds(5));
        verify(emailService).sendPasswordResetEmail(
                eq("ada@example.com"), anyString(), eq("https://app.local/chronogram"));
    }

    @Test
    void initiateSilentlyDoesNothingForUnknownEmail() {
        when(userAuthRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        service.initiatePasswordReset("ghost@example.com");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void initiateSilentlyDoesNothingForInactiveUser() {
        UserAuth user = activeUser();
        user.setStatus(AccountStatus.BLOCKED);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        service.initiatePasswordReset("ada@example.com");

        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    // ---- resetPassword ----

    private PasswordResetToken validToken() {
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(11);
        token.setSelector("sel");
        token.setVerifierHash("stored-verifier-hash");
        token.setExpirationTime(LocalDateTime.now().plusMinutes(10));
        return token;
    }

    @Test
    void resetPasswordUpdatesHashAndDeletesToken() {
        UserAuth user = activeUser();
        when(tokenRepository.findBySelector("sel")).thenReturn(Optional.of(validToken()));
        when(passwordEncoder.matches("ver", "stored-verifier-hash")).thenReturn(true);
        when(userAuthRepository.findById(11)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        service.resetPassword("sel:ver", "newPassword1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userAuthRepository).save(user);
        verify(tokenRepository).deleteBySelector("sel");
    }

    @Test
    void resetPasswordRejectsMalformedToken() {
        assertThatThrownBy(() -> service.resetPassword("no-colon-here", "newPassword1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid token format");
        verifyNoInteractions(tokenRepository);
    }

    @Test
    void resetPasswordRejectsUnknownSelector() {
        when(tokenRepository.findBySelector("sel")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("sel:ver", "newPassword1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("invalid or has expired");
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken token = validToken();
        token.setExpirationTime(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findBySelector("sel")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.resetPassword("sel:ver", "newPassword1"))
                .isInstanceOf(ValidationException.class);
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void resetPasswordRejectsWrongVerifier() {
        when(tokenRepository.findBySelector("sel")).thenReturn(Optional.of(validToken()));
        when(passwordEncoder.matches("badver", "stored-verifier-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword("sel:badver", "newPassword1"))
                .isInstanceOf(ValidationException.class);
        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void resetPasswordFailsWhenUserVanished() {
        when(tokenRepository.findBySelector("sel")).thenReturn(Optional.of(validToken()));
        when(passwordEncoder.matches("ver", "stored-verifier-hash")).thenReturn(true);
        when(userAuthRepository.findById(11)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("sel:ver", "newPassword1"))
                .isInstanceOf(ServiceException.class);
        verify(tokenRepository, never()).deleteBySelector(anyString());
    }
}
