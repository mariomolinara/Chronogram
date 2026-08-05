package it.unicas.chronogram.llm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.UpstreamServiceException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.ActivityType;
import it.unicas.chronogram.llm.dto.LlmResponse;
import it.unicas.chronogram.repository.ActivityTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Unit test for {@link LlmService}. The {@code RestClient} built from the
 * injected builder is bound to a {@link MockRestServiceServer}, so the real
 * provider call is intercepted without any network access.
 * <p>
 * Il contratto sotto test e' la separazione fra i due esiti: un guasto tecnico
 * (chiave assente, status non-2xx, timeout, risposta inutilizzabile) solleva
 * un'eccezione che diventera' 502/503, mentre un'estrazione senza campi utili
 * resta una risposta valida a campi nulli. In nessun caso il messaggio
 * dell'eccezione o i log devono contenere la {@code LLM_API_KEY}, e il messaggio
 * non deve rivelare nulla del provider.
 */
class LlmServiceTest {

    private static final String API_URL = "https://openrouter.example/api/v1/chat/completions";
    private static final String API_KEY = "sk-super-secret-key-DO-NOT-LEAK-42";
    private static final String DEFAULT_MODEL = "deepseek/deepseek-chat-v3-0324:free";

    private MockRestServiceServer server;
    private ActivityTypeRepository activityTypeRepository;
    private LlmService service;

    private ListAppender<ILoggingEvent> logAppender;
    private ch.qos.logback.classic.Logger serviceLogger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        activityTypeRepository = org.mockito.Mockito.mock(ActivityTypeRepository.class);

        ChronogramProperties properties = new ChronogramProperties();
        properties.getLlm().setApiUrl(API_URL);
        properties.getLlm().setApiKey(API_KEY);
        properties.getLlm().setDefaultModel(DEFAULT_MODEL);

        service = new LlmService(builder, new ObjectMapper(), activityTypeRepository, properties);

