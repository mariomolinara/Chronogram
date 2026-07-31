package it.unicas.chronogram.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.ActivityType;
import it.unicas.chronogram.llm.dto.LlmResponse;
import it.unicas.chronogram.repository.ActivityTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts structured activity data from a free-text prompt by calling an
 * OpenRouter-compatible chat-completions endpoint and mapping the returned
 * activity-type name to its database id.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final String MODEL_PATTERN = "^[a-zA-Z0-9/_\\-:]+$";

    private static final String SYSTEM_PROMPT = """
            You are an expert data extractor for a time-tracking app called Chronogram. \
            Analyze the user's input and extract the following fields as a flat JSON object: \
            name (string), durationMins (integer in minutes), details (string), pleasantness (integer from -3 to +3), \
            activityTypeName (string), recurrence ('R' or 'E'), costEuro (string), and location (string). \
            If a value is not present, omit the key. \
            Respond with a single valid JSON object only. Do NOT include any explanations, comments, or markdown.""";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ActivityTypeRepository activityTypeRepository;
    private final ChronogramProperties.Llm props;

    public LlmService(RestClient.Builder restClientBuilder,
                      ObjectMapper objectMapper,
                      ActivityTypeRepository activityTypeRepository,
                      ChronogramProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.activityTypeRepository = activityTypeRepository;
        this.props = properties.getLlm();
    }

    public LlmResponse extract(String prompt, String requestedModel) {
        if (!StringUtils.hasText(props.getApiKey())) {
            log.error("LLM API key is missing (chronogram.llm.api-key / LLM_API_KEY).");
            return LlmResponse.empty();
        }

        String model = resolveModel(requestedModel);
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", java.util.List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", prompt == null ? "" : prompt)));

            String raw = restClient.post()
                    .uri(props.getApiUrl())
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            log.debug("Raw LLM content: {}", content);
            return parseContent(content);
        } catch (Exception e) {
            log.error("Error communicating with or parsing the LLM response", e);
            return LlmResponse.empty();
        }
    }

    private LlmResponse parseContent(String content) throws Exception {
        String json = extractJsonObject(content);
        JsonNode node = objectMapper.readTree(json);

        Integer activityTypeId = resolveActivityTypeId(text(node, "activityTypeName"));

        return new LlmResponse(
                text(node, "name"),
                node.hasNonNull("durationMins") ? node.get("durationMins").asInt() : null,
                text(node, "details"),
                node.hasNonNull("pleasantness") ? node.get("pleasantness").asInt() : null,
                activityTypeId,
                text(node, "recurrence"),
                node.hasNonNull("costEuro") ? node.get("costEuro").asText() : "",
                text(node, "location"));
    }

    private String resolveModel(String requested) {
        if (requested != null && requested.matches(MODEL_PATTERN)) {
            return requested;
        }
        if (requested != null) {
            log.warn("Invalid model '{}' — using default.", requested);
        }
        return props.getDefaultModel();
    }

    private Integer resolveActivityTypeId(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        Map<String, Integer> byName = activityTypeRepository.findAll().stream()
                .collect(Collectors.toMap(
                        t -> t.getName().trim().toLowerCase(Locale.ROOT),
                        ActivityType::getId,
                        (a, b) -> a));
        return byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private String extractJsonObject(String response) {
        int first = response.indexOf('{');
        int last = response.lastIndexOf('}');
        if (first != -1 && last > first) {
            return response.substring(first, last + 1);
        }
        log.warn("Could not find valid JSON in LLM response: {}", response);
        return "{}";
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
