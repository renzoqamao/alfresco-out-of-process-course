package com.curso.alfresco.model;

import java.io.IOException;
import java.util.Map;

import feign.FeignException;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.model.NodeBodyCreate;
import org.alfresco.core.model.NodeBodyUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Leccion 12: la extension despliega su propio modelo (modelo dinamico) sin reiniciar Alfresco.
 * Sube el XML a Data Dictionary/Models como cm:dictionaryModel y lo activa con cm:modelActive=true.
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "curso.model-bootstrap.enabled", havingValue = "true")
public class ModelBootstrap implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelBootstrap.class);
    private static final String MODELS_PATH = "Data Dictionary/Models";
    private static final String MODEL_FILE = "factura-model.xml";

    private final NodesApi nodesApi;

    public ModelBootstrap(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public void run(String... args) throws IOException {
        try {
            nodesApi.getNode("-root-", null, MODELS_PATH + "/" + MODEL_FILE, null);
            LOGGER.info("[MODELO] {} ya esta desplegado", MODEL_FILE);
            return;
        } catch (FeignException.NotFound e) {
            LOGGER.info("[MODELO] Desplegando {}", MODEL_FILE);
        }

        String modelsFolderId = nodesApi.getNode("-root-", null, MODELS_PATH, null).getBody().getEntry().getId();
        String modelId = nodesApi.createNode(modelsFolderId,
                        new NodeBodyCreate().name(MODEL_FILE).nodeType("cm:dictionaryModel"), null, null, null, null, null)
                .getBody().getEntry().getId();

        byte[] xml = new ClassPathResource("models/" + MODEL_FILE).getContentAsByteArray();
        nodesApi.updateNodeContent(modelId, xml, null, null, null, null, null);

        // Al activarlo, Alfresco valida el XML: si es invalido, esta llamada falla con 4xx/5xx
        nodesApi.updateNode(modelId, new NodeBodyUpdate().properties(Map.of("cm:modelActive", true)), null, null);
        LOGGER.info("[MODELO] {} activo (id {})", MODEL_FILE, modelId);
    }
}
