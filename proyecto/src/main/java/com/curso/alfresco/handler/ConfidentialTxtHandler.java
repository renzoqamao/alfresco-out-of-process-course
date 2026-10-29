package com.curso.alfresco.handler;

import java.util.Set;

import com.curso.alfresco.service.ConfidentialClassifier;
import org.alfresco.event.sdk.handling.filter.ContentChangedFilter;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.EventTypeFilter;
import org.alfresco.event.sdk.handling.filter.IsFileFilter;
import org.alfresco.event.sdk.handling.filter.MimeTypeFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.EventType;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Leccion 11: el handler solo decide y delega; el trabajo pesado corre en otro hilo.
 */
@Component
public class ConfidentialTxtHandler implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfidentialTxtHandler.class);

    private final ConfidentialClassifier classifier;

    public ConfidentialTxtHandler(ConfidentialClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.NODE_CREATED, EventType.NODE_UPDATED);
    }

    @Override
    public EventFilter getEventFilter() {
        // Texto plano recien creado, o al que le cambiaron el contenido
        return IsFileFilter.get()
                .and(MimeTypeFilter.of("text/plain"))
                .and(EventTypeFilter.NODE_CREATED.or(ContentChangedFilter.get()));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        LOGGER.info("[ASYNC] Delegando '{}' al clasificador (hilo {})", node.getName(), Thread.currentThread().getName());
        classifier.classifyAsync(node.getId());
    }
}
