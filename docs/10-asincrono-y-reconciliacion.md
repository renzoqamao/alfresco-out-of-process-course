# Lección 10 — Procesamiento asíncrono y reconciliación

Hasta ahora cada handler hacía su trabajo **dentro del hilo del listener JMS**. Eso tiene dos límites que
medimos en las lecciones anteriores:

1. **Un handler lento retrasa todos los eventos siguientes**: los handlers se ejecutan uno tras otro, en un único hilo.
2. **Los eventos que llegan mientras la extensión está parada se pierden**: la suscripción al topic no es durable.
   Comprobado: un documento creado con la app detenida nunca llegó a los handlers al volver a arrancarla.

Esta lección resuelve ambos con un ejemplo real: **clasificar como confidenciales los `.txt` que contienen la
palabra `CONFIDENCIAL`**. Para eso hay que descargar el contenido, que es justo el tipo de trabajo lento.

```
                  hilo del listener (rápido)               pool curso-async-* (lento)
evento ─▶ ConfidentialTxtHandler ─ classifyAsync(id) ─▶ ConfidentialClassifier.classify(id)
                                                          ├─ getNode (tamaño)
arranque ─▶ ReconciliationRunner ─ classify(id) ────────▶ ├─ listTagsForNode (¿ya clasificado?)
            (busca lo cambiado desde el checkpoint)       ├─ getNodeContent
                                                          └─ createTagForNode("confidencial")
```

## 10.1 Un servicio con un método `@Async`

Primero activamos `@Async` (`proyecto/.../async/AsyncConfig.java`):

```java
@Configuration
@EnableAsync
public class AsyncConfig {
}
```

El trabajo lo hace un servicio (`proyecto/.../service/ConfidentialClassifier.java`):

```java
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
```

Fíjate en tres decisiones:

- **Dos métodos**: `classifyAsync` para los eventos y `classify` (síncrono) para la reconciliación. Así el mismo
  código sirve en los dos caminos.
- **Límite de tamaño** (`curso.classifier.max-bytes`): nunca leas un binario de 2 GB en memoria.
- **Idempotencia**: si ya tiene la etiqueta, no hace nada. Es lo que permite reprocesar sin miedo (reintentos, reconciliación).

> 🌱 **Spring — cómo funciona `@Async`.** Spring no te entrega el objeto `ConfidentialClassifier` tal cual, sino un
> **proxy** que lo envuelve. Cuando otro bean llama a `classifyAsync()`, el proxy pone la llamada en una cola y
> devuelve el control de inmediato; un hilo del pool la ejecuta después. Consecuencia importante: **una llamada
> desde la misma clase** (`this.classifyAsync()`) **no pasa por el proxy y no es asíncrona**. Por eso el método
> asíncrono vive en un servicio aparte y lo llama el handler.

## 10.2 El handler sólo decide y delega

`proyecto/.../handler/ConfidentialTxtHandler.java`:

```java
@Component
public class ConfidentialTxtHandler implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler {

    private final ConfidentialClassifier classifier;

    public ConfidentialTxtHandler(ConfidentialClassifier classifier) {
        this.classifier = classifier;
    }

    @Override
    public Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.NODE_CREATED, EventType.NODE_UPDATED);
    }

    @Override
    public EventFilter getEventFilter() {
        // Texto plano recién creado, o al que le cambiaron el contenido
        return IsFileFilter.get()
                .and(MimeTypeFilter.of("text/plain"))
                .and(EventTypeFilter.NODE_CREATED.or(ContentChangedFilter.get()));
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        LOGGER.info("[ASYNC] Delegando '{}' al clasificador (hilo {})", node.getName(), Thread.currentThread().getName());
        classifier.classifyAsync(node.getId());
    }
}
```

## 10.3 Configurar el pool de hilos

Spring Boot crea el pool automáticamente; lo ajustamos en `application.properties`:

