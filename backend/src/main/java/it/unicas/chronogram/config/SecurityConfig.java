package it.unicas.chronogram.config;

import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.security.JwtAuthenticationFilter;
import it.unicas.chronogram.security.RestAccessDeniedHandler;
import it.unicas.chronogram.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless security configuration: public auth endpoints, JWT-protected
 * activity and LLM endpoints, a strict CORS allowlist, and CSRF disabled
 * (token-based, no cookies).
 */
@Configuration
public class SecurityConfig {

    private final ChronogramProperties properties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(ChronogramProperties properties,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.properties = properties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // The LLM proxy forwards to a metered third-party provider: keep it
                        // behind authentication so it cannot be abused by anonymous callers.
                        .requestMatchers("/api/llm/**").authenticated()
                        .requestMatchers("/api/activities/**").authenticated()
                        // Self-service account data and the support form: any signed-in
                        // user, admin or not. Both resolve the subject from the token,
                        // so authentication is the only authorisation they need.
                        .requestMatchers("/api/profile/**").authenticated()
                        .requestMatchers("/api/support/**").authenticated()
                        // Back-office: aggregate stats and full-database export.
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        // Catch-all for any future /api endpoint added without an
                        // explicit rule: never let it fall through to the public
                        // static-resources matcher below.
                        .requestMatchers("/api/**", "/actuator/**").authenticated()
                        // The SPA bundle (index.html, /assets/**, icons) and its
                        // client-side routes are public by nature; everything
                        // sensitive lives under /api and is matched above.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = properties.getSecurity().getCors().getAllowedOrigins();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(86_400L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
