package com.curso.alfresco.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.EventData;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.junit.jupiter.api.Test;

class PropertySetFilterTest {

    private static final PropertySetFilter FILTER = PropertySetFilter.of("cm:title");

    private static Map<String, Serializable> props(String title) {
        Map<String, Serializable> map = new HashMap<>();
        map.put("cm:title", title); // HashMap admite null, igual que el JSON del evento
        return map;
    }

    private static RepoEvent<DataAttributes<Resource>> update(Map<String, Serializable> before, Map<String, Serializable> after) {
        NodeResource b = NodeResource.builder().setProperties(before).build();
        NodeResource a = NodeResource.builder().setId("1").setName("notas.txt").setProperties(after).build();
        return RepoEvent.<DataAttributes<Resource>>builder()
                .setType("org.alfresco.event.node.Updated")
                .setData(EventData.builder().setResource(a).setResourceBefore(b).build())
                .build();
    }

    @Test
    void tituloAgregado() {
        // Asi llega en ACS 26.2: "resourceBefore": {"properties": {"cm:title": null}}
        assertTrue(FILTER.test(update(props(null), props("Nuevo"))));
    }

    @Test
    void tituloModificado() {
        assertTrue(FILTER.test(update(props("Viejo"), props("Nuevo"))));
    }

    @Test
    void otroCambioNoDisparaAunqueElNodoTengaTitulo() {
        // Caso real: se agrega el aspecto cm:titled y resourceBefore no trae propiedades
        assertFalse(FILTER.test(update(null, props("Nuevo"))));
        assertFalse(FILTER.test(update(Map.of("cm:description", "x"), props("Nuevo"))));
    }

    @Test
    void tituloEliminado() {
        assertFalse(FILTER.test(update(props("Viejo"), new HashMap<>())));
    }
}
