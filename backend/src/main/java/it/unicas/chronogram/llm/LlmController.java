package it.unicas.chronogram.llm;

import it.unicas.chronogram.llm.dto.LlmPromptRequest;
import it.unicas.chronogram.llm.dto.LlmResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoint that extracts structured activity data from a prompt.
 * Returns the {@link LlmResponse} object directly (not wrapped), preserving the
 * legacy contract where the JSON root was the extracted data object.
 * <p>
 * Solo il caso di successo e' non incapsulato: un'estrazione senza campi utili
 * resta 200 con tutti i campi nulli (il client chiede di riformulare la frase),
 * mentre un guasto del servizio LLM diventa 502/503 con il consueto envelope
 * {@code ApiResponse.fail(...)} prodotto dal {@code GlobalExceptionHandler}.
 * Il client deve quindi distinguere i due esiti dallo status HTTP.
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmService llmService;

    public LlmController(LlmService llmService) {
        this.llmService = llmService;
    }

    @PostMapping("/prompt")
    public LlmResponse prompt(@Valid @RequestBody LlmPromptRequest request) {
        return llmService.extract(request.prompt(), request.model());
    }
}
