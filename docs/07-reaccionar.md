# Lección 7 — Reaccionar a eventos con la REST API

Juntamos lo anterior: un evento llega, el filtro lo selecciona y el handler actúa vía REST.

## 7.1 Etiquetar y comentar cada PDF subido

`proyecto/.../handler/PdfUploadedHandler.java`:

```java
@Component
public class PdfUploadedHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfUploadedHandler.class);

    private final TagsApi tagsApi;
    private final CommentsApi commentsApi;
    private final String tag;

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
```

> 🌱 **Spring Boot — `@Value`.** Inyecta el valor de una propiedad (`curso.tag-pdf=pdf` en
> `application.properties`). Así el comportamiento se cambia por configuración, sin recompilar.

Prueba (sube un PDF a la carpeta creada en la lección 4):

```bash
curl -u admin:admin -F filedata=@factura.pdf \
  "http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/<ID_CARPETA>/children"
```

Log real:

```
[CREADO] nombre='factura.pdf' tipo=cm:content ...
[PDF] Nuevo PDF 'factura.pdf', etiquetando con 'pdf'
[CREADO] nombre='pdf' tipo=cm:category ...        ← la etiqueta es un nodo nuevo
```

Y en el repositorio:

```bash
curl -u admin:admin ".../nodes/<ID_PDF>/tags"      # {"tag":"pdf", ...}
curl -u admin:admin ".../nodes/<ID_PDF>/comments"  # "Procesado por la extension out-of-process del curso"
```

## 7.2 Modificar el propio nodo: cadenas de eventos

`TxtAutoTitleHandler` pone un título a cada `.txt` nuevo:

```java
@Component
public class TxtAutoTitleHandler implements OnNodeCreatedEventHandler {

    private final NodesApi nodesApi;

    public TxtAutoTitleHandler(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public EventFilter getEventFilter() {
        return IsFileFilter.get().and(NameEndsWithFilter.of(".txt"));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        String title = "Texto: " + node.getName();
        // Escribir cm:title agrega automaticamente el aspecto cm:titled
        nodesApi.updateNode(node.getId(), new NodeBodyUpdate().properties(Map.of("cm:title", title)), null, null);
    }
}
```

Esa actualización **genera nuevos eventos** `node.Updated`, que a su vez llegan a `TitleChangedHandler`:

```
[CREADO] nombre='otra.txt' ...
[TXT] Asignando cm:title='Texto: otra.txt' a 09dc6845-...
[TITULO] 'otra.txt': 'null' -> 'Texto: otra.txt'
```

### ⚠️ Cuidado con los bucles infinitos

Si un handler de `node.Updated` modifica el mismo nodo **sin un filtro que lo excluya después**, cada
actualización dispara otra actualización… para siempre. Reglas:

1. Filtra por el **cambio concreto** que te interesa (`PropertySetFilter`, `AspectAddedFilter`…), nunca "cualquier update".
2. Haz que la acción sea **idempotente**: comprueba si ya está hecha antes de hacerla (p. ej. si ya tiene el título).
3. Si hace falta, marca los nodos procesados (un aspecto o propiedad propia) y exclúyelos con `NodeAspectFilter.of(...).negate()`.
4. Opcional: usa una cuenta de servicio y descarta eventos cuyo `modifiedByUser` sea esa cuenta.

## 7.3 Qué pasa cuando un handler falla

Las llamadas REST pueden fallar (red, permisos, nodo borrado). Para saber qué hacer hay que conocer qué pasa
con una excepción **no capturada**. Lo medimos con tres handlers para `node.Created`: uno con `@Order(2)`, otro con
`@Order(5)` que siempre lanza `IllegalStateException`, y los del proyecto (sin `@Order`):

```
12:42:36.109  BoomHandler va a lanzar excepcion
              Execution of JMS message listener failed, and no ErrorHandler has been set.
              ... Dispatcher has no subscribers for channel '...alfresco.events.error.channel'
12:42:37.116  BoomHandler va a lanzar excepcion      ← reintento 1
12:42:38.136  BoomHandler va a lanzar excepcion      ← reintento 2
...
12:42:42.177  BoomHandler va a lanzar excepcion      ← reintento 6, y el evento se descarta
```

Lo que ocurre (comprobado en ACS 26.2 / SDK 7.3.3):

1. La excepción sube hasta el listener JMS, que **no confirma el mensaje**: el cliente de ActiveMQ lo
   **vuelve a entregar** cada ~1 s, hasta **6 reintentos** (7 ejecuciones en total, la política por defecto de ActiveMQ).
2. **Cada reintento ejecuta de nuevo todos los handlers del evento desde el principio**: el de `@Order(2)` se ejecutó 7 veces.
3. Los handlers **posteriores** al que falla **nunca** reciben el evento.
4. Tras el último intento el evento **se pierde**: no va a la DLQ (`ActiveMQ.DLQ`), porque los eventos de Alfresco son mensajes no persistentes.
5. Mientras tanto, el listener está ocupado: **los eventos siguientes esperan** esos ~7 segundos.

Conclusiones:

- Un error **muy breve** (un timeout puntual) se recupera solo gracias a los reintentos… siempre que los
  handlers sean **idempotentes**, porque los que ya terminaron se repiten. Un corte más largo que ~6 s
  (p. ej. Alfresco reiniciándose) agota los reintentos: para eso está la reconciliación de la [lección 10](10-asincrono-y-reconciliacion.md).
- Un error permanente (un bug, un dato inválido) **bloquea a los demás handlers** de ese evento y acaba perdiéndolo.

Por eso, en cada handler captura lo que **sabes** que no se arregla reintentando y deja subir el resto:

```java
try {
    tagsApi.createTagForNode(id, new TagBody().tag(tag), null);
} catch (FeignException.NotFound e) {
    // permanente: el nodo se borró antes de procesarlo; reintentar no sirve
    LOGGER.info("El nodo {} se borró antes de procesarlo", id);
}
// Otros FeignException (5xx, timeout) suben: ActiveMQ reintenta el evento.
```

> 💡 ¿Y si el handler delega en otro hilo (`@Async`)? Entonces la excepción ya no llega al listener y **no hay
> reintentos**: lo vemos en la [lección 10](10-asincrono-y-reconciliacion.md).

Siguiente: [Lección 8 — Spring Integration](08-spring-integration.md)
