package it.unicas.chronogram.auth;

import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.AccountStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The domain rule is the whole gate: everything it lets through skips human
 * review, so the cases that must NOT match matter as much as the ones that must.
 */
class RegistrationPolicyTest {

    private RegistrationPolicy policyWith(List<String> domains) {
        ChronogramProperties properties = new ChronogramProperties();
        properties.getRegistration().setAutoApproveDomains(domains);
        return new RegistrationPolicy(properties);
    }

    private RegistrationPolicy defaultPolicy() {
        return new RegistrationPolicy(new ChronogramProperties());
    }

    @Test
    void unicasIsTrustedOutOfTheBox() {
        assertThat(defaultPolicy().statusFor("mario@unicas.it")).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void matchingIgnoresCaseAndSurroundingSpace() {
        RegistrationPolicy policy = policyWith(List.of("  UNICAS.IT  "));

        assertThat(policy.isAutoApproved("Mario@Unicas.IT")).isTrue();
    }

    @Test
    void subDomainsOfATrustedDomainAreTrustedToo() {
        assertThat(defaultPolicy().isAutoApproved("mario@studenti.unicas.it")).isTrue();
    }

    /**
     * The check is a domain match, not a substring one: an attacker registering
     * {@code notunicas.it} or {@code unicas.it.evil.com} must not slip through.
     */
    @Test
    void lookalikeDomainsAreNotTrusted() {
        RegistrationPolicy policy = defaultPolicy();

        assertThat(policy.isAutoApproved("mario@notunicas.it")).isFalse();
        assertThat(policy.isAutoApproved("mario@unicas.it.evil.com")).isFalse();
        assertThat(policy.isAutoApproved("mario@unicas.com")).isFalse();
        assertThat(policy.isAutoApproved("unicas.it@gmail.com")).isFalse();
    }

    @Test
    void anythingElseWaitsForApproval() {
        assertThat(defaultPolicy().statusFor("mario@gmail.com")).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void malformedAddressesNeverAutoApprove() {
        RegistrationPolicy policy = defaultPolicy();

        assertThat(policy.isAutoApproved(null)).isFalse();
        assertThat(policy.isAutoApproved("")).isFalse();
        assertThat(policy.isAutoApproved("no-at-sign")).isFalse();
        assertThat(policy.isAutoApproved("trailing@")).isFalse();
    }

    /** Only the part after the last @ counts, so a local part cannot fake a domain. */
    @Test
    void onlyTheFinalDomainSegmentIsConsidered() {
        assertThat(defaultPolicy().isAutoApproved("\"foo@unicas.it\"@gmail.com")).isFalse();
    }

    @Test
    void anEmptyAllowlistSendsEveryoneThroughApproval() {
        RegistrationPolicy policy = policyWith(List.of());

        assertThat(policy.statusFor("mario@unicas.it")).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void blankEntriesInTheAllowlistAreIgnoredRatherThanMatchingEverything() {
        RegistrationPolicy policy = policyWith(List.of("", "   "));

        assertThat(policy.isAutoApproved("mario@unicas.it")).isFalse();
    }
}
