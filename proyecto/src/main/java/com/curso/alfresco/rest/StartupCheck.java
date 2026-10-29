package com.curso.alfresco.rest;

import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.NodeChildAssociationPaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Leccion 6: primer uso de la REST API. Al arrancar lista las carpetas de Company Home (-root-).
 */
@Component
@ConditionalOnProperty(name = "curso.startup-check.enabled", havingValue = "true")
public class StartupCheck implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupCheck.class);

    private final NodesApi nodesApi;

    public StartupCheck(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public void run(String... args) {
        NodeChildAssociationPaging children = nodesApi
                .listNodeChildren("-root-", 0, 100, null, null, null, null, null, null)
                .getBody();

        LOGGER.info("Conectado a Alfresco. Company Home contiene {} nodos:", children.getList().getPagination().getCount());
        children.getList().getEntries()
                .forEach(e -> LOGGER.info("  - {} ({})", e.getEntry().getName(), e.getEntry().getNodeType()));
    }
}
