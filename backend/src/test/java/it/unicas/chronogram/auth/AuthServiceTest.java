package it.unicas.chronogram.auth;

import it.unicas.chronogram.auth.dto.LoginResponse;
import it.unicas.chronogram.auth.dto.RegisterRequest;
import it.unicas.chronogram.common.exception.ApiExceptions.EmailAlreadyExistsException;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private JwtService jwtService;

    @Mock(lenient = true) // password encoder is not touched in every path
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

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
        assertThat(persisted.isActive()).isTrue();

        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        UserProfile profile = profileCaptor.getValue();
        assertThat(profile.getUserId()).isEqualTo(42);
        assertThat(profile.getName()).isEqualTo("Ada");
        assertThat(profile.getBirthday()).isEqualTo(LocalDate.of(1815, 12, 10));
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
        user.setActive(true);
        return user;
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndResetsCounters() {
        UserAuth user = activeUser();
        user.setFailedLoginAttempts(3);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken("ada@example.com")).thenReturn("jwt-token");

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

    @Test
    void loginFailsForInactiveUser() {
        UserAuth user = activeUser();
        user.setActive(false);
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login("ada@example.com", "password123");

        assertThat(response.success()).isFalse();
        verify(passwordEncoder, never()).matches(anyString(), anyString());
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
        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void loginDoesNotGenerateTokenOnFailure() {
        UserAuth user = activeUser();
        when(userAuthRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        authService.login("ada@example.com", "wrong");

        verify(jwtService, never()).generateToken(anyString());
        verify(userAuthRepository, times(1)).save(user);
    }
}
