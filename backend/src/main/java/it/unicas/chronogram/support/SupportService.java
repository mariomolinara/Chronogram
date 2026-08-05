package it.unicas.chronogram.support;

import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.UserAuth;
import it.unicas.chronogram.domain.UserProfile;
import it.unicas.chronogram.mail.EmailService;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.repository.UserProfileRepository;
import it.unicas.chronogram.support.dto.SupportMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Delivers the messages written on the support screen to whoever reads support
 * mail for this installation.
 *
 * <p>Nothing is persisted: the email is the whole feature, which is why a send
 * failure is allowed to surface as an error instead of being swallowed the way
 * the account-lifecycle notifications are. There the mail is a courtesy attached
 * to a change already committed; here a silent failure would mean telling a user
 * their request was received when it went nowhere.
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final UserAuthRepository userAuthRepository;
    private final UserProfileRepository userProfileRepository;
    private final EmailService emailService;
    private final ChronogramProperties properties;

    public SupportService(UserAuthRepository userAuthRepository,
                          UserProfileRepository userProfileRepository,
                          EmailService emailService,
                          ChronogramProperties properties) {
        this.userAuthRepository = userAuthRepository;
        this.userProfileRepository = userProfileRepository;
        this.emailService = emailService;
        this.properties = properties;
    }

    /**
     * Sends the message on behalf of the authenticated user.
     *
     * @param userId the caller, taken from the JWT
     * @throws FeatureUnavailableException (503) when no support recipient is configured
     * @throws it.unicas.chronogram.common.exception.ApiExceptions.ServiceException
     *         (500) when the SMTP server refuses the message
     */
    @Transactional(readOnly = true)
    public void submit(Integer userId, SupportMessageRequest request) {
        UserAuth account = userAuthRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        UserProfile profile = userProfileRepository.findById(userId).orElse(null);

        String recipient = recipient();
        String senderName = profile == null ? null : fullName(profile);

        // The subject and the body are the user's own words: they belong in the
        // email, not in the server log.
        log.info("Support message from user_id={} being delivered to the support mailbox", userId);
        emailService.sendSupportMessage(recipient, account.getEmail(), senderName,
                request.subject().trim(), request.message().trim());
    }

    /**
     * The support mailbox, in order of preference: the explicitly configured
     * address, then the administrator account as it currently stands in the
     * database, then the address the administrator was provisioned with.
     *
     * <p>The stored admin email is preferred over {@code chronogram.admin.email}
     * because an administrator who changed their address from the back office
     * would otherwise never see these messages - the configured value is only
     * read when the account is first created.
     */
    private String recipient() {
        String configured = trimToNull(properties.getSupport().getEmail());
        if (configured != null) {
            return configured;
        }

        String systemAdmin = userAuthRepository.findFirstBySystemAccountTrue()
                .map(UserAuth::getEmail)
                .map(SupportService::trimToNull)
                .orElse(null);
        if (systemAdmin != null) {
            return systemAdmin;
        }

        String fallback = trimToNull(properties.getAdmin().getEmail());
        if (fallback != null) {
            return fallback;
        }

        log.error("A support message could not be delivered: neither chronogram.support.email "
                + "(SUPPORT_EMAIL) nor an administrator account is configured.");
        throw new FeatureUnavailableException(
                "Support requests are not available right now. Please try again later.");
    }

    private static String fullName(UserProfile profile) {
        String name = trimToNull(profile.getName());
        String surname = trimToNull(profile.getSurname());
        if (name == null) {
            return surname;
        }
        return surname == null ? name : name + " " + surname;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
