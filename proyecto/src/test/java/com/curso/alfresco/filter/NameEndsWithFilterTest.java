package com.curso.alfresco.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.EventData;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.junit.jupiter.api.Test;

class NameEndsWithFilterTest {

    private static RepoEvent<DataAttributes<Resource>> eventFor(String name) {
        NodeResource node = NodeResource.builder().setId("1").setName(name).setIsFile(true).build();
        return RepoEvent.<DataAttributes<Resource>>builder()
                .setType("org.alfresco.event.node.Created")
                .setData(EventData.builder().setResource(node).build())
                .build();
    }

    @Test
    void aceptaElSufijoSinImportarMayusculas() {
        assertTrue(NameEndsWithFilter.of(".txt").test(eventFor("NOTAS.TXT")));
    }

    @Test
    void rechazaOtrosNombres() {
        assertFalse(NameEndsWithFilter.of(".txt").test(eventFor("informe.pdf")));
        assertFalse(NameEndsWithFilter.of(".txt").test(eventFor(null)));
    }
}
