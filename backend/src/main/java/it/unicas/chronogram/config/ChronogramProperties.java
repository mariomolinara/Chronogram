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
        private String fallbackBaseUrl = "http://localhost:8100";
        private int tokenTtlMinutes = 30;
    }

    @Getter
    @Setter
    public static class Llm {
        private String apiUrl;
        private String apiKey;
        private String defaultModel;
    }
}
