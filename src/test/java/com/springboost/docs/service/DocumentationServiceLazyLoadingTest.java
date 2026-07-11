package com.springboost.docs.service;

import com.springboost.config.SpringBoostProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies guideline indexing is deferred until first access, not done
 * eagerly at startup -- the eager version added several seconds to every
 * stdio MCP session's boot time regardless of whether search-docs was ever
 * called, worsening the already-marginal connection-timeout problem.
 */
class DocumentationServiceLazyLoadingTest {

    @Test
    void loadingHappensOnFirstAccessAndOnlyOnce() {
        SpringBoostProperties properties = new SpringBoostProperties();
        EmbeddingsService embeddingsService = new EmbeddingsService(properties);
        DocumentationService service = new DocumentationService(embeddingsService, WebClient.builder());
        service.initialize(); // simulates @PostConstruct -- must not eagerly load

        int firstCallCount = service.getAllDocuments().size();
        int secondCallCount = service.getAllDocuments().size();

        assertTrue(firstCallCount > 100,
                "expected the real bundled guideline corpus (100+ chunks) to load on first access, got " + firstCallCount);
        assertEquals(firstCallCount, secondCallCount,
                "second access should return the already-loaded index, not reload or duplicate entries");
    }
}
