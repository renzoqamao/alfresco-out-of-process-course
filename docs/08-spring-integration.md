# Lección 8 — Spring Integration

Además de los handlers, el SDK publica cada evento en un canal de
[Spring Integration](https://spring.io/projects/spring-integration): `EventChannels.MAIN`. Sobre ese canal puedes
construir **flujos** declarativos: filtrar, transformar, enrutar, agrupar, enviar a otros sistemas (HTTP, Kafka,
correo, ficheros…) con los adaptadores de Spring Integration.

## 8.1 Activarlo

```properties
alfresco.events.enable-spring-integration=true
```

Ambos modos pueden convivir (en el proyecto del curso están los dos activos).

| Usa **handlers** si… | Usa **Spring Integration** si… |
|----------------------|-------------------------------|
| La lógica es "si pasa X, haz Y" | Necesitas pipelines: transformar, enrutar, agrupar, reintentar |
| Quieres el mínimo de conceptos nuevos | Ya usas Spring Integration o vas a conectar con otros sistemas |
| Necesitas `@Order` entre reacciones | Quieres procesamiento asíncrono/paralelo con canales propios |

## 8.2 Un flujo: registrar los borrados

`proyecto/.../integration/DeletedNodesFlow.java`:

```java
@Configuration
public class DeletedNodesFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedNodesFlow.class);

    @Bean
    public IntegrationFlow logDeletedNodes() {
        return IntegrationFlow.from(EventChannels.MAIN)
                // Sin discardChannel, Spring Integration registra un WARN por cada evento rechazado
                .filter(IntegrationEventFilter.of(EventTypeFilter.NODE_DELETED), f -> f.discardChannel("nullChannel"))
                .handle(message -> {
                    RepoEvent<?> event = (RepoEvent<?>) message.getPayload();
                    NodeResource node = (NodeResource) event.getData().getResource();
                    LOGGER.info("[BORRADO] '{}' ({}) enviado a la papelera", node.getName(), node.getId());
                })
                .get();
    }
}
```

- `IntegrationEventFilter.of(...)` adapta cualquier `EventFilter` del SDK (también los tuyos) a un filtro de Spring Integration.
- `.handle(...)` recibe un `Message<?>`; el evento está en `getPayload()`.

> ⚠️ **Detalle comprobado en ACS 26.2 / Spring Integration 7:** sin `discardChannel`, cada evento rechazado por
> el filtro produce un `WARN ... has been rejected in filter`. Como el topic recibe *muchos* eventos
> (incluidos los de auditoría, ver lección 9), el log se inunda. `nullChannel` es un canal predefinido que descarta en silencio.

> 🌱 **Spring — `@Configuration` y `@Bean`.** Una clase `@Configuration` declara beans con métodos `@Bean`: Spring
> llama al método una vez al arrancar y registra el objeto devuelto. Aquí el bean es un `IntegrationFlow`, que
> Spring Integration conecta automáticamente al canal indicado.

## 8.3 Más ideas

```java
// Enrutar por tipo de evento a canales distintos
IntegrationFlow.from(EventChannels.MAIN)
    .<RepoEvent<?>, String>route(RepoEvent::getType, m -> m
        .channelMapping("org.alfresco.event.node.Created", "creados")
        .channelMapping("org.alfresco.event.node.Deleted", "borrados")
        .defaultOutputChannel("nullChannel"))
    .get();

// Reenviar a un webhook HTTP (requiere spring-integration-http)
IntegrationFlow.from(EventChannels.MAIN)
    .filter(IntegrationEventFilter.of(IsFileFilter.get().and(EventTypeFilter.NODE_CREATED)), f -> f.discardChannel("nullChannel"))
    .transform(Transformers.toJson())   // sin esto el POST sale con Content-Type JSON pero cuerpo VACÍO
    .handle(Http.outboundChannelAdapter("https://mi-sistema/webhook").httpMethod(HttpMethod.POST))
    .get();
```

Para el webhook agrega la dependencia (la versión la pone el *parent* de Spring Boot):

```xml
<dependency>
  <groupId>org.springframework.integration</groupId>
  <artifactId>spring-integration-http</artifactId>
</dependency>
```

> ⚠️ **Comprobado:** sin `.transform(Transformers.toJson())` el receptor recibe `Content-Type: application/json`
> y un cuerpo vacío. Con el transformador recibe el evento completo en JSON (mismo formato que en la lección 9).
> Si el webhook falla (4xx/5xx o caído), la excepción sube al listener y se aplican los reintentos de la [lección 7.3](07-reaccionar.md#73-qué-pasa-cuando-un-handler-falla).

Siguiente: [Lección 9 — El modelo de eventos](09-modelo-de-eventos.md)
