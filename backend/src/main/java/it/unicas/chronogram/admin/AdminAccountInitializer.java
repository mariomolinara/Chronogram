package it.unicas.chronogram.admin;

import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Provisions the built-in administrator at startup from {@code chronogram.admin.*}
 * (env {@code ADMIN_EMAIL} / {@code ADMIN_INITIAL_PASSWORD}).
 *
 * <p>Runs after Flyway, and is deliberately conservative:
 * <ul>
 *   <li>the account is created only once - on later boots the row is left alone,
 *       so an email or password changed from the admin page is never reverted to
 *       the values still sitting in {@code .env};</li>
 *   <li>it never takes over an email that already belongs to an ordinary user;</li>
 *   <li>it re-asserts the invariants (role, system flag, active) on every boot, so
 *       a hand-edited database cannot leave the installation without an admin.</li>
 * </ul>
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final ChronogramProperties properties;
    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(ChronogramProperties properties,
                                   UserAuthRepository userAuthRepository,
                                   UserProfileRepository userProfileRepository,
                                   PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<UserAuth> existing = userAuthRepository.findFirstBySystemAccountTrue();
        if (existing.isPresent()) {
            repairInvariants(existing.get());
            return;
        }

        String email = trimToNull(properties.getAdmin().getEmail());
        String password = trimToNull(properties.getAdmin().getInitialPassword());

        if (email == null) {
            log.warn("No administrator account exists and ADMIN_EMAIL is not set: "
                    + "the admin dashboard will be unreachable until it is configured.");
            return;
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            log.error("Administrator account not created: ADMIN_INITIAL_PASSWORD is missing or shorter "
                    + "than {} characters.", MIN_PASSWORD_LENGTH);
            return;
        }
        if (userAuthRepository.existsByEmailIgnoreCase(email)) {
            log.error("Administrator account not created: {} already belongs to a regular user. "
                    + "Choose a different ADMIN_EMAIL.", email);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        UserAuth admin = new UserAuth();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setSystemAccount(true);
        admin.setMustChangePassword(true);
        admin.setActive(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        UserAuth saved = userAuthRepository.save(admin);

        // The activity screens join user_auth to user; give the admin a profile row
        // too so the rest of the app does not have to special-case its absence.
        UserProfile profile = new UserProfile();
        profile.setUserId(saved.getUserId());
        profile.setName("Chronogram");
        profile.setSurname("Administrator");
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        userProfileRepository.save(profile);

        log.info("Administrator account created for {} (user_id={}); the initial password must be "
                + "changed at first login.", email, saved.getUserId());
    }

    /**
     * Re-applies what the system account must always be. Cheap, and it means a
     * manual {@code UPDATE} that demoted or disabled the admin is undone on the
     * next restart rather than locking everyone out of the back office.
     */
    private void repairInvariants(UserAuth admin) {
        boolean changed = false;
        if (admin.getRole() != Role.ADMIN) {
            admin.setRole(Role.ADMIN);
            changed = true;
        }
        if (!admin.isActive()) {
            admin.setActive(true);
            changed = true;
        }
        if (changed) {
            admin.setUpdatedAt(LocalDateTime.now());
            userAuthRepository.save(admin);
            log.warn("Administrator account {} had been demoted or disabled; restored.", admin.getEmail());
        }

        String configured = trimToNull(properties.getAdmin().getEmail());
        if (configured != null && !configured.equalsIgnoreCase(admin.getEmail())) {
            log.info("ADMIN_EMAIL ({}) differs from the administrator's current email ({}). "
                    + "The stored value wins; update .env to match.", configured, admin.getEmail());
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
