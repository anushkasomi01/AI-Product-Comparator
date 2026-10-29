package io.github.anushkasomi01.laptopfinder.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/** Context/property-binding test only. No hosted request, real credential, or cost. */
@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-only-not-a-real-key",
        "spring.ai.openai.base-url=http://127.0.0.1:1",
        "spring.ai.openai.chat.model=test-model",
        "spring.ai.openai.chat.temperature=0.2",
        "spring.ai.openai.chat.max-completion-tokens=512",
        "spring.ai.openai.chat.store=false",
        "spring.ai.openai.max-retries=0",
        "spring.ai.openai.timeout=1s"
})
@ActiveProfiles({"test", "hosted"})
class HostedConfigurationTest {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private Environment environment;

    @Test
    void selectsTheHostedAdapterWithoutChangingBusinessServices() {
        assertThat(chatModel).isInstanceOf(OpenAiChatModel.class);
        assertThat(chatClient).isNotNull();
        assertThat(environment.getProperty("spring.ai.openai.chat.model")).isEqualTo("test-model");
        assertThat(environment.getProperty("spring.ai.openai.max-retries", Integer.class)).isZero();
    }
}
