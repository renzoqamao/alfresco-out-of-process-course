package com.curso.alfresco.handler;

import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Leccion 4: el handler mas simple posible. Sin filtro, recibe TODOS los nodos creados.
 */
@Component
public class NodeCreatedLoggerHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeCreatedLoggerHandler.class);

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        // Desde ACS 25.x/26.x el evento trae la ruta legible en extensionAttributes.path
        Object path = event.getExtensionAttributes() == null ? null : event.getExtensionAttributes().getExtension("path");
        LOGGER.info("[CREADO] nombre='{}' tipo={} id={} usuario={} ruta={}",
                node.getName(), node.getNodeType(), node.getId(), node.getCreatedByUser().getId(), path);
    }
}
