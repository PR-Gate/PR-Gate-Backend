package com.prgate.ai.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prgate.ai.application.port.out.*;
import com.prgate.ai.infrastructure.outbound.gemini.*;
import com.prgate.ai.infrastructure.outbound.openai.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

class AiProviderSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Adapters.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test void geminiDoesNotRequireOpenAiCredentialsAndRegistersExactlyOneAdapterPerPort() {
        runner.withPropertyValues("ai.provider=gemini", "gemini.api-key=fixture", "gemini.model=fixture",
                "gemini.base-url=http://localhost:1").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(RubricTranslationPort.class).size());
            assertEquals(1, context.getBeansOfType(FeedbackGenerator.class).size());
            assertInstanceOf(GeminiRubricAdapter.class, context.getBean(RubricTranslationPort.class));
            assertInstanceOf(GeminiFeedbackAdapter.class, context.getBean(FeedbackGenerator.class));
        });
    }
    @Test void openAiRemainsTheDefaultAndDoesNotRequireGeminiCredentials() {
        runner.withPropertyValues("openai.api-key=fixture", "openai.model=fixture",
                "openai.base-url=http://localhost:1").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(RubricTranslationPort.class).size());
            assertEquals(1, context.getBeansOfType(FeedbackGenerator.class).size());
            assertInstanceOf(OpenAiRubricAdapter.class, context.getBean(RubricTranslationPort.class));
            assertTrue(context.getBeansOfType(GeminiClient.class).isEmpty());
        });
    }
    @Configuration(proxyBeanMethods = false)
    @Import({GeminiClient.class, GeminiRubricAdapter.class, GeminiFeedbackAdapter.class,
             OpenAiRubricAdapter.class, OpenAiFeedbackAdapter.class})
    static class Adapters {}
}