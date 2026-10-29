package com.curso.alfresco.handler;

import java.util.Map;

import com.curso.alfresco.filter.NameEndsWithFilter;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.NodeBodyUpdate;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.IsFileFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Leccion 7: modificar el nodo que disparo el evento.
 * Al crear un .txt le pone un titulo; eso genera a su vez un evento node.Updated (ver TitleChangedHandler).
 */
@Component
public class TxtAutoTitleHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TxtAutoTitleHandler.class);

    private final NodesApi nodesApi;

    public TxtAutoTitleHandler(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public EventFilter getEventFilter() {
        return IsFileFilter.get().and(NameEndsWithFilter.of(".txt"));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        String title = "Texto: " + node.getName();
        LOGGER.info("[TXT] Asignando cm:title='{}' a {}", title, node.getId());

        // Escribir cm:title agrega automaticamente el aspecto cm:titled
        nodesApi.updateNode(node.getId(), new NodeBodyUpdate().properties(Map.of("cm:title", title)), null, null);
    }
}
