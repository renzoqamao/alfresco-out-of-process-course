package com.curso.alfresco.filter;

import java.util.Locale;

import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;

/**
 * Leccion 5: filtro propio. Acepta eventos de nodos cuyo nombre termina en un sufijo (sin distinguir mayusculas).
 */
public class NameEndsWithFilter implements EventFilter {

    private final String suffix;

    private NameEndsWithFilter(String suffix) {
        this.suffix = suffix.toLowerCase(Locale.ROOT);
    }

    public static NameEndsWithFilter of(String suffix) {
        return new NameEndsWithFilter(suffix);
    }

    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> event) {
        // Un evento puede traer otros recursos (asociaciones, permisos): solo nos interesan nodos
        return event.getData().getResource() instanceof NodeResource node
                && node.getName() != null
                && node.getName().toLowerCase(Locale.ROOT).endsWith(suffix);
    }
}
