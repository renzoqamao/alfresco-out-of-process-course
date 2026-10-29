# Lección 13 — Ejercicios

Intenta cada ejercicio antes de abrir la solución. Todos se resuelven agregando clases al proyecto del curso.

---

## Ejercicio 1 — Estructura automática de carpetas

Cuando alguien cree una carpeta llamada `Facturas`, la extensión debe crear dentro las subcarpetas
`Pendientes` y `Pagadas`.

Pistas: `IsFolderFilter`, una lambda que compare el nombre, `NodesApi.createNode` con `NodeBodyCreate`.

<details>
<summary>Solución</summary>

```java
@Component
public class FacturasFolderHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FacturasFolderHandler.class);

    private final NodesApi nodesApi;

    public FacturasFolderHandler(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public EventFilter getEventFilter() {
        return IsFolderFilter.get()
                .and(e -> "Facturas".equalsIgnoreCase(((NodeResource) e.getData().getResource()).getName()));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        String parentId = ((NodeResource) event.getData().getResource()).getId();
        for (String name : List.of("Pendientes", "Pagadas")) {
            try {
                nodesApi.createNode(parentId, new NodeBodyCreate().name(name).nodeType("cm:folder"),
                        null, null, null, null, null);
            } catch (FeignException.Conflict e) {
                LOGGER.info("'{}' ya existía", name);   // idempotente: si ya está, no es un error
            }
        }
    }
}
```

¿Por qué no se dispara de nuevo al crear `Pendientes`? Porque el filtro exige que el nombre sea `Facturas`.
</details>

---

## Ejercicio 2 — Filtro "creado dentro de la carpeta X"

Crea `InFolderFilter.of(folderId)` que acepte nodos cuyo **padre directo** sea `folderId`, y escribe su test.

Pista: lección 9.2, `primaryHierarchy`.

<details>
<summary>Solución</summary>

```java
public class InFolderFilter implements EventFilter {

    private final String folderId;

    private InFolderFilter(String folderId) {
        this.folderId = folderId;
    }

    public static InFolderFilter of(String folderId) {
        return new InFolderFilter(folderId);
    }

    @Override
    public boolean test(RepoEvent<DataAttributes<Resource>> event) {
        return event.getData().getResource() instanceof NodeResource node
                && node.getPrimaryHierarchy() != null
                && !node.getPrimaryHierarchy().isEmpty()
                && folderId.equals(node.getPrimaryHierarchy().get(0));
    }
}
```

```java
class InFolderFilterTest {

    private static RepoEvent<DataAttributes<Resource>> eventIn(List<String> hierarchy) {
        NodeResource node = NodeResource.builder().setId("n").setPrimaryHierarchy(hierarchy).build();
        return RepoEvent.<DataAttributes<Resource>>builder()
                .setData(EventData.builder().setResource(node).build())
                .build();
    }

    @Test
    void soloElPadreDirecto() {
        assertTrue(InFolderFilter.of("padre").test(eventIn(List.of("padre", "abuelo"))));
        assertFalse(InFolderFilter.of("abuelo").test(eventIn(List.of("padre", "abuelo"))));
        assertFalse(InFolderFilter.of("padre").test(eventIn(List.of())));
    }
}
```

Variante: para "en cualquier nivel bajo X" usa `getPrimaryHierarchy().contains(folderId)`.
</details>

---

## Ejercicio 3 — Detectar movimientos

Registra en el log cuando un documento se mueve de carpeta, indicando el id de la carpeta de origen y la de destino.

Pistas: `OnNodeUpdatedEventHandler`, `NodeMovedFilter`, `primaryHierarchy` en `resource` y `resourceBefore`.

<details>
<summary>Solución</summary>

```java
@Component
public class NodeMovedHandler implements OnNodeUpdatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeMovedHandler.class);

    @Override
    public EventFilter getEventFilter() {
        return NodeMovedFilter.get();
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource after = (NodeResource) event.getData().getResource();
        NodeResource before = (NodeResource) event.getData().getResourceBefore();
        LOGGER.info("[MOVIDO] '{}': {} -> {}", after.getName(),
                before.getPrimaryHierarchy().get(0), after.getPrimaryHierarchy().get(0));
    }
}
```

