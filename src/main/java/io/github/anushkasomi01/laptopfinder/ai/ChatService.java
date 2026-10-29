package io.github.anushkasomi01.laptopfinder.ai;

import io.github.anushkasomi01.laptopfinder.api.dto.ChatReply;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * One request, one independent model call. User text is a user message,
     * never part of the system prompt or a manually interpolated prompt template.
     */
    public ChatReply chat(String message) {
        Assert.hasText(message, "message must not be blank");
        String answer;
        try {
            answer = chatClient.prompt()
                    .user(message.strip())
                    .call()
                    .content();
        }
        catch (RuntimeException exception) {
            // Do not log provider messages, request bodies, authorization headers, or prompts.
            throw new ModelProviderException(exception);
        }

        if (answer == null || answer.isBlank()) {
            throw new EmptyModelResponseException();
        }
        return new ChatReply(answer.strip());
    }
}
