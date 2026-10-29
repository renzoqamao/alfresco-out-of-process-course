package com.curso.alfresco.handler;

import java.util.Map;

import org.alfresco.core.handler.CommentsApi;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.CommentBody;
import org.alfresco.core.model.NodeBodyUpdate;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.NodeTypeFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Leccion 12: regla de negocio sobre un tipo propio.
 * Facturas hasta el umbral se aprueban solas; las demas quedan PENDIENTE con un comentario.
 */
@Component
public class FacturaCreatedHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FacturaCreatedHandler.class);

    private final NodesApi nodesApi;
    private final CommentsApi commentsApi;
    private final double umbral;

    public FacturaCreatedHandler(NodesApi nodesApi, CommentsApi commentsApi,
                                 @Value("${curso.factura.umbral-aprobacion}") double umbral) {
        this.nodesApi = nodesApi;
        this.commentsApi = commentsApi;
        this.umbral = umbral;
    }

    @Override
    public EventFilter getEventFilter() {
        // NodeTypeFilter compara el tipo exacto: no incluye subtipos
        return NodeTypeFilter.of("fac:factura");
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        // Las propiedades d:double llegan como Number desde el JSON del evento
        Number importe = (Number) node.getProperties().get("fac:importe");
        if (importe == null) {
            LOGGER.warn("[FACTURA] '{}' sin fac:importe, se deja PENDIENTE", node.getName());
            return;
        }
        if (importe.doubleValue() <= umbral) {
            nodesApi.updateNode(node.getId(), new NodeBodyUpdate().properties(Map.of("fac:estado", "APROBADA")), null, null);
            LOGGER.info("[FACTURA] {} ({}) aprobada automaticamente", node.getProperties().get("fac:numero"), importe);
        } else {
            commentsApi.createComment(node.getId(),
                    new CommentBody().content("Importe mayor a " + umbral + ": requiere aprobacion manual"), null);
            LOGGER.info("[FACTURA] {} ({}) requiere aprobacion manual", node.getProperties().get("fac:numero"), importe);
        }
    }
}