```properties
spring.task.execution.thread-name-prefix=curso-async-
spring.task.execution.pool.core-size=2
spring.task.execution.pool.max-size=4
spring.task.execution.pool.queue-capacity=100
# Al parar la app, esperar a que terminen las tareas en curso
spring.task.execution.shutdown.await-termination=true
spring.task.execution.shutdown.await-termination-period=30s
```

> ☕ **Java — pool de hilos.** El pool mantiene `core-size` hilos vivos. Las tareas que llegan cuando todos están
> ocupados esperan en una cola de `queue-capacity` posiciones; sólo cuando la cola se llena se crean hilos extra
> hasta `max-size`. Si también se llenan, la tarea se rechaza con una excepción (que sí sube al listener y provoca
> reintentos del evento).

### Resultado real

Subimos `secreto.txt` ("Documento CONFIDENCIAL de prueba") y `publico.txt`:

```
[erContainer#0-1] [ASYNC] Delegando 'secreto.txt' al clasificador (hilo ...DefaultMessageListenerContainer#0-1)
[erContainer#0-1] [ASYNC] Delegando 'publico.txt' al clasificador (hilo ...DefaultMessageListenerContainer#0-1)
[erContainer#0-1] [FACTURA] F-001 (500.0) aprobada automaticamente        ← el listener ya está en otro evento
[  curso-async-2] [CLASIFICADOR] 'publico.txt' no es confidencial (hilo curso-async-2)
[  curso-async-1] [CLASIFICADOR] 'secreto.txt' etiquetado como confidencial (hilo curso-async-1)
```

El listener delega y sigue con el siguiente evento; la clasificación termina después, en paralelo y **sin orden
garantizado** (`publico.txt` terminó antes que `secreto.txt`).

### El precio de la asincronía

