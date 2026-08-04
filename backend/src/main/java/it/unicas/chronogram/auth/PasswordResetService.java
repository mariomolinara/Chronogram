package it.unicas.chronogram.auth;

import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.PasswordResetToken;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.repository.PasswordResetTokenRepository;
import it.unicas.chronogram.repository.UserAuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Business logic for the password-reset flow using the selector/verifier
 * pattern. Token creation and email dispatch are transactional so a failed
 * send rolls back the stored token.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAuthRepository userAuthRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ChronogramProperties.Reset resetProps;

    public PasswordResetService(UserAuthRepository userAuthRepository,
                                PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder,
                                ChronogramProperties properties) {
        this.userAuthRepository = userAuthRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.resetProps = properties.getReset();
    }

    @Transactional
    public void initiatePasswordReset(String email, String origin) {
        UserAuth user = userAuthRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive()) {
            // Do not reveal whether the email exists.
            log.warn("Password reset requested for non-existent or inactive user: {}", email);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        PasswordResetToken token = tokenRepository.findByUserId(user.getUserId())
                .orElseGet(PasswordResetToken::new);

        // Per-account cooldown: if a reset token was issued very recently, ignore
        // this request without generating a new token or sending another email.
        // This throttles reset-email spam at the application layer (complementing
        // nginx per-IP limits) while keeping the external response identical, so
        // no account enumeration is possible.
        if (token.getCreatedAt() != null
                && token.getCreatedAt().isAfter(now.minusSeconds(resetProps.getRequestCooldownSeconds()))) {
            log.info("Password reset for user_id={} throttled: within {}s cooldown",
                    user.getUserId(), resetProps.getRequestCooldownSeconds());
            return;
        }

        String selector = generateSecureString(16);
        String verifier = generateSecureString(32);

        token.setUserId(user.getUserId());
        token.setSelector(selector);
        token.setVerifierHash(passwordEncoder.encode(verifier));
        token.setExpirationTime(now.plusMinutes(resetProps.getTokenTtlMinutes()));
        token.setCreatedAt(now);
        tokenRepository.save(token);

        String fullToken = selector + ":" + verifier;
        String baseUrl = StringUtils.hasText(origin) ? origin : resetProps.getFallbackBaseUrl();
        emailService.sendPasswordResetEmail(email, fullToken, baseUrl);

        log.info("Reset token created and email sent for {}", email);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        String[] parts = token.split(":");
        if (parts.length != 2) {
            throw new ValidationException("Invalid token format.");
        }
        String selector = parts[0];
        String verifier = parts[1];

        PasswordResetToken resetToken = tokenRepository.findBySelector(selector).orElse(null);
        if (resetToken == null
                || resetToken.getExpirationTime().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(verifier, resetToken.getVerifierHash())) {
            throw new ValidationException("Reset token is invalid or has expired.");
        }

        UserAuth user = userAuthRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ServiceException("User associated with reset token no longer exists."));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // The account no longer uses the password it was provisioned with, so the
        // forced-change flag (set for the bootstrapped admin) is satisfied.
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        userAuthRepository.save(user);

        tokenRepository.deleteBySelector(selector);
        log.info("Password successfully reset for user_id={}", resetToken.getUserId());
    }

    private String generateSecureString(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
