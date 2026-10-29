# Lección 4 — Tu primer event handler

## 4.1 La idea

Un **event handler** es una clase que el SDK invoca cada vez que llega un evento de cierto tipo. Para crear uno:

1. Implementa una de las interfaces `On...EventHandler`.
2. Anótala con `@Component` para que Spring la registre.
3. Escribe la lógica en `handleEvent(...)`.

## 4.2 Interfaces disponibles

| Interfaz | Tipo de evento | Cuándo ocurre |
|----------|----------------|---------------|
| `OnNodeCreatedEventHandler` | `org.alfresco.event.node.Created` | Se crea un archivo, carpeta, categoría, etc. |
| `OnNodeUpdatedEventHandler` | `org.alfresco.event.node.Updated` | Cambian propiedades, contenido, aspectos, tipo, ubicación (mover) |
| `OnNodeDeletedEventHandler` | `org.alfresco.event.node.Deleted` | Se borra (va a la papelera) |
| `OnChildAssocCreatedEventHandler` / `...Deleted...` | `org.alfresco.event.assoc.child.*` | Asociaciones padre-hijo secundarias |
| `OnPeerAssocCreatedEventHandler` / `...Deleted...` | `org.alfresco.event.assoc.peer.*` | Asociaciones entre pares |
| `OnPermissionUpdatedEventHandler` | `org.alfresco.event.permission.Updated` | Cambian permisos |

Todas están en `org.alfresco.event.sdk.handling.handler`.

## 4.3 El código

`proyecto/src/main/java/com/curso/alfresco/handler/NodeCreatedLoggerHandler.java`:

```java
package com.curso.alfresco.handler;

import org.alfresco.event.sdk.handling.handler.OnNodeCreatedEventHandler;
import org.alfresco.repo.event.v1.model.DataAttributes;
import org.alfresco.repo.event.v1.model.NodeResource;
import org.alfresco.repo.event.v1.model.RepoEvent;
import org.alfresco.repo.event.v1.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NodeCreatedLoggerHandler implements OnNodeCreatedEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeCreatedLoggerHandler.class);

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource node = (NodeResource) event.getData().getResource();
        // Desde ACS 25.x/26.x el evento trae la ruta legible en extensionAttributes.path
        Object path = event.getExtensionAttributes() == null ? null : event.getExtensionAttributes().getExtension("path");
        LOGGER.info("[CREADO] nombre='{}' tipo={} id={} usuario={} ruta={}",
                node.getName(), node.getNodeType(), node.getId(), node.getCreatedByUser().getId(), path);
    }
}
```

> ☕ **Java — interfaces con métodos `default`.** `OnNodeCreatedEventHandler` extiende `EventHandler`, que tiene
> tres métodos: `getHandledEventTypes()`, `getEventFilter()` y `handleEvent()`. Los dos primeros ya vienen
> implementados (`default`) en la interfaz: el tipo de evento lo fija la propia interfaz y el filtro por defecto
> acepta todo. Por eso sólo estás obligado a escribir `handleEvent`.

> ☕ **Java — genéricos y *cast*.** `RepoEvent<DataAttributes<Resource>>` se lee "un evento cuyos datos contienen un
> `Resource`". `Resource` es genérico (puede ser un nodo, una asociación, una entrada de auditoría…), por eso
> hacemos `(NodeResource)` para acceder a `getName()`, `getNodeType()`, etc. En un handler de nodos el cast es seguro.

> 🌱 **Spring Boot — `@Component` y la lista de handlers.** Al arrancar, Spring crea una instancia de cada clase
> `@Component`. La autoconfiguración del SDK pide un `List<EventHandler>`: Spring le entrega **todas** las
> instancias que implementan esa interfaz, y el SDK las registra en su `EventHandlingRegistry`. No tienes que
> registrar nada a mano.

## 4.4 Probarlo

```bash
cd proyecto
mvn spring-boot:run
```

Crea una carpeta desde Alfresco Digital Workspace / Share o con curl:

```bash
curl -u admin:admin -H "Content-Type: application/json" \
  -d '{"name":"curso-oop-prueba","nodeType":"cm:folder"}' \
  "http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/-shared-/children"
```

En la consola de la extensión verás (salida real contra ACS 26.2):

```
[CREADO] nombre='curso-oop-prueba' tipo=cm:folder id=2e424090-... usuario=admin ruta=/Company Home/Shared/curso-oop-prueba
```

Ahora sube un archivo a esa carpeta. Fíjate en que **una sola acción del usuario puede generar varios eventos**:
al etiquetar un documento, por ejemplo, verás también un `[CREADO]` para un nodo de tipo `cm:category` (la
etiqueta). Esto es importante: sin filtros, tu handler reacciona a *todo*. De eso trata la lección siguiente.

## 4.5 Varios handlers para el mismo evento

Puedes tener todos los handlers que quieras para el mismo tipo. Se ejecutan **secuencialmente, en el mismo hilo**.
Para fijar el orden usa `@Order` (menor número = antes; los handlers sin `@Order` van al final):

```java
@Component
@Order(10)
public class PrimeroHandler implements OnNodeCreatedEventHandler { ... }
```

Y si una clase debe atender **varios tipos** de evento, implementa varias interfaces y resuelve el conflicto de
métodos `default` declarando explícitamente los tipos:

```java
@Component
public class CreadoOActualizadoHandler implements OnNodeCreatedEventHandler, OnNodeUpdatedEventHandler {

    @Override
    public Set<EventType> getHandledEventTypes() {
        return Set.of(EventType.NODE_CREATED, EventType.NODE_UPDATED);
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) { ... }
}
```

> ⚠️ Si un handler lanza una excepción, **el evento entero se reintenta** (hasta 7 veces) y los handlers que
> van después nunca lo reciben. Lo vemos con detalle en la [lección 7.3](07-reaccionar.md#73-qué-pasa-cuando-un-handler-falla).

Siguiente: [Lección 5 — Filtros de eventos](05-filtros.md)
