package com.curso.alfresco.handler;

import org.alfresco.core.handler.CommentsApi;
import org.alfresco.core.handler.TagsApi;
import org.alfresco.core.model.CommentBody;
import org.alfresco.core.model.TagBody;
import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.event.sdk.handling.filter.IsFileFilter;
import org.alfresco.event.sdk.handling.filter.MimeTypeFilter;
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
 * Lecciones 5 y 7: filtro combinado + reaccion usando la REST API.
 * Cuando se sube un PDF, lo etiqueta y le agrega un comentario.
 */
@Component
public class PdfUploadedHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfUploadedHandler.class);

    private final TagsApi tagsApi;
    private final CommentsApi commentsApi;
    private final String tag;

    // Inyeccion por constructor: Spring pasa los beans del starter REST y el valor de la propiedad
    public PdfUploadedHandler(TagsApi tagsApi, CommentsApi commentsApi, @Value("${curso.tag-pdf}") String tag) {
        this.tagsApi = tagsApi;
        this.commentsApi = commentsApi;
        this.tag = tag;
    }

    @Override
    public EventFilter getEventFilter() {
        return IsFileFilter.get().and(MimeTypeFilter.of("application/pdf"));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        LOGGER.info("[PDF] Nuevo PDF '{}', etiquetando con '{}'", node.getName(), tag);

        tagsApi.createTagForNode(node.getId(), new TagBody().tag(tag), null);
        commentsApi.createComment(node.getId(),
                new CommentBody().content("Procesado por la extension out-of-process del curso"), null);
    }
}
