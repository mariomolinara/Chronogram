package it.unicas.chronogram;

import it.unicas.chronogram.config.ChronogramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point of the Chronogram backend.
 *
 * <p>Replaces the former Struts 2 / Tomcat WAR deployment with a self-contained
 * Spring Boot application (embedded Tomcat, executable JAR).</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ChronogramProperties.class)
public class ChronogramApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChronogramApplication.class, args);
    }
}
