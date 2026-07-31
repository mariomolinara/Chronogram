package it.unicas.chronogram.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.GlobalExceptionHandler;
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
 * Slice test for the public LLM endpoint. The controller returns the
 * {@link LlmResponse} object directly (legacy unwrapped contract), so the
 * assertions target the JSON root rather than an {@code ApiResponse} envelope.
 * Focus: request-DTO Bean Validation (400 on blank prompt) and that the
 * {@code model} field is passed through to the service.
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
