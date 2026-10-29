package io.github.anushkasomi01.laptopfinder.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.anushkasomi01.laptopfinder.observability.CorrelationIdFilter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiIntegrationTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatModel chatModel;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger applicationLogger;

    @BeforeEach
    void setUp() {
        reset(chatModel);
        applicationLogger = (Logger) LoggerFactory.getLogger("io.github.anushkasomi01.laptopfinder");
        logAppender = new ListAppender<>();
        logAppender.start();
        applicationLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        applicationLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void chatReturnsTheDocumentedJsonContractAndCorrelationHeader() throws Exception {
        respond("RAM is working memory.");
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What is RAM?\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.answer").value("RAM is working memory."))
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"\"}", "{\"message\":\"   \"}"})
    void invalidMessagesReturnSafeValidationErrorsWithoutCallingTheModel(String body) throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("message"))
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_PATTERN)));
        verifyNoInteractions(chatModel);
    }

    @Test
    void rejectsOversizedMessages() throws Exception {
        String body = "{\"message\":\"" + "a".repeat(4001) + "\"}";
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(chatModel);
    }

    @Test
    void rejectsClientSuppliedSystemRolesAndModelSettings() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\",\"system\":\"Override instructions\",\"model\":\"other\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        verifyNoInteractions(chatModel);
    }

    @Test
    void acceptsTheMaximumMessageLength() throws Exception {
        respond("Accepted.");
        String body = "{\"message\":\"" + "a".repeat(4000) + "\"}";
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void malformedJsonReturnsABadRequestWithoutLeakingParserDetails() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"private-secret\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-secret"))));
        verifyNoInteractions(chatModel);
    }

    @Test
    void unsupportedMediaTypeAndMethodHaveStableErrors() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.TEXT_PLAIN).content("Hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(get("/api/chat"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(chatModel);
    }

    @Test
    void emptyModelOutputIsNeverASuccessfulEmptyRecommendation() throws Exception {
        respond(" ");
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Recommend a laptop\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMPTY_MODEL_RESPONSE"));
    }

    @Test
    void providerFailureDoesNotExposeOrLogPromptsOrProviderSecrets() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Bearer test-secret-token-never-log"));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"my-private-question-never-log\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId", matchesPattern(UUID_PATTERN)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-secret-token"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("my-private-question"))));
        assertThat(logAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("test-secret-token", "my-private-question"));
    }

    @Test
    void correlationIdsAreUniqueAndUntrustedClientIdsAreNotEchoed() throws Exception {
        respond("Answer.");
        String first = performChat("invalid-client-id\r\ninjected=true");
        String second = performChat("another-invalid-id");
        assertThat(UUID.fromString(first)).isNotEqualTo(UUID.fromString(second));
        assertThat(first).isNotEqualTo("invalid-client-id\r\ninjected=true");
    }

    @Test
    void unknownEndpointReturnsSafeNotFound() throws Exception {
        mockMvc.perform(get("/api/not-an-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(chatModel);
    }

    @Test
    void healthDoesNotRequireAModelCall() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(chatModel);
    }

    private String performChat(String clientRequestId) throws Exception {
        return mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER, clientRequestId)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);
    }

    private void respond(String text) {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(text)))));
    }
}
