package com.curso.alfresco.integration;

import org.alfresco.event.sdk.handling.filter.EventTypeFilter;
import org.alfresco.event.sdk.integration.EventChannels;
import org.alfresco.event.sdk.integration.filter.IntegrationEventFilter;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;

/**
 * Leccion 8: la alternativa a los handlers, un flujo de Spring Integration sobre el canal principal.
 * Requiere alfresco.events.enable-spring-integration=true
 */
@Configuration
public class DeletedNodesFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedNodesFlow.class);

    @Bean
    public IntegrationFlow logDeletedNodes() {
        return IntegrationFlow.from(EventChannels.MAIN)
                // Sin discardChannel, Spring Integration registra un WARN por cada evento rechazado
                .filter(IntegrationEventFilter.of(EventTypeFilter.NODE_DELETED), f -> f.discardChannel("nullChannel"))
                .handle(message -> {
                    RepoEvent<?> event = (RepoEvent<?>) message.getPayload();
                    NodeResource node = (NodeResource) event.getData().getResource();
                    LOGGER.info("[BORRADO] '{}' ({}) enviado a la papelera", node.getName(), node.getId());
                })
                .get();
    }
}
