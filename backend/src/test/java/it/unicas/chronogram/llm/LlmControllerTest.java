package it.unicas.chronogram.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.UpstreamServiceException;
import it.unicas.chronogram.llm.dto.LlmResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the authenticated LLM endpoint. The controller returns the
 * {@link LlmResponse} object directly (legacy unwrapped contract), so the
 * assertions target the JSON root rather than an {@code ApiResponse} envelope.
 * Focus: request-DTO Bean Validation (400 on blank prompt), il passaggio del
 * campo {@code model} al service e soprattutto la distinzione fra estrazione
 * vuota (200 con campi nulli) e guasto del servizio LLM (502/503 con envelope
 * {@code ApiResponse.fail}), che prima erano indistinguibili per il client.
 */
@WebMvcTest(controllers = LlmController.class)
@Import(GlobalExceptionHandler.class)
@WithMockUser
class LlmControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private LlmService llmService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void promptReturnsExtractedFieldsAtJsonRoot() throws Exception {
        when(llmService.extract(eq("I read for 30 minutes"), any()))
                .thenReturn(new LlmResponse("Reading", 30, "novel", 2, 7, "E", "0", "Home"));
        Map<String, Object> body = Map.of("prompt", "I read for 30 minutes", "model", "openai/gpt-4o-mini");

        mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reading"))
                .andExpect(jsonPath("$.durationMins").value(30))
                .andExpect(jsonPath("$.activityTypeId").value(7));

        verify(llmService).extract("I read for 30 minutes", "openai/gpt-4o-mini");
    }

    @Test
    void promptWorksWithoutModelField() throws Exception {
        when(llmService.extract(eq("did some work"), any()))
                .thenReturn(LlmResponse.empty());
        Map<String, Object> body = Map.of("prompt", "did some work"); // model omitted

        mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());

        verify(llmService).extract("did some work", null);
    }

    @Test
    void emptyExtractionStaysOkWithNullFields() throws Exception {
        // Il provider ha risposto ma non c'era nulla da estrarre: il client
        // mostra "riformula la frase", quindi lo status resta 200.
        when(llmService.extract(eq("asdfgh"), any())).thenReturn(LlmResponse.empty());
        Map<String, Object> body = Map.of("prompt", "asdfgh");

        mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.durationMins").doesNotExist())
                .andExpect(jsonPath("$.activityTypeId").doesNotExist());
    }

    @Test
    void upstreamFailureIsMappedTo502WithGenericMessageAndNoProviderDetails() throws Exception {
        // Il service ha gia' loggato status e corpo dell'errore del provider: la
        // risposta HTTP deve contenere solo il messaggio per l'utente finale.
        when(llmService.extract(any(), any()))
                .thenThrow(new UpstreamServiceException(LlmService.UNAVAILABLE_MESSAGE,
                        new IllegalStateException("401 Unauthorized from https://api.provider.example")));
        Map<String, Object> body = Map.of("prompt", "I had a 45 minute sprint review");

        String responseBody = mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(LlmService.UNAVAILABLE_MESSAGE))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody)
                .as("nessun dettaglio su provider, modello, chiave o status upstream nel body")
                .doesNotContain("401")
                .doesNotContain("Unauthorized")
                .doesNotContain("api.provider.example")
                .doesNotContain("model");
    }

    @Test
    void missingApiKeyIsMappedTo503WithTheSameUserMessage() throws Exception {
        when(llmService.extract(any(), any()))
                .thenThrow(new FeatureUnavailableException(LlmService.UNAVAILABLE_MESSAGE));
        Map<String, Object> body = Map.of("prompt", "I had a 45 minute sprint review");

        mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(LlmService.UNAVAILABLE_MESSAGE));
    }

    @Test
    void blankPromptIsRejectedWith400AndDoesNotHitService() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", "   "); // blank -> @NotBlank fails

        mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(llmService, never()).extract(any(), any());
    }

    @Test
    void missingPromptIsRejectedWith400() throws Exception {
        Map<String, Object> body = Map.of("model", "openai/gpt-4o-mini"); // no prompt

        mockMvc.perform(post("/api/llm/prompt").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(llmService, never()).extract(any(), any());
    }
}
