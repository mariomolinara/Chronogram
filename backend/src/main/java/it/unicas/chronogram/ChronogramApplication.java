package it.unicas.chronogram;

import it.unicas.chronogram.config.ChronogramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point of the Chronogram backend.
 *
 * <p>Runs both as an executable WAR (embedded Tomcat, {@code java -jar}) and as
 * a classic WAR on an external Apache Tomcat (&gt;=10.1). On external Tomcat the
 * context path comes from the WAR file name ({@code chronogram.war} →
 * {@code /chronogram}); {@code server.servlet.context-path} only applies to the
 * embedded run.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ChronogramProperties.class)
public class ChronogramApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(ChronogramApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ChronogramApplication.class);
    }
}
