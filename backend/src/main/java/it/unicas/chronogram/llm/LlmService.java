package it.unicas.chronogram.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.UpstreamServiceException;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts structured activity data from a free-text prompt by calling an
 * OpenAI-compatible chat-completions endpoint and mapping the returned
 * activity-type name to its database id.
 * <p>
 * Distingue due esiti che prima collassavano entrambi su {@link LlmResponse#empty()}:
 * <ul>
 *   <li><b>guasto tecnico</b> (chiave assente, status non-2xx del provider,
 *       errore di rete/timeout, risposta inutilizzabile): il metodo solleva
 *       un'eccezione, il client riceve 502/503 e un messaggio generico. Il
 *       dettaglio — status, corpo dell'errore, id del modello richiesto — resta
 *       nei log a livello ERROR;</li>
 *   <li><b>estrazione vuota</b> (il provider ha risposto ma la frase non
 *       conteneva dati): ritorna un {@link LlmResponse} a campi nulli, loggato a
 *       INFO, e il client mostra "riformula la frase".</li>
 * </ul>
 * Confonderli faceva arrivare all'utente un errore di configurazione (un id di
 * modello non abilitato per la chiave, quindi 401 dal provider) travestito da
 * "non ho capito quello che hai scritto", rendendo il guasto invisibile.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final String MODEL_PATTERN = "^[a-zA-Z0-9/_\\-:]+$";

    /**
     * Unico messaggio restituito al client per qualunque guasto tecnico: non
     * rivela nulla su chiave, provider o modello, e dice all'utente cosa fare.
     */
    public static final String UNAVAILABLE_MESSAGE =
            "The AI assistant is temporarily unavailable. Please fill the form manually and try again later.";

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

    /**
     * @return i campi estratti, eventualmente tutti nulli se la frase non ne
     *         conteneva
     * @throws FeatureUnavailableException se la chiave API non e' configurata
     * @throws UpstreamServiceException    se la chiamata al provider fallisce o
     *                                     la risposta non e' utilizzabile
     */
    public LlmResponse extract(String prompt, String requestedModel) {
        if (!StringUtils.hasText(props.getApiKey())) {
            // Errore di configurazione del server, non della frase dell'utente.
            log.error("LLM upstream failure: API key is not configured (chronogram.llm.api-key / LLM_API_KEY).");
            throw new FeatureUnavailableException(UNAVAILABLE_MESSAGE);
        }

        String model = resolveModel(requestedModel);
        String content = messageContent(callProvider(prompt, model), model);
        log.debug("Raw LLM content: {}", content);

        LlmResponse response = parseContent(content, model);
        if (response.isEmpty()) {
            // Esito legittimo: il provider ha funzionato, la frase non conteneva
            // dati riconoscibili. Loggato a INFO e con un marcatore diverso dal
            // guasto tecnico, cosi' dal log si capisce subito quale dei due e'.
            log.info("LLM empty extraction: the provider answered but no usable field was found (model '{}').", model);
        }
        return response;
    }

    /** Esegue la chiamata HTTP; ogni fallimento diventa un 502 per il client. */
    private String callProvider(String prompt, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt == null ? "" : prompt)));

        try {
            return restClient.post()
                    .uri(props.getApiUrl())
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // Status non-2xx: status, corpo e id del modello sono l'unica traccia
            // che permette di riconoscere una chiave scaduta, un rate limit o un
            // modello non abilitato. Restano nel log, mai nella risposta HTTP.
            log.error("LLM upstream failure: provider responded {} for model '{}'. Response body: {}",
                    e.getStatusCode(), model, e.getResponseBodyAsString(), e);
            throw new UpstreamServiceException(UNAVAILABLE_MESSAGE, e);
        } catch (RestClientException e) {
            // Nessuna risposta: DNS, connessione rifiutata, timeout.
            log.error("LLM upstream failure: call to the provider failed (network or timeout) for model '{}'.",
                    model, e);
            throw new UpstreamServiceException(UNAVAILABLE_MESSAGE, e);
        }
    }

    /**
     * Estrae {@code choices[0].message.content} dall'envelope del provider. Una
     * risposta 2xx di forma inattesa e' comunque un guasto del provider: va
     * segnalata come tale e non degradata a "riformula la frase".
     */
    private String messageContent(String raw, String model) {
        if (!StringUtils.hasText(raw)) {
            log.error("LLM upstream failure: provider returned an empty body (model '{}').", model);
            throw new UpstreamServiceException(UNAVAILABLE_MESSAGE);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            log.error("LLM upstream failure: provider response is not valid JSON (model '{}'). Raw response: {}",
                    model, raw, e);
            throw new UpstreamServiceException(UNAVAILABLE_MESSAGE, e);
        }

        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            log.error("LLM upstream failure: unexpected response envelope, "
                    + "no textual choices[0].message.content (model '{}').", model);
            throw new UpstreamServiceException(UNAVAILABLE_MESSAGE);
        }
        return content.asText();
    }

    private LlmResponse parseContent(String content, String model) {
        String json = extractJsonObject(content);
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            // Il modello ha prodotto qualcosa che assomiglia a JSON ma non lo e':
            // non e' una frase da riformulare, e' una risposta inutilizzabile.
            log.error("LLM upstream failure: the model returned content that is not parsable JSON (model '{}').",
                    model, e);
            throw new UpstreamServiceException(UNAVAILABLE_MESSAGE, e);
        }

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
        // Il modello ha risposto in prosa ("non ho capito"): e' un'estrazione
        // vuota, non un guasto — il fallback a "{}" produce campi nulli.
        log.debug("No JSON object found in the model answer: {}", response);
        return "{}";
    }

    private String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
