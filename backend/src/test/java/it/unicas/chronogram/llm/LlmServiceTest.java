package it.unicas.chronogram.llm;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Unit test for {@link LlmService}. The {@code RestClient} built from the
 * injected builder is bound to a {@link MockRestServiceServer}, so the real
 * OpenRouter call is intercepted without any network access.
 * <p>
 * The service deliberately never propagates upstream failures: any transport,
 * status or parsing error is caught and mapped to {@link LlmResponse#empty()},
 * i.e. graceful degradation rather than a crash. These tests pin that contract
 * as it is written today and assert the {@code LLM_API_KEY} never leaks into the
 * returned DTO or the application logs.
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

    // ---- upstream / robustness ----

    @Test
    void upstream5xxIsMappedToEmptyResponseWithoutCrashingOrLeakingKey() {
        server.expect(requestTo(API_URL))
                .andRespond(withServerError());

        LlmResponse res = service.extract("anything", null);

        assertThat(res).isEqualTo(LlmResponse.empty());
        assertNoApiKeyInLogs();
    }

    @Test
    void malformedUpstreamBodyIsMappedToEmptyResponse() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("this-is-not-json", MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("anything", null);

        assertThat(res).isEqualTo(LlmResponse.empty());
        assertNoApiKeyInLogs();
    }

    @Test
    void emptyUpstreamBodyIsMappedToEmptyResponse() {
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        LlmResponse res = service.extract("anything", null);

        assertThat(res).isEqualTo(LlmResponse.empty());
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
    }

    @Test
    void networkTimeoutIsMappedToEmptyResponseWithoutLeakingKey() {
        server.expect(requestTo(API_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        LlmResponse res = service.extract("anything", null);

        assertThat(res).isEqualTo(LlmResponse.empty());
        assertNoApiKeyInLogs();
    }

    // ---- api-key guardrails ----

    @Test
    void missingApiKeyShortCircuitsToEmptyResponseWithoutCallingUpstream() {
        ChronogramProperties properties = new ChronogramProperties();
        properties.getLlm().setApiUrl(API_URL);
        properties.getLlm().setApiKey("");   // not configured
        properties.getLlm().setDefaultModel(DEFAULT_MODEL);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer localServer = MockRestServiceServer.bindTo(builder).build();
        // No expectations registered: any HTTP call would fail the test.
        LlmService noKeyService =
                new LlmService(builder, new ObjectMapper(), activityTypeRepository, properties);

        LlmResponse res = noKeyService.extract("anything", null);

        assertThat(res).isEqualTo(LlmResponse.empty());
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