        // Capture everything the service logs, at all levels, to assert no key leak.
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        serviceLogger = ctx.getLogger(LlmService.class);
        serviceLogger.setLevel(Level.TRACE);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (serviceLogger != null && logAppender != null) {
            serviceLogger.detachAppender(logAppender);
        }
    }

    private ActivityType type(int id, String name) {
        ActivityType t = new ActivityType();
        t.setId(id);
        t.setName(name);
        return t;
    }

    private String chatCompletion(String contentJson) {
        // Emulate an OpenRouter chat-completions envelope. contentJson is the raw
        // assistant message content (a JSON string embedded as a string field).
        String escaped = contentJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"choices\":[{\"message\":{\"content\":\"" + escaped + "\"}}]}";
    }

    private List<String> logMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void assertNoApiKeyInLogs() {
        boolean leaked = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage() != null && e.getFormattedMessage().contains(API_KEY));
        assertThat(leaked)
                .as("LLM_API_KEY must never appear in any log message")
                .isFalse();
    }

    // ---- happy path ----

    @Test
    void happyPathMapsContentAndResolvesActivityType() {
        org.mockito.Mockito.when(activityTypeRepository.findAll())
                .thenReturn(List.of(type(7, "Work")));

        String content = "{\"name\":\"Sprint review\",\"durationMins\":45,\"details\":\"team sync\","
                + "\"pleasantness\":2,\"activityTypeName\":\"Work\",\"recurrence\":\"E\","
                + "\"costEuro\":\"0\",\"location\":\"Office\"}";

        server.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(chatCompletion(content), MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("I had a 45 minute sprint review at the office", null);

        server.verify();
        assertThat(res.name()).isEqualTo("Sprint review");
        assertThat(res.durationMins()).isEqualTo(45);
        assertThat(res.details()).isEqualTo("team sync");
        assertThat(res.pleasantness()).isEqualTo(2);
        assertThat(res.activityTypeId()).isEqualTo(7); // resolved case-insensitively
        assertThat(res.recurrence()).isEqualTo("E");
        assertThat(res.costEuro()).isEqualTo("0");
        assertThat(res.location()).isEqualTo("Office");
        assertNoApiKeyInLogs();
    }

    @Test
    void unknownActivityTypeNameResolvesToNullId() {
        org.mockito.Mockito.when(activityTypeRepository.findAll())
                .thenReturn(List.of(type(7, "Work")));

        String content = "{\"name\":\"Yoga\",\"activityTypeName\":\"Sport\"}";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(chatCompletion(content), MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("did yoga", null);

        assertThat(res.name()).isEqualTo("Yoga");
        assertThat(res.activityTypeId()).isNull();
    }

    @Test
    void contentWrappedInMarkdownIsStillExtracted() {
        // The model wraps JSON in prose/markdown; extractJsonObject must recover it.
        String content = "Here is the result:\\n```json\\n{\\\"name\\\":\\\"Reading\\\"}\\n```";
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}",
                        MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("read a book", null);

        assertThat(res.name()).isEqualTo("Reading");
    }

    // ---- estrazione vuota: resta una risposta valida (200 lato controller) ----

    @Test
    void emptyJsonObjectYieldsNullFieldsAndIsFlaggedAsEmptyExtraction() {
        // Il provider ha risposto correttamente, ma nella frase non c'era nulla
        // da estrarre: NON e' un guasto, il client deve poter chiedere all'utente
        // di riformulare.
        org.mockito.Mockito.when(activityTypeRepository.findAll()).thenReturn(List.of());
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(chatCompletion("{}"), MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("asdfgh", null);

        assertThat(res.name()).isNull();
        assertThat(res.durationMins()).isNull();
        assertThat(res.details()).isNull();
        assertThat(res.pleasantness()).isNull();
        assertThat(res.activityTypeId()).isNull();
        assertThat(res.recurrence()).isNull();
        assertThat(res.location()).isNull();
        assertThat(res.costEuro()).isEqualTo(""); // normalizzato dal parsing
        assertThat(res.isEmpty()).isTrue();
        // Il log deve dire "estrazione vuota", non "guasto".
        assertThat(logMessages()).anyMatch(m -> m.contains("LLM empty extraction"));
        assertThat(logMessages()).noneMatch(m -> m.contains("LLM upstream failure"));
    }

    @Test
    void contentWithoutJsonObjectYieldsEmptyFields() {
        // Valid envelope but the assistant answered with no JSON object at all.
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(chatCompletion("Sorry, I cannot help."), MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("anything", null);

        // extractJsonObject falls back to "{}" -> all fields null/empty, no crash.
        assertThat(res.name()).isNull();
        assertThat(res.durationMins()).isNull();
        assertThat(res.costEuro()).isEqualTo("");
        assertThat(res.isEmpty()).isTrue();
    }

    // ---- guasto tecnico: deve propagarsi (502/503 lato controller) ----

    @Test
    void upstream401IsReportedAsUpstreamFailureWithoutLeakingProviderDetails() {
        // Il caso reale visto in produzione: id del modello non abilitato per la
        // chiave -> 401 dal provider. Deve diventare un guasto, non "riformula".
        String providerBody = "{\"error\":{\"message\":\"key not allowed to access model. "
                + "This key can only access models=['Llama-3.3-70B-Instruct']\","
                + "\"type\":\"key_model_access_denied\",\"code\":\"401\"}}";
        server.expect(requestTo(API_URL))
                .andRespond(withStatus(UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(providerBody));

        assertThatThrownBy(() -> service.extract("I had a 45 minute sprint review", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE)
                // Nessun dettaglio su provider, modello o chiave nel messaggio
                // che finira' nell'envelope restituito al client.
                .hasMessageNotContaining("401")
                .hasMessageNotContaining("model")
                .hasMessageNotContaining("key");

        // Il dettaglio tecnico deve invece essere finito nei log del server.
        assertThat(logMessages()).anyMatch(m -> m.contains("LLM upstream failure")
                && m.contains("401")
                && m.contains(DEFAULT_MODEL));
        assertNoApiKeyInLogs();
    }

    @Test
    void upstream5xxIsReportedAsUpstreamFailureWithoutLeakingKey() {
        server.expect(requestTo(API_URL))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.extract("anything", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);

        assertNoApiKeyInLogs();
    }

    @Test
    void malformedUpstreamBodyIsReportedAsUpstreamFailure() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("this-is-not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.extract("anything", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);

        assertNoApiKeyInLogs();
    }

    @Test
    void unexpectedEnvelopeWithoutMessageContentIsReportedAsUpstreamFailure() {
        // 2xx ma senza choices[0].message.content: forma sconosciuta, quindi
        // guasto del provider e non frase incomprensibile.
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"error\":\"quota exceeded\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.extract("anything", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);
    }

    @Test
    void emptyUpstreamBodyIsReportedAsUpstreamFailure() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.extract("anything", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);
    }

    @Test
    void unparsableJsonInModelContentIsReportedAsUpstreamFailure() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(chatCompletion("{name: 'Reading', }}"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.extract("read a book", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);
    }

    @Test
    void networkTimeoutIsReportedAsUpstreamFailureWithoutLeakingKey() {
        server.expect(requestTo(API_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.extract("anything", null))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);

        assertNoApiKeyInLogs();
    }

    // ---- api-key guardrails ----

    @Test
    void missingApiKeyIsReportedAsUnavailableFeatureWithoutCallingUpstream() {
        ChronogramProperties properties = new ChronogramProperties();
        properties.getLlm().setApiUrl(API_URL);
        properties.getLlm().setApiKey("");   // not configured
        properties.getLlm().setDefaultModel(DEFAULT_MODEL);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer localServer = MockRestServiceServer.bindTo(builder).build();
        // No expectations registered: any HTTP call would fail the test.
        LlmService noKeyService =
                new LlmService(builder, new ObjectMapper(), activityTypeRepository, properties);

        assertThatThrownBy(() -> noKeyService.extract("anything", null))
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessage(LlmService.UNAVAILABLE_MESSAGE);

        localServer.verify(); // proves no request was issued
    }

    @Test
    void apiKeyNeverAppearsInTheReturnedDtoOnSuccess() {
        org.mockito.Mockito.when(activityTypeRepository.findAll()).thenReturn(List.of());
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(chatCompletion("{\"name\":\"X\"}"), MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("x", null);

        assertThat(res.toString()).doesNotContain(API_KEY);
        assertNoApiKeyInLogs();
    }

    // ---- model selection ----

    @Test
    void invalidRequestedModelFallsBackToDefault() {
        org.mockito.Mockito.when(activityTypeRepository.findAll()).thenReturn(List.of());
        server.expect(requestTo(API_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .jsonPath("$.model").value(DEFAULT_MODEL))
                .andRespond(withSuccess(chatCompletion("{\"name\":\"X\"}"), MediaType.APPLICATION_JSON));

        service.extract("x", "not a valid model!!"); // contains spaces/'!'

        server.verify();
    }

    @Test
    void validRequestedModelIsForwarded() {
        org.mockito.Mockito.when(activityTypeRepository.findAll()).thenReturn(List.of());
        String customModel = "openai/gpt-4o-mini";
        server.expect(requestTo(API_URL))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .jsonPath("$.model").value(customModel))
                .andRespond(withSuccess(chatCompletion("{\"name\":\"X\"}"), MediaType.APPLICATION_JSON));

        service.extract("x", customModel);

        server.verify();
    }
}
