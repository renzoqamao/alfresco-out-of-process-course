package com.curso.alfresco.handler;

import java.util.Map;

import com.curso.alfresco.filter.PropertySetFilter;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lecciones 5 y 9: eventos de actualizacion, resourceBefore y filtros de propiedades.
 */
@Component
public class TitleChangedHandler implements OnNodeUpdatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TitleChangedHandler.class);

    @Override
    public EventFilter getEventFilter() {
        return PropertySetFilter.of("cm:title");
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource after = (NodeResource) event.getData().getResource();
        Map<String, ?> before = ((NodeResource) event.getData().getResourceBefore()).getProperties();
        LOGGER.info("[TITULO] '{}': '{}' -> '{}'", after.getName(), before.get("cm:title"), after.getProperties().get("cm:title"));
    }
}
