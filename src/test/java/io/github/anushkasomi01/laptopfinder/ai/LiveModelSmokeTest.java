package io.github.anushkasomi01.laptopfinder.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Opt-in connectivity test, not a benchmark or a semantic-quality evaluation.
 * Run only with a configured, running model. Never runs in ordinary CI.
 */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.ai.model.chat=ollama",
        "spring.http.clients.connect-timeout=5s",
        "spring.http.clients.read-timeout=120s"
})
class LiveModelSmokeTest {

    @Autowired
    private ChatService chatService;

    @Test
    void realModelReturnsANonemptyAnswerThroughSpringAi() {
        var reply = chatService.chat("What is laptop RAM? Explain in one short sentence.");
        assertThat(reply.answer()).isNotBlank();
        // Do not assert exact generated wording and do not log the response.
    }
}
