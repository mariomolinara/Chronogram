package it.unicas.chronogram.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.activity.ActivityController;
import it.unicas.chronogram.activity.ActivityService;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.JwtAuthenticationFilter;
import it.unicas.chronogram.security.JwtService;
import it.unicas.chronogram.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test wiring the real {@link SecurityConfig} filter chain (CORS allowlist,
 * stateless auth, public vs protected routes) over a controller. It verifies the
 * CORS contract - allowed origin gets the CORS headers, a foreign origin is
 * rejected on preflight - and that protected routes stay behind authentication.
 * Controller behaviour itself is covered elsewhere (ActivityControllerTest), so
 * this focuses purely on the security/CORS layer.
 */
@WebMvcTest(controllers = ActivityController.class)
@Import({SecurityConfig.class, SecurityConfigCorsTest.TestBeans.class})
class SecurityConfigCorsTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String FOREIGN_ORIGIN = "http://evil.example.com";

    @Autowired private MockMvc mockMvc;

    @MockBean private ActivityService activityService;
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    /**
     * Beans the imported {@link SecurityConfig} depends on but which a plain
     * {@code @WebMvcTest} does not component-scan: an explicit CORS allowlist,
     * plus the real filter/entry-point (their JWT/repository collaborators are
     * mocked above and injected here).
     */
    static class TestBeans {

        @Bean
        @Primary
        ChronogramProperties chronogramProperties() {
            ChronogramProperties props = new ChronogramProperties();
            props.getSecurity().getCors().setAllowedOrigins(List.of(ALLOWED_ORIGIN));
            return props;
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
                                                        UserAuthRepository userAuthRepository) {
            return new JwtAuthenticationFilter(jwtService, userAuthRepository);
        }

        @Bean
        RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
            return new RestAuthenticationEntryPoint(objectMapper);
        }
    }

    // ---- CORS allowlist ----

    @Test
    void preflightFromAllowedOriginReceivesCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/activities/list")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightFromForeignOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/activities/list")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void simpleRequestFromAllowedOriginEchoesAllowOriginHeader() throws Exception {
        // An authenticated-less POST would be 401, but the CORS header is still set
        // by the CorsFilter before authorization runs.
        mockMvc.perform(post("/api/activities/list")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType("application/json").content("{}"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    // ---- authentication gate ----

    @Test
    void protectedRouteWithoutTokenReturns401JsonEnvelope() throws Exception {
        mockMvc.perform(post("/api/activities/list")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optionsPreflightIsPermittedWithoutAuthentication() throws Exception {
        // OPTIONS /** is explicitly permitAll so browsers can preflight protected routes.
        mockMvc.perform(options("/api/activities/create")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk());
    }
}
