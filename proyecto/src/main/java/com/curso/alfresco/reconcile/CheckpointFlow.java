package com.curso.alfresco.reconcile;

import org.alfresco.event.sdk.handling.filter.EventTypeFilter;
import org.alfresco.event.sdk.integration.EventChannels;
import org.alfresco.event.sdk.integration.filter.IntegrationEventFilter;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

/**
 * Leccion 11: cada evento de nodo avanza el checkpoint.
 */
@Configuration
public class CheckpointFlow {

    @Bean
    public IntegrationFlow recordCheckpoint(Checkpoint checkpoint) {
        return IntegrationFlow.from(EventChannels.MAIN)
                .filter(IntegrationEventFilter.of(EventTypeFilter.NODE), f -> f.discardChannel("nullChannel"))
                .handle(message -> checkpoint.record(((RepoEvent<?>) message.getPayload()).getTime()))
                .get();
    }
}