Pruébalo con `POST .../nodes/{id}/move` y body `{"targetParentId":"<carpeta destino>"}`.
</details>

---

## Ejercicio 4 — Enrutar con Spring Integration

Crea un flujo que envíe los eventos `node.Created` de archivos a un canal `archivosNuevos` y, en otro flujo que
lea de ese canal, registre el mimetype.

<details>
<summary>Solución</summary>

```java
@Configuration
public class ArchivosNuevosFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchivosNuevosFlow.class);

    @Bean
    public IntegrationFlow seleccionarArchivosNuevos() {
        return IntegrationFlow.from(EventChannels.MAIN)
                .filter(IntegrationEventFilter.of(EventTypeFilter.NODE_CREATED.and(IsFileFilter.get())),
                        f -> f.discardChannel("nullChannel"))
                .channel("archivosNuevos")
                .get();
    }

    @Bean
    public IntegrationFlow registrarMimetype() {
        return IntegrationFlow.from("archivosNuevos")
                .handle(m -> {
                    NodeResource node = (NodeResource) ((RepoEvent<?>) m.getPayload()).getData().getResource();
                    LOGGER.info("[ARCHIVO] {} es {}", node.getName(), node.getContent().getMimeType());
                })
                .get();
    }
}
```
</details>

---

## Ejercicio 5 — Reto: archivar facturas pagadas

Usando el modelo de la [lección 11](11-modelo-de-contenido.md): cuando una factura pase a `PAGADA`, muévela a una
subcarpeta `Pagadas` de la carpeta donde está (créala si no existe).

Piensa en:

- ¿Qué filtro evita reaccionar a otros cambios de la factura? (pista: el mismo que usa `FacturaPagadaHandler`)
- ¿Qué pasa si la factura **ya está** dentro de `Pagadas`? ¿Y si dos facturas se pagan a la vez y ambas intentan crear la carpeta?
- Mover un nodo también genera un `node.Updated`: ¿puede provocar un bucle?

<details>
<summary>Solución</summary>

```java
@Component
public class ArchivarFacturaPagadaHandler implements OnNodeUpdatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchivarFacturaPagadaHandler.class);
    private static final String ARCHIVO = "Pagadas";

    private final NodesApi nodesApi;

    public ArchivarFacturaPagadaHandler(NodesApi nodesApi) {
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
        String parentId = node.getPrimaryHierarchy().get(0);
        String parentName = nodesApi.getNode(parentId, null, null, null).getBody().getEntry().getName();
        if (ARCHIVO.equals(parentName)) {
            return; // ya archivada
        }
        String archivoId = carpetaArchivo(parentId);
        nodesApi.moveNode(node.getId(), new NodeBodyMove().targetParentId(archivoId), null, null);
        LOGGER.info("[ARCHIVO] {} movida a {}", node.getName(), ARCHIVO);
    }

    private String carpetaArchivo(String parentId) {
        try {
            return nodesApi.getNode(parentId, null, ARCHIVO, null).getBody().getEntry().getId();
        } catch (FeignException.NotFound e) {
            try {
                return nodesApi.createNode(parentId, new NodeBodyCreate().name(ARCHIVO).nodeType("cm:folder"),
                        null, null, null, null, null).getBody().getEntry().getId();
            } catch (FeignException.Conflict c) {
                // otra factura la creó en paralelo
                return nodesApi.getNode(parentId, null, ARCHIVO, null).getBody().getEntry().getId();
            }
        }
    }
}
```

No hay bucle: el `node.Updated` del movimiento no trae `fac:estado` en `resourceBefore`, así que `PropertySetFilter` lo descarta.
</details>

---

## Ejercicio 6 — Reto: desclasificar

Amplía `ConfidentialClassifier` ([lección 10](10-asincrono-y-reconciliacion.md)) para que, si el contenido de un
documento etiquetado **deja de** contener `CONFIDENCIAL`, se le quite la etiqueta. Pista: hoy `classify` termina
en cuanto ve la etiqueta; necesitarás `TagsApi.deleteTagFromNode` y el id de la etiqueta que devuelve `listTagsForNode`.
