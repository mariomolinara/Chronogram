package it.unicas.chronogram.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.auth.dto.LoginResponse;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.common.exception.ApiExceptions.EmailAlreadyExistsException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.domain.AccountStatus;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the public auth endpoints. Default Spring Security applies, so
 * requests carry {@code csrf()} and a mock user; the focus is request binding,
 * Bean Validation and error mapping via {@link GlobalExceptionHandler}.
 */
@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
@WithMockUser
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private PasswordResetService passwordResetService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> validRegisterBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Ada");
        body.put("surname", "Lovelace");
        body.put("email", "ada@example.com");
        body.put("password", "password123");
        return body;
    }

    @Test
    void registerReturnsOkEnvelope() throws Exception {
        when(authService.register(any())).thenReturn(AccountStatus.ACTIVE);

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegisterBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("Registration successful!"));
    }

    /**
     * A pending registration must not be told it can sign in: the client needs a
     * different message and the status to branch on.
     */
    @Test
    void registerAnnouncesThatApprovalIsPending() throws Exception {
        when(authService.register(any())).thenReturn(AccountStatus.PENDING);

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegisterBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("PENDING"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("administrator has to approve")));
    }

    @Test
    void registerRejectsInvalidEmailWith400() throws Exception {
        Map<String, Object> body = validRegisterBody();
        body.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registerRejectsShortPasswordWith400() throws Exception {
        Map<String, Object> body = validRegisterBody();
        body.put("password", "short");

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registerMapsDuplicateEmailTo409() throws Exception {
        doThrow(new EmailAlreadyExistsException("Email already registered."))
                .when(authService).register(any());

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validRegisterBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginReturnsFlatLoginResponse() throws Exception {
        when(authService.login("ada@example.com", "password123"))
                .thenReturn(LoginResponse.success("ada@example.com", "jwt-token", Role.USER, false));
        Map<String, Object> body = Map.of("email", "ada@example.com", "password", "password123");

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.username").value("ada@example.com"));
    }

    @Test
    void loginReturns200EvenOnBadCredentials() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenReturn(LoginResponse.failure("Invalid credentials."));
        Map<String, Object> body = Map.of("email", "ada@example.com", "password", "nope");

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void requestResetAlwaysReturnsGenericOkMessage() throws Exception {
        doNothing().when(passwordResetService).initiatePasswordReset(anyString());
        Map<String, Object> body = Map.of("email", "ada@example.com");

        // The Origin header must be ignored: the reset link is built server-side
        // from the configured canonical URL.
        mockMvc.perform(post("/api/auth/request-reset").with(csrf())
                        .header("Origin", "https://localhost")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordResetService).initiatePasswordReset("ada@example.com");
    }

    @Test
    void resetPasswordMapsValidationExceptionTo400() throws Exception {
        doThrow(new ValidationException("Reset token is invalid or has expired."))
                .when(passwordResetService).resetPassword(anyString(), anyString());
        Map<String, Object> body = Map.of("token", "sel:ver", "newPassword", "newPassword1");

        mockMvc.perform(post("/api/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
