package it.unicas.chronogram.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * Strongly-typed binding for the {@code chronogram.*} configuration tree.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "chronogram")
public class ChronogramProperties {

    @NestedConfigurationProperty
    private Security security = new Security();

    @NestedConfigurationProperty
    private Reset reset = new Reset();

    @NestedConfigurationProperty
    private Llm llm = new Llm();

    @NestedConfigurationProperty
    private Admin admin = new Admin();

    @NestedConfigurationProperty
    private Stats stats = new Stats();

    @NestedConfigurationProperty
    private Registration registration = new Registration();

    @NestedConfigurationProperty
    private Support support = new Support();

    @Getter
    @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
        private Cors cors = new Cors();
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs = 86_400_000L;
        private String issuer = "chronogram";
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of();
    }

    @Getter
    @Setter
    public static class Reset {
        /**
         * Canonical base URL of the front-end, used to build EVERY password-reset
         * link. The request {@code Origin} header is deliberately ignored: it is
         * the bare origin (no context path) for the web app and the WebView origin
         * for the Android app, so it would produce broken links. Bound from the
         * {@code APP_CANONICAL_URL} environment variable; a trailing slash is
         * tolerated and stripped when the link is built.
         */
        private String appBaseUrl = "http://localhost:5173";
        private int tokenTtlMinutes = 30;
        /**
         * Minimum interval, in seconds, between two accepted password-reset
         * requests for the same account. A second request within this window is
         * silently ignored (no new token, no new email) to throttle reset-email
         * spam at the application level, complementing nginx per-IP rate limits.
         * The external response is unchanged, so no account enumeration occurs.
         */
        private int requestCooldownSeconds = 60;
    }

    @Getter
    @Setter
    public static class Llm {
        private String apiUrl;
        private String apiKey;
        private String defaultModel;
    }

    /**
     * Built-in administrator, provisioned at startup. Credentials live in the
     * environment, never in the repository or in a Flyway seed.
     */
    @Getter
    @Setter
    public static class Admin {
        /** Login of the administrator account. Blank disables provisioning. */
        private String email;
        /**
         * Password used only when the account is first created. It is stored
         * BCrypt-hashed and the account is flagged {@code must_change_password},
         * so this value stops working as soon as the admin picks a new one.
         */
        private String initialPassword;
    }

    /** Who gets in without waiting for an administrator. */
    @Getter
    @Setter
    public static class Registration {
        /**
         * Email domains whose registrations are approved automatically. Everyone
         * else is created PENDING and needs an administrator to approve them.
         * Matching is case-insensitive and covers sub-domains, so {@code unicas.it}
         * also admits {@code studenti.unicas.it}. An empty list sends every new
         * registration through manual approval.
         */
        private List<String> autoApproveDomains = List.of("unicas.it");
        /**
         * Whether the administrator is emailed when a registration needs a
         * decision. Without it a pending request is only visible to someone who
         * happens to open the back office.
         */
        private boolean notifyAdmin = true;
    }

    /** Where the in-app support form delivers. */
    @Getter
    @Setter
    public static class Support {
        /**
         * Mailbox that receives the messages sent from the support screen. Left
         * empty - the default - the messages go to the built-in administrator
         * instead: the address stored on the system account, falling back to
         * {@code chronogram.admin.email}. With none of the three available the
         * endpoint reports the feature as unconfigured rather than dropping a
         * message on the floor.
         */
        private String email;
    }

    /** Windows behind the admin dashboard metrics. */
    @Getter
    @Setter
    public static class Stats {
        /** A user is "active" if they logged in at least once in this many days. */
        private int activeWindowDays = 10;
        /** A user is "regular" if they logged in on every one of these days. */
        private int regularWindowDays = 7;
        /** Length of the activity-per-day series returned to the dashboard. */
        private int distributionDays = 30;
    }
}
