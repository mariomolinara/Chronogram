package it.unicas.chronogram.auth;

import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.AccountStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Decides whether a new registration is approved on the spot or has to wait for
 * an administrator.
 *
 * <p>The rule is the email domain: addresses belonging to a configured trusted
 * domain (institutional accounts, {@code unicas.it} by default) are already
 * vouched for by whoever issued them, so making a human re-approve them adds
 * delay without adding a check. Everything else needs a decision.
 */
@Component
public class RegistrationPolicy {

    private final ChronogramProperties properties;

    public RegistrationPolicy(ChronogramProperties properties) {
        this.properties = properties;
    }

    /** The state a brand-new account registered with this email should start in. */
    public AccountStatus statusFor(String email) {
        return isAutoApproved(email) ? AccountStatus.ACTIVE : AccountStatus.PENDING;
    }

    /**
     * Whether the address belongs to a trusted domain. Matching is
     * case-insensitive and includes sub-domains, so {@code unicas.it} also
     * admits {@code mario@studenti.unicas.it} but never {@code notunicas.it}.
     */
    public boolean isAutoApproved(String email) {
        String domain = domainOf(email);
        if (domain == null) {
            return false;
        }
        List<String> trusted = properties.getRegistration().getAutoApproveDomains();
        if (trusted == null || trusted.isEmpty()) {
            return false;
        }
        for (String candidate : trusted) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            String trimmed = candidate.trim().toLowerCase(Locale.ROOT);
            if (domain.equals(trimmed) || domain.endsWith("." + trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything after the last {@code @}. Splitting on the last one matters:
     * a local part is allowed to contain {@code @} when quoted, and only the
     * final segment is the domain.
     */
    private static String domainOf(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }
}
