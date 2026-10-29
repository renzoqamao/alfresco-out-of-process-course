package com.curso.alfresco.handler;

import java.time.LocalDate;
import java.util.Map;

import com.curso.alfresco.filter.PropertySetFilter;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.NodeBodyUpdate;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.NodeTypeFilter;
import org.alfresco.event.sdk.handling.filter.PropertyCurrentValueFilter;
import org.alfresco.event.sdk.handling.handler.OnNodeUpdatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Leccion 12: cuando fac:estado pasa a PAGADA, registra la fecha de pago.
 */
@Component
public class FacturaPagadaHandler implements OnNodeUpdatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FacturaPagadaHandler.class);

    private final NodesApi nodesApi;

    public FacturaPagadaHandler(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public EventFilter getEventFilter() {
        return NodeTypeFilter.of("fac:factura")
                .and(PropertySetFilter.of("fac:estado"))
                .and(PropertyCurrentValueFilter.of("fac:estado", "PAGADA"));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        if (node.getProperties().get("fac:fechaPago") != null) {
            return; // idempotente: ya tenia fecha
        }
        String hoy = LocalDate.now().toString();
        nodesApi.updateNode(node.getId(), new NodeBodyUpdate().properties(Map.of("fac:fechaPago", hoy)), null, null);
        LOGGER.info("[FACTURA] {} pagada el {}", node.getProperties().get("fac:numero"), hoy);
    }
}
