package it.unicas.chronogram.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.profile.dto.ProfileResponse;
import it.unicas.chronogram.profile.dto.UpdateProfileRequest;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.AuthPrincipal;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the self-service endpoints: the JSON contract the app binds to
 * (including the ISO birthday), the validation of the mandatory fields, and the
 * fact that the subject always comes from the authenticated principal.
 */
@WebMvcTest(controllers = ProfileController.class)
@Import(GlobalExceptionHandler.class)
class ProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ProfileService profileService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private Authentication principal() {
        AuthPrincipal p = new AuthPrincipal(55, "ada@unicas.it", Role.USER);
        return new UsernamePasswordAuthenticationToken(p, null, List.of());
    }

    private static ProfileResponse sample() {
        return new ProfileResponse("Ada", "Lovelace", "London", "+39 000",
                "ada@unicas.it", LocalDate.of(1815, 12, 10), "F");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static Map<String, Object> validEdit() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Grace");
        body.put("surname", "Hopper");
        body.put("address", "New York");
        body.put("phone", null);
        body.put("birthday", "1906-12-09");
        body.put("gender", "F");
        return body;
    }

    // ---- GET /me ----

    @Test
    void meReturnsTheProfileOfThePrincipalWithAnIsoBirthday() throws Exception {
        when(profileService.currentProfile(55)).thenReturn(sample());

        mockMvc.perform(get("/api/profile/me").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Ada"))
                .andExpect(jsonPath("$.data.surname").value("Lovelace"))
                .andExpect(jsonPath("$.data.address").value("London"))
                .andExpect(jsonPath("$.data.phone").value("+39 000"))
                .andExpect(jsonPath("$.data.email").value("ada@unicas.it"))
                .andExpect(jsonPath("$.data.gender").value("F"))
                .andExpect(jsonPath("$.data.birthday").value("1815-12-10"));

        verify(profileService).currentProfile(55);
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The optional fields are part of the contract as {@code string|null}: the key
     * has to be there carrying null, not be dropped from the payload, so the client
     * can bind the form field either way.
     */
    @Test
    void nullOptionalFieldsAreSerialisedAsExplicitNulls() throws Exception {
        when(profileService.currentProfile(55)).thenReturn(
                new ProfileResponse("Ada", "Lovelace", "London", null, "ada@unicas.it", null, null));

        mockMvc.perform(get("/api/profile/me").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.birthday").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.gender").value(org.hamcrest.Matchers.nullValue()));
    }

    // ---- POST /update ----

    @Test
    void updateUsesThePrincipalIdAndEchoesTheStoredProfile() throws Exception {
        when(profileService.update(eq(55), any())).thenReturn(sample());

        mockMvc.perform(post("/api/profile/update").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validEdit())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("ada@unicas.it"));

        verify(profileService).update(eq(55), any());
    }

    /**
     * The email identifies the login: a client that sends one gets it ignored,
     * because the request DTO has no field to bind it to.
     */
    @Test
    void anEmailSentInTheBodyIsIgnored() throws Exception {
        when(profileService.update(eq(55), any())).thenReturn(sample());
        Map<String, Object> body = validEdit();
        body.put("email", "attacker@evil.example");

        mockMvc.perform(post("/api/profile/update").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@unicas.it"));

        ArgumentCaptor<UpdateProfileRequest> captured = ArgumentCaptor.forClass(UpdateProfileRequest.class);
        verify(profileService).update(eq(55), captured.capture());
        assertThat(captured.getValue().name()).isEqualTo("Grace");
    }

    @Test
    void updateRejectsAMissingMandatoryFieldWith400() throws Exception {
        Map<String, Object> body = validEdit();
        body.remove("address");

        mockMvc.perform(post("/api/profile/update").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Address is required")));

        verify(profileService, never()).update(any(), any());
    }

    @Test
    void updateRejectsABlankNameWith400() throws Exception {
        Map<String, Object> body = validEdit();
        body.put("name", "   ");

        mockMvc.perform(post("/api/profile/update").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/profile/update").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validEdit())))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /change-password ----

    @Test
    void changePasswordReturnsOk() throws Exception {
        Map<String, Object> body = Map.of("currentPassword", "OldPass1!", "newPassword", "NewPass1!");

        mockMvc.perform(post("/api/profile/change-password").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(profileService).changePassword(eq(55), any());
    }

    /**
     * 400, not 401: a mistyped current password must not look like an expired
     * session, or the client's interceptor would sign the user out over a typo.
     */
    @Test
    void aWrongCurrentPasswordIsA400WithTheStandardErrorEnvelope() throws Exception {
        doThrow(new ValidationException("Current password is incorrect."))
                .when(profileService).changePassword(eq(55), any());
        Map<String, Object> body = Map.of("currentPassword", "nope", "newPassword", "NewPass1!");

        mockMvc.perform(post("/api/profile/change-password").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
    }

    @Test
    void aWeakNewPasswordIsA400() throws Exception {
        doThrow(new ValidationException("Password must be at least 8 characters long and contain an "
                + "uppercase letter, a lowercase letter, a digit and a symbol."))
                .when(profileService).changePassword(eq(55), any());
        Map<String, Object> body = Map.of("currentPassword", "OldPass1!", "newPassword", "weak");

        mockMvc.perform(post("/api/profile/change-password").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("uppercase")));
    }

    @Test
    void changePasswordRejectsABlankFieldBeforeReachingTheService() throws Exception {
        Map<String, Object> body = Map.of("currentPassword", "", "newPassword", "NewPass1!");

        mockMvc.perform(post("/api/profile/change-password").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).changePassword(any(), any());
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        Map<String, Object> body = Map.of("currentPassword", "OldPass1!", "newPassword", "NewPass1!");

        mockMvc.perform(post("/api/profile/change-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isUnauthorized());
    }
}
