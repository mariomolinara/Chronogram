package it.unicas.chronogram.auth;

import it.unicas.chronogram.auth.dto.LoginResponse;
import it.unicas.chronogram.auth.dto.RegisterRequest;
import it.unicas.chronogram.common.exception.ApiExceptions.EmailAlreadyExistsException;
import it.unicas.chronogram.domain.LoginEvent;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.repository.LoginEventRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import it.unicas.chronogram.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Business logic for registration and login, including brute-force protection
 * (temporary lockout after repeated failures).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;
    private static final DateTimeFormatter BIRTHDAY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final LoginEventRepository loginEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAuthRepository userAuthRepository,
                       UserProfileRepository userProfileRepository,
                       LoginEventRepository loginEventRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.loginEventRepository = loginEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (userAuthRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered.");
        }

        LocalDateTime now = LocalDateTime.now();

        UserAuth auth = new UserAuth();
        auth.setEmail(request.email());
        auth.setPasswordHash(passwordEncoder.encode(request.password()));
        auth.setCreatedAt(now);
        auth.setUpdatedAt(now);
        auth.setActive(true);
        UserAuth savedAuth = userAuthRepository.save(auth);

        UserProfile profile = new UserProfile();
        profile.setUserId(savedAuth.getUserId());
        profile.setName(request.name());
        profile.setSurname(request.surname());
        profile.setPhone(request.phone());
        profile.setGender(request.gender());
        profile.setAddress(request.address());
        profile.setBirthday(parseBirthday(request.birthday()));
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        userProfileRepository.save(profile);

        log.info("User {} registered successfully with user_id={}", request.email(), savedAuth.getUserId());
    }

    /**
     * Authenticates a user. Business failures (bad credentials, locked account)
     * are returned as an unsuccessful {@link LoginResponse} rather than thrown,
     * so the endpoint always responds 200 and the client handles the outcome.
     */
    @Transactional
    public LoginResponse login(String email, String rawPassword) {
        UserAuth user = userAuthRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null || !user.isActive()) {
            log.warn("Login attempt for non-existent or inactive user: {}", email);
            return LoginResponse.failure("Invalid credentials.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            log.warn("Login attempt on locked account: {}", email);
            return LoginResponse.failure("Account is locked. Please try again later.");
        }

        if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLogin(now);
            user.setUpdatedAt(now);
            userAuthRepository.save(user);
            // Append-only history: last_login alone cannot answer the "active on
            // each of the last N days" question the admin dashboard asks.
            loginEventRepository.save(new LoginEvent(user.getUserId(), now));
            log.info("Login successful for {}", email);
            Role role = user.getRole() == null ? Role.USER : user.getRole();
            return LoginResponse.success(user.getEmail(),
                    jwtService.generateToken(user.getEmail(), role),
                    role,
                    user.isMustChangePassword());
        }

        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        String message = "Invalid credentials.";
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCKOUT_DURATION_MINUTES));
            message = "Account locked due to too many failed attempts.";
            log.warn("Account for {} has been locked.", email);
        }
        user.setUpdatedAt(now);
        userAuthRepository.save(user);
        log.warn("Wrong password for {}", email);
        return LoginResponse.failure(message);
    }

    private LocalDate parseBirthday(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, BIRTHDAY_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("Invalid birthday format '{}', storing null", value);
            return null;
        }
    }
}
