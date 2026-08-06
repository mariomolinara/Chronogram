package it.unicas.chronogram.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.activity.ActivityController;
import it.unicas.chronogram.activity.ActivityService;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.JwtAuthenticationFilter;
import it.unicas.chronogram.security.JwtService;
import it.unicas.chronogram.security.RestAccessDeniedHandler;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

        @Bean
        RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
            return new RestAccessDeniedHandler(objectMapper);
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
    void preflightFromTheMobileAppOriginIsAllowedWithoutConfiguration() throws Exception {
        // The Capacitor WebView calls from https://localhost (Android). That origin
        // is compiled into SecurityConfig, NOT into the per-environment allowlist
        // (which here contains only ALLOWED_ORIGIN): if this test fails, the web
        // keeps working but the installed app cannot even log in.
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://localhost")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://localhost"));
    }

    @Test
    void preflightFromTheIosAppOriginIsAllowedWithoutConfiguration() throws Exception {
        // Same contract for the iOS scheme, so a future iOS build does not
        // rediscover this bug.
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "capacitor://localhost")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "capacitor://localhost"));
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
    void llmRouteWithoutTokenReturns401() throws Exception {
        // The LLM proxy bills a third-party provider per call: it must never be
        // reachable anonymously. Authorization is decided by the filter chain
        // before dispatch, so no LlmController bean is needed in this slice.
        mockMvc.perform(post("/api/llm/prompt")
                        .contentType("application/json").content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRouteWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRouteIsForbiddenForAnOrdinaryUser() throws Exception {
        // Authenticated but without ROLE_ADMIN: must be 403, not 401, and must not
        // reach the controller. The JSON envelope comes from RestAccessDeniedHandler.
        mockMvc.perform(get("/api/admin/stats").with(user("ada@example.com").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
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
