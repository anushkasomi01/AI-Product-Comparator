package io.github.anushkasomi01.laptopfinder.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * AI wiring belongs here, not in controllers or search/business services.
 * Boot supplies the ChatClient.Builder for the selected provider's ChatModel.
 */
@Configuration(proxyBeanMethods = false)
public class ChatConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
            @Value("classpath:prompts/assistant-system.txt") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                // Deliberately no memory advisor, tools, or vector-store advisor in Phase 1.
                .build();
    }
}
