package com.curso.alfresco.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import feign.FeignException;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.handler.TagsApi;
import org.alfresco.core.model.Node;
import org.alfresco.core.model.TagBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Leccion 11: trabajo lento (descargar y leer el contenido) fuera del hilo del listener.
 * Etiqueta como "confidencial" los textos que contienen la palabra CONFIDENCIAL. Es idempotente.
 */
@Service
public class ConfidentialClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfidentialClassifier.class);
    private static final String TAG = "confidencial";
    private static final String WORD = "CONFIDENCIAL";

    private final NodesApi nodesApi;
    private final TagsApi tagsApi;
    private final long maxBytes;

    public ConfidentialClassifier(NodesApi nodesApi, TagsApi tagsApi, @Value("${curso.classifier.max-bytes}") long maxBytes) {
        this.nodesApi = nodesApi;
        this.tagsApi = tagsApi;
        this.maxBytes = maxBytes;
    }

    /** La llamada vuelve de inmediato; classify() se ejecuta en un hilo del pool. */
    @Async
    public void classifyAsync(String nodeId) {
        classify(nodeId);
    }

    public void classify(String nodeId) {
        try {
            Node node = nodesApi.getNode(nodeId, null, null, null).getBody().getEntry();
            if (node.getContent() == null || node.getContent().getSizeInBytes() > maxBytes) {
                LOGGER.info("[CLASIFICADOR] '{}' omitido: sin contenido o mayor a {} bytes", node.getName(), maxBytes);
                return;
            }
            if (hasTag(nodeId)) {
                LOGGER.info("[CLASIFICADOR] '{}' ya estaba clasificado", node.getName());
                return;
            }
            String text;
            try (InputStream in = nodesApi.getNodeContent(nodeId, true, null, null).getBody().getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (text.contains(WORD)) {
                tagsApi.createTagForNode(nodeId, new TagBody().tag(TAG), null);
                LOGGER.info("[CLASIFICADOR] '{}' etiquetado como {} (hilo {})", node.getName(), TAG, Thread.currentThread().getName());
            } else {
                LOGGER.info("[CLASIFICADOR] '{}' no es confidencial (hilo {})", node.getName(), Thread.currentThread().getName());
            }
        } catch (FeignException.NotFound e) {
            LOGGER.info("[CLASIFICADOR] El nodo {} ya no existe", nodeId);
        } catch (FeignException | IOException e) {
            // En un hilo @Async no hay reintento del broker: el error se registra y el evento queda sin procesar
            LOGGER.error("[CLASIFICADOR] No se pudo clasificar {}", nodeId, e);
        }
    }

    private boolean hasTag(String nodeId) {
        return tagsApi.listTagsForNode(nodeId, 0, 100, null).getBody().getList().getEntries().stream()
                .anyMatch(t -> TAG.equals(t.getEntry().getTag()));
    }
}