| | Síncrono (lecciones 4–9) | Asíncrono |
|---|---|---|
| Un handler lento | bloquea todos los eventos | no bloquea |
| Excepción | ActiveMQ **reintenta** el evento ([7.3](07-reaccionar.md#73-qué-pasa-cuando-un-handler-falla)) | **no hay reintentos**: debes registrar o guardar el fallo tú |
| Orden de procesamiento | el de los eventos | no garantizado |
| App que se para con tareas en cola | — | se pierden si superan `await-termination-period` |

Úsala para trabajo lento e independiente. Si el orden importa (p. ej. "crear" antes que "actualizar" del mismo nodo), quédate en síncrono.

## 10.4 Reconciliación: recuperar lo que pasó con la app parada

La idea:

1. **Guardar un *checkpoint***: la fecha del último evento recibido.
2. **Al arrancar**, buscar con la REST API los nodos modificados desde ese checkpoint y procesarlos.
3. Como la acción es idempotente, procesar de más no es un problema.

### El checkpoint

`proyecto/.../reconcile/Checkpoint.java` lo guarda en un fichero:

```java
@Component
public class Checkpoint {

    private final Path file;
    private final ZonedDateTime atStartup;
    private ZonedDateTime last;

    public Checkpoint(@Value("${curso.reconciliation.checkpoint-file}") Path file) throws IOException {
        this.file = file;
        this.atStartup = Files.exists(file) ? ZonedDateTime.parse(Files.readString(file).trim()) : null;
        this.last = atStartup;
    }

    public Optional<ZonedDateTime> atStartup() {
        return Optional.ofNullable(atStartup);
    }

    public synchronized void record(ZonedDateTime time) {
        if (time == null || (last != null && !time.isAfter(last))) {
            return;
        }
        last = time;
        try {
            Files.writeString(file, time.toString());
        } catch (IOException e) {
            LOGGER.warn("No se pudo guardar el checkpoint en {}", file, e);
        }
    }
}
```

> ⚠️ **Detalle de orden de arranque.** El listener JMS empieza a recibir eventos **antes** de que se ejecuten los
> `CommandLineRunner`. Si la reconciliación leyera el fichero en ese momento, ya podría estar sobrescrito por un
> evento nuevo. Por eso el valor de arranque se lee en el **constructor** (`atStartup`).

Cada evento de nodo avanza el checkpoint con un pequeño flujo de Spring Integration (`CheckpointFlow.java`):

```java
@Bean
public IntegrationFlow recordCheckpoint(Checkpoint checkpoint) {
    return IntegrationFlow.from(EventChannels.MAIN)
            .filter(IntegrationEventFilter.of(EventTypeFilter.NODE), f -> f.discardChannel("nullChannel"))
            .handle(message -> checkpoint.record(((RepoEvent<?>) message.getPayload()).getTime()))
            .get();
}
```

### La búsqueda al arrancar

`proyecto/.../reconcile/ReconciliationRunner.java`:

```java
@Override
public void run(String... args) {
    Optional<ZonedDateTime> since = checkpoint.atStartup();
    if (since.isEmpty()) {
        LOGGER.info("[RECONCILIACION] Sin checkpoint previo: nada que recuperar");
        return;
    }
    // El margen cubre eventos recibidos pero no terminados de procesar antes de la parada
    String from = since.get().minus(margin).toInstant().toString();
    String query = "TYPE:\"cm:content\" AND cm:content.mimetype:\"text/plain\" AND cm:modified:[\"" + from + "\" TO NOW]";

    int total = 0;
    boolean more = true;
    while (more) {
        SearchRequest request = new SearchRequest()
                .query(new RequestQuery().language(RequestQuery.LanguageEnum.AFTS).query(query))
                .paging(new RequestPagination().maxItems(PAGE_SIZE).skipCount(total));
        ResultSetPaging page = searchApi.search(request).getBody();
        page.getList().getEntries().forEach(row -> classifier.classify(row.getEntry().getId()));
        total += page.getList().getEntries().size();
        more = Boolean.TRUE.equals(page.getList().getPagination().isHasMoreItems());
    }
    LOGGER.info("[RECONCILIACION] {} documentos revisados", total);
}
```

```properties
curso.reconciliation.enabled=true
curso.reconciliation.checkpoint-file=./checkpoint.txt
curso.reconciliation.margin=5m
```

¿Por qué el **margen** (`5m`)? El checkpoint avanza al *recibir* el evento, antes de que el clasificador asíncrono
termine. Si la app se cae justo entonces, ese evento no se procesó pero el checkpoint ya lo dejó atrás. Volver
5 minutos atrás lo cubre; la idempotencia hace que repetir los ya procesados no cueste más que unas consultas.

> 🌱 **Spring Boot — `Duration` en propiedades.** `@Value("${curso.reconciliation.margin}") Duration margin`
> convierte automáticamente `5m`, `30s`, `1h` o el formato ISO `PT5M`.

### Prueba real

```
# 1ª ejecución (sin checkpoint)
[RECONCILIACION] Sin checkpoint previo: nada que recuperar

# Se para la app y se sube perdido-secreto.txt ("Informe CONFIDENCIAL subido con la extension parada")
# 2ª ejecución:
[RECONCILIACION] Buscando cambios desde 2026-10-29T17:45:31.317Z
[CLASIFICADOR] 'publico.txt' no es confidencial (hilo main)
[CLASIFICADOR] 'secreto.txt' ya estaba clasificado                         ← idempotencia
[CLASIFICADOR] 'perdido-secreto.txt' etiquetado como confidencial (hilo main)  ← recuperado
[RECONCILIACION] 3 documentos revisados
```

### Límites de esta reconciliación

- Recupera **estado**, no **eventos**: sabe que un nodo cambió, no *qué* cambió. Sirve para acciones del tipo
  "asegurar que X está hecho", no para auditar cada cambio.
- **No ve los borrados** (un nodo borrado no aparece en la búsqueda).
- Depende del **índice de búsqueda** ([nota de la lección 6](06-rest-api.md)): los cambios de los últimos segundos
  antes de arrancar podrían no estar indexados todavía. El margen también ayuda aquí.
- El checkpoint en un **fichero local** sirve para una instancia; en Kubernetes guárdalo en un volumen persistente,
  en una base de datos o incluso como propiedad de un nodo en Alfresco.

Siguiente: [Lección 11 — Modelo de contenido propio](11-modelo-de-contenido.md)
