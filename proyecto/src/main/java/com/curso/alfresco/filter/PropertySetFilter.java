package com.curso.alfresco.filter;

import java.util.Map;

import org.alfresco.event.sdk.handling.filter.EventFilter;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;

/**
 * Leccion 5: acepta updates en los que la propiedad recibio un valor (nuevo o distinto).
 * resourceBefore solo contiene lo que cambio: si la clave esta presente (aunque sea null), la propiedad cambio.
 * Evita el falso positivo de PropertyAddedFilter, que trata "ausente en before" como "agregada".
 */
public class PropertySetFilter implements EventFilter {

    private final String property;

    private PropertySetFilter(String property) {
        this.property = property;
    }

    public static PropertySetFilter of(String property) {
        return new PropertySetFilter(property);
    }

    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> event) {
        if (!(event.getData().getResource() instanceof NodeResource after)
                || !(event.getData().getResourceBefore() instanceof NodeResource before)) {
            return false;
        }
        Map<String, ?> oldProps = before.getProperties();
        Map<String, ?> newProps = after.getProperties();
        return oldProps != null && oldProps.containsKey(property)
                && newProps != null && newProps.get(property) != null;
    }
}
