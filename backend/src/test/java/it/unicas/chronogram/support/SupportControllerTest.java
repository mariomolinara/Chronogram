package it.unicas.chronogram.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.AuthPrincipal;
import it.unicas.chronogram.security.JwtService;
import it.unicas.chronogram.support.dto.SupportMessageRequest;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the support form: the size and presence rules that must be
 * rejected before any mail is attempted, the mapping of a delivery failure onto
 * the standard error envelope, and the fact that the author is never read from
 * the request body.
 */
@WebMvcTest(controllers = SupportController.class)
@Import(GlobalExceptionHandler.class)
class SupportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SupportService supportService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private Authentication principal() {
        AuthPrincipal p = new AuthPrincipal(55, "ada@unicas.it", Role.USER);
        return new UsernamePasswordAuthenticationToken(p, null, List.of());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static Map<String, Object> valid() {
        return Map.of("subject", "Cannot export", "message", "The CSV button does nothing.");
    }

    @Test
    void aValidMessageIsAcceptedAndAttributedToThePrincipal() throws Exception {
        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<SupportMessageRequest> captured =
                ArgumentCaptor.forClass(SupportMessageRequest.class);
        verify(supportService).submit(eq(55), captured.capture());
        assertThat(captured.getValue().subject()).isEqualTo("Cannot export");
        assertThat(captured.getValue().message()).isEqualTo("The CSV button does nothing.");
    }

    @Test
    void aBlankSubjectIsRejectedWith400() throws Exception {
        Map<String, Object> body = Map.of("subject", "   ", "message", "Something is wrong.");

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Subject is required")));

        verify(supportService, never()).submit(any(), any());
    }

    @Test
    void aBlankMessageIsRejectedWith400() throws Exception {
        Map<String, Object> body = Map.of("subject", "Cannot export", "message", "  ");

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Message is required")));
    }

    @Test
    void anOverlongSubjectIsRejectedWith400() throws Exception {
        Map<String, Object> body = Map.of("subject", "x".repeat(151), "message", "Body.");

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("at most 150")));

        verify(supportService, never()).submit(any(), any());
    }

    @Test
    void anOverlongMessageIsRejectedWith400() throws Exception {
        Map<String, Object> body = Map.of("subject", "Cannot export", "message", "x".repeat(2001));

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("at most 2000")));
    }

    @Test
    void theBoundaryLengthsAreAccepted() throws Exception {
        Map<String, Object> body = Map.of("subject", "x".repeat(150), "message", "x".repeat(2000));

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
    }

    /** SMTP refused the message: 500 with the standard envelope, no stack trace. */
    @Test
    void aDeliveryFailureIsReportedAs500() throws Exception {
        doThrow(new ServiceException("Could not send the support message."))
                .when(supportService).submit(eq(55), any());

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** No mailbox configured on this installation: 503, nothing to retry right now. */
    @Test
    void anUnconfiguredMailboxIsReportedAs503() throws Exception {
        doThrow(new FeatureUnavailableException("Support requests are not available right now."))
                .when(supportService).submit(eq(55), any());

        mockMvc.perform(post("/api/support/messages").with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void theEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/support/messages").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid())))
                .andExpect(status().isUnauthorized());

        verify(supportService, never()).submit(any(), any());
    }
}
