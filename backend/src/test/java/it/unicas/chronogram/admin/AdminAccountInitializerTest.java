package it.unicas.chronogram.admin;

import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the provisioning rules of the built-in administrator: created once from
 * configuration, never overwriting an existing account, and self-healing if the
 * row is demoted or disabled behind the application's back.
 */
@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock private UserAuthRepository userAuthRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private ChronogramProperties properties;
    private AdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new ChronogramProperties();
        initializer = new AdminAccountInitializer(properties, userAuthRepository,
                userProfileRepository, passwordEncoder);
    }

    private void configure(String email, String password) {
        properties.getAdmin().setEmail(email);
        properties.getAdmin().setInitialPassword(password);
    }

    @Test
    void createsTheAdministratorOnFirstBoot() {
        configure("admin@example.com", "initial-password");
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.empty());
        when(userAuthRepository.existsByEmailIgnoreCase("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("initial-password")).thenReturn("hashed");
        UserAuth saved = new UserAuth();
        saved.setUserId(1);
        when(userAuthRepository.save(any(UserAuth.class))).thenReturn(saved);

        initializer.run(null);

        ArgumentCaptor<UserAuth> captor = ArgumentCaptor.forClass(UserAuth.class);
        verify(userAuthRepository).save(captor.capture());
        UserAuth admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getPasswordHash()).isEqualTo("hashed");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isSystemAccount()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void doesNothingWhenTheAdministratorAlreadyExists() {
        // Credentials changed from the admin page must survive a restart, even
        // though .env still carries the original values.
        configure("admin@example.com", "initial-password");
        UserAuth existing = new UserAuth();
        existing.setEmail("changed@example.com");
        existing.setRole(Role.ADMIN);
        existing.setSystemAccount(true);
        existing.setStatus(AccountStatus.ACTIVE);
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.of(existing));

        initializer.run(null);

        verify(userAuthRepository, never()).save(any());
        assertThat(existing.getEmail()).isEqualTo("changed@example.com");
    }

    @Test
    void restoresAnAdministratorThatWasDemotedOrDisabled() {
        configure("admin@example.com", "initial-password");
        UserAuth tampered = new UserAuth();
        tampered.setEmail("admin@example.com");
        tampered.setRole(Role.USER);
        tampered.setSystemAccount(true);
        tampered.setStatus(AccountStatus.BLOCKED);
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.of(tampered));

        initializer.run(null);

        assertThat(tampered.getRole()).isEqualTo(Role.ADMIN);
        assertThat(tampered.isActive()).isTrue();
        verify(userAuthRepository).save(tampered);
    }

    @Test
    void refusesToTakeOverAnEmailBelongingToARegularUser() {
        configure("ada@example.com", "initial-password");
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.empty());
        when(userAuthRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        initializer.run(null);

        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void skipsProvisioningWhenTheInitialPasswordIsTooShort() {
        configure("admin@example.com", "short");
        when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.empty());

        initializer.run(null);

        verify(userAuthRepository, never()).save(any());
    }

    @Test
    void skipsProvisioningWhenNoEmailIsConfigured() {
        configure("   ", "initial-password");
        lenient().when(userAuthRepository.findFirstBySystemAccountTrue()).thenReturn(Optional.empty());

        initializer.run(null);

        verify(userAuthRepository, never()).save(any());
    }
}
