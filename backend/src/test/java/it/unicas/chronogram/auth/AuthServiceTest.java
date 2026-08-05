package it.unicas.chronogram.auth;

import it.unicas.chronogram.auth.dto.LoginResponse;
import it.unicas.chronogram.auth.dto.RegisterRequest;
import it.unicas.chronogram.common.exception.ApiExceptions.EmailAlreadyExistsException;
import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.repository.LoginEventRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private LoginEventRepository loginEventRepository;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;

    @Mock(lenient = true) // password encoder is not touched in every path
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private AuthService authService;

    /**
     * The registration policy is wired for real, not mocked: the domain rule that
     * decides who waits for approval is the point of the feature, so the tests
     * exercise it rather than a stub of it. Defaults apply, i.e. {@code unicas.it}
     * is the only trusted domain.
     */
    @BeforeEach
    void setUp() {
        ChronogramProperties properties = new ChronogramProperties();
        authService = new AuthService(userAuthRepository, userProfileRepository, loginEventRepository,
                passwordEncoder, jwtService, new RegistrationPolicy(properties), emailService, properties);
    }

    private RegisterRequest registerRequest() {
        return new RegisterRequest(
                "Ada", "Lovelace", "123456",
                "ada@example.com", "password123",
                "10-12-1815", "F", "London");
    }

    // ---- register ----

    @Test
    void registerPersistsAuthAndProfileForNewEmail() {
        when(userAuthRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-pw");
        UserAuth saved = new UserAuth();
        saved.setUserId(42);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);

        authService.register(registerRequest());

        ArgumentCaptor<UserAuth> authCaptor = ArgumentCaptor.forClass(UserAuth.class);
        verify(userAuthRepository).save(authCaptor.capture());
        UserAuth persisted = authCaptor.getValue();
        assertThat(persisted.getEmail()).isEqualTo("ada@example.com");
        assertThat(persisted.getPasswordHash()).isEqualTo("hashed-pw");
        // example.com is not a trusted domain: the account waits for approval and
        // is therefore not yet allowed to authenticate.
        assertThat(persisted.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(persisted.isActive()).isFalse();

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        UserProfile profile = profileCaptor.getValue();
        assertThat(profile.getUserId()).isEqualTo(42);
        assertThat(profile.getName()).isEqualTo("Ada");
        assertThat(profile.getBirthday()).isEqualTo(LocalDate.of(1815, 12, 10));
    }

    @Test
    void registerAutoApprovesATrustedDomainAndSendsNoPendingEmail() {
        RegisterRequest req = new RegisterRequest(
                "Ada", "Lovelace", null, "ada@unicas.it", "password123", null, null, null);
        when(userAuthRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        UserAuth saved = new UserAuth();
        saved.setUserId(3);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);

        AccountStatus status = authService.register(req);

        assertThat(status).isEqualTo(AccountStatus.ACTIVE);
        ArgumentCaptor<UserAuth> captor = ArgumentCaptor.forClass(UserAuth.class);
        verify(userAuthRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(captor.getValue().isActive()).isTrue();
        // Nothing to wait for, so nobody is told to wait.
        verify(emailService, never()).sendRegistrationPendingEmail(anyString());
    }

    @Test
    void registerOnASubdomainOfATrustedDomainIsAlsoAutoApproved() {
        RegisterRequest req = new RegisterRequest(
                "Ada", "Lovelace", null, "ada@studenti.unicas.it", "password123", null, null, null);
        when(userAuthRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        UserAuth saved = new UserAuth();
        saved.setUserId(4);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);

        assertThat(authService.register(req)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void registerOnAnUntrustedDomainEmailsTheApplicantAndTheAdministrator() {
        when(userAuthRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pw");
        UserAuth saved = new UserAuth();
        saved.setUserId(42);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);
        UserAuth admin = new UserAuth();
        admin.setEmail("admin@chronogram.io");
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.of(admin));

        AccountStatus status = authService.register(registerRequest());

        assertThat(status).isEqualTo(AccountStatus.PENDING);
        verify(emailService).sendRegistrationPendingEmail("ada@example.com");
        verify(emailService).sendPendingRegistrationNotice("admin@chronogram.io", "ada@example.com", "Ada");
    }

    @Test
    void registrationSurvivesAMailOutage() {
        when(userAuthRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pw");
        UserAuth saved = new UserAuth();
        saved.setUserId(42);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);
        doThrow(new ServiceException("smtp down"))
                .when(emailService).sendRegistrationPendingEmail(anyString());

        // The account is created either way: an SMTP failure must not lose a
        // registration the user has already completed.
        assertThat(authService.register(registerRequest())).isEqualTo(AccountStatus.PENDING);
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userAuthRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userAuthRepository, never()).save(any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void registerStoresNullBirthdayWhenFormatInvalid() {
        RegisterRequest req = new RegisterRequest(
                "Ada", "Lovelace", null, "ada@example.com", "password123",
                "not-a-date", null, null);
        when(userAuthRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        UserAuth saved = new UserAuth();
        saved.setUserId(1);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);

        authService.register(req);

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getBirthday()).isNull();
    }

    // ---- login ----

    private UserAuth activeUser() {
        UserAuth user = new UserAuth();
        user.setUserId(7);
        user.setEmail("ada@example.com");
        user.setPasswordHash("stored-hash");
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndResetsCounters() {
        UserAuth user = activeUser();
        user.setFailedLoginAttempts(3);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken("ada@example.com", Role.USER)).thenReturn("jwt-token");

        LoginResponse response = authService.login("ada@example.com", "password123");

        assertThat(response.success()).isTrue();
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("ada@example.com");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLogin()).isNotNull();
        verify(userAuthRepository).save(user);
    }

    @Test
    void loginFailsForUnknownUserWithoutSaving() {
        when(userAuthRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        LoginResponse response = authService.login("ghost@example.com", "whatever");

        assertThat(response.success()).isFalse();
        assertThat(response.token()).isNull();
        verify(userAuthRepository, never()).save(any());
    }

    /**
     * The state is revealed only after the password proves the caller owns the
     * account. Checking it earlier would answer "does this address exist here?"
     * to anyone who asked, which is why the password is verified first.
     */
    @Test
    void loginTellsAPendingUserToWaitOnlyAfterThePasswordIsVerified() {
        UserAuth user = activeUser();
        user.setStatus(AccountStatus.PENDING);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);

        LoginResponse response = authService.login("ada@example.com", "password123");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("waiting for administrator approval");
        assertThat(response.token()).isNull();
        verify(passwordEncoder).matches("password123", "stored-hash");
        // No session was established, so nothing about the account changed.
        verify(userAuthRepository, never()).save(any());
        verify(loginEventRepository, never()).save(any());
    }

    @Test
    void loginTellsABlockedUserTheAccountIsBlocked() {
        UserAuth user = activeUser();
        user.setStatus(AccountStatus.BLOCKED);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);

        LoginResponse response = authService.login("ada@example.com", "password123");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("blocked");
        assertThat(response.token()).isNull();
    }

    /**
     * A wrong password on a pending account must be indistinguishable from a wrong
     * password anywhere else, otherwise the status leaks after all.
     */
    @Test
    void loginOnAPendingAccountWithAWrongPasswordSaysOnlyInvalidCredentials() {
        UserAuth user = activeUser();
        user.setStatus(AccountStatus.PENDING);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        LoginResponse response = authService.login("ada@example.com", "wrong");

        assertThat(response.message()).isEqualTo("Invalid credentials.");
    }

    @Test
    void loginRejectsLockedAccountBeforeCheckingPassword() {
        UserAuth user = activeUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login("ada@example.com", "password123");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("locked");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void loginIncrementsFailedAttemptsOnWrongPassword() {
        UserAuth user = activeUser();
        user.setFailedLoginAttempts(1);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        LoginResponse response = authService.login("ada@example.com", "wrong");

        assertThat(response.success()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(user.getLockedUntil()).isNull();
        verify(userAuthRepository).save(user);
    }

    @Test
    void loginLocksAccountAfterFifthFailure() {
        UserAuth user = activeUser();
        user.setFailedLoginAttempts(4); // fifth attempt will trip the lockout
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("wrong"), anyString())).thenReturn(false);

        LoginResponse response = authService.login("ada@example.com", "wrong");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).contains("locked");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());
        verify(jwtService, never()).generateToken(anyString(), any());
    }

    @Test
    void loginDoesNotGenerateTokenOnFailure() {
        UserAuth user = activeUser();
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        authService.login("ada@example.com", "wrong");

        verify(jwtService, never()).generateToken(anyString(), any());
        verify(userAuthRepository, times(1)).save(user);
    }
}
