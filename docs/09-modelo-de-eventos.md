# Lección 9 — El modelo de eventos

Conocer el JSON que envía el repositorio es lo que separa una extensión que "parece funcionar" de una fiable.
Los ejemplos de esta lección se **capturaron de ACS 26.2** mientras se probaba el curso; están completos en
[`eventos-ejemplo/`](eventos-ejemplo).

## 9.1 Estructura (CloudEvents)

Los eventos siguen la especificación [CloudEvents 1.0](https://cloudevents.io):

```jsonc
{
  "specversion": "1.0",
  "type": "org.alfresco.event.node.Created",       // qué pasó
  "id": "3362cff9-53e5-44ea-8d80-63dbccf45acc",     // id único del evento
  "source": "/b193cb9c-c28f-4e5f-93cb-9cc28fde5fb6", // id del repositorio
  "time": "2026-10-29T17:08:50.277Z",
  "dataschema": "https://api.alfresco.com/schema/event/repo/v1/nodeCreated",
  "datacontenttype": "application/json",
  "data": {
    "eventGroupId": "1aa5cdd3-...",                 // eventos de la misma transacción comparten grupo
    "resource": { ... },                            // estado ACTUAL del recurso
    "resourceBefore": { ... },                      // solo en Updated: campos que cambiaron, con su valor anterior
    "resourceReaderAuthorities": ["GROUP_EVERYONE"],// quién puede leer el nodo
    "resourceDeniedAuthorities": []
  },
  "extensionAttributes": {
    "path": "/Company Home/Shared/curso-oop-prueba/factura.pdf",
    "clientId": null
  }
}
```

En Java: `RepoEvent` → `getData()` (`EventData`) → `getResource()` / `getResourceBefore()`; y
`getExtensionAttributes().getExtension("path")`.

## 9.2 El recurso `NodeResource`

```jsonc
"resource": {
  "@type": "NodeResource",
  "id": "f0d6a9b4-725e-4539-96a9-b4725e2539a4",
  "primaryHierarchy": [               // ids de los ancestros: del PADRE INMEDIATO hacia la raíz
    "2e424090-...",                   //   curso-oop-prueba
    "4c1ae56c-...",                   //   Shared
    "1560dd2c-...",                   //   Company Home
    "3e54d481-..."                    //   raíz del store
  ],
  "name": "factura.pdf",
  "nodeType": "cm:content",
  "createdByUser":  { "id": "admin", "displayName": "Administrator" },
  "createdAt": "2026-10-29T17:08:49.682Z",
  "modifiedByUser": { "id": "admin", "displayName": "Administrator" },
  "modifiedAt": "2026-10-29T17:08:49.682Z",
  "content": { "mimeType": "application/pdf", "sizeInBytes": 44, "encoding": "UTF-8" },
  "properties": { "cm:versionLabel": "1.0", "cm:autoVersion": true, ... },
  "aspectNames": ["cm:versionable", "cm:auditable"],
  "primaryAssocQName": "cm:factura.pdf",
  "secondaryParents": [],
  "isFile": true,
  "isFolder": false
}
```

Observaciones prácticas:

- `primaryHierarchy.get(0)` es el **padre directo**: útil para filtrar "nodos creados en la carpeta X" por id.
- `extensionAttributes.path` da la **ruta legible**: más cómodo para filtros por ruta, pero cambia si se renombra una carpeta. Para reglas estables, usa ids. **No todos los eventos lo traen** (ver 9.2.1).
- `properties` no trae `cm:name`, `cm:created`, etc.: esos valores están como campos propios (`name`, `createdAt`…).
- **No trae el binario.** Para leer el contenido, usa `NodesApi.getNodeContent(...)`.

### 9.2.1 ¿Qué eventos traen `extensionAttributes`?

Comprobado en ACS 26.2 generando cada tipo de evento:

| Evento | `path` | `toPath` | Observación |
|--------|:------:|:--------:|-------------|
| `node.Created` | ✅ | — | ruta del nodo nuevo |
| `node.Updated` (propiedades, contenido) | ✅ | — | ruta actual |
| `node.Updated` (**mover**) | ✅ ruta **anterior** | ✅ ruta **nueva** | |
| `node.Updated` (**renombrar**) | ✅ ruta anterior | ✅ ruta nueva | los hijos de una carpeta renombrada **no** generan eventos: su ruta cambia sin aviso |
| `node.Updated` (agregar padre **secundario**) | ⚠️ `"/b.txt"` (sólo el nombre) | ✅ ruta por el padre secundario | `path` no es una ruta útil |
| `node.Updated` (quitar padre secundario) | ✅ ruta por el padre secundario | ✅ ruta primaria | |
| `node.Deleted` | ✅ | — | ruta que tenía antes de borrarse |
| `permission.Updated` | ✅ | — | ⚠️ en uno emitido al renombrar una carpeta, la ruta traía un UUID en lugar del nombre |
| `assoc.child.Created` / `Deleted` | ❌ | ❌ | `extensionAttributes` ausente |
| `assoc.peer.Created` / `Deleted` | ❌ | ❌ | `extensionAttributes` ausente |
| `audit.entry.Created` | ❌ | ❌ | `extensionAttributes` ausente |

Conclusiones prácticas:

- Comprueba siempre `event.getExtensionAttributes() != null` antes de usarlo (el handler de la lección 4 ya lo hace).
- En un `node.Updated`, si existe `toPath` hubo un cambio de ubicación o de nombre: `path` es el **antes** y `toPath` el **después**.
  Desde Java: `getExtension("toPath")`.
- Úsalo para **logs y filtros aproximados**. Para decisiones de negocio usa los ids (`primaryHierarchy`, `secondaryParents`).
- Mover o renombrar también genera `permission.Updated` (cambian los permisos heredados): tenlo en cuenta si reaccionas a permisos.

## 9.3 `resourceBefore` en un `node.Updated`

```jsonc
"resourceBefore": {
  "@type": "NodeResource",
  "modifiedAt": "2026-10-29T17:08:48.89Z",
  "properties": { "cm:title": null },          // la clave existe => la propiedad cambió; null => antes no tenía valor
  "localizedProperties": { "cm:title": { "en": null } },
  "aspectNames": ["cm:versionable", "cm:auditable"]  // aspectos ANTES del cambio
}
```

`resourceBefore` es un **diff**, no el nodo anterior completo. Esto explica la trampa de la
[lección 5.4](05-filtros.md#54-trampa-real-resourcebefore-solo-trae-lo-que-cambió).

## 9.4 Tipos de evento

| `type` | `EventType` (Java) | Recurso |
|--------|--------------------|---------|
| `org.alfresco.event.node.Created` | `NODE_CREATED` | `NodeResource` |
| `org.alfresco.event.node.Updated` | `NODE_UPDATED` | `NodeResource` (+ `resourceBefore`) |
| `org.alfresco.event.node.Deleted` | `NODE_DELETED` | `NodeResource` |
| `org.alfresco.event.assoc.child.Created` / `.Deleted` | `CHILD_ASSOC_*` | `ChildAssociationResource` |
| `org.alfresco.event.assoc.peer.Created` / `.Deleted` | `PEER_ASSOC_*` | `PeerAssociationResource` |
| `org.alfresco.event.permission.Updated` | `PERMISSION_UPDATED` | `NodeResource` |
| `org.alfresco.event.audit.entry.Created` | — | `AuditEntryResource` |

### Eventos de auditoría (nuevos en 25.x/26.x)

En la instancia de prueba, **la mayoría de los mensajes del topic eran de auditoría** (logins, accesos…):

```json
{
  "type": "org.alfresco.event.audit.entry.Created",
  "data": {
    "resource": {
      "@type": "AuditEntryResource",
      "entryType": "audit-suite-auth",
      "username": "admin",
      "entryDBId": 3277,
      "entryData": { "login": { "user": "admin" } }
    }
  }
}
```

No hay interfaz `On...Handler` para ellos, así que los handlers del SDK los ignoran; en Spring Integration sí
llegan al canal `MAIN`, por eso **siempre filtra por tipo** en tus flujos.

## 9.5 Ver los eventos en crudo

Para depurar, sube el nivel de log del flujo interno del SDK, que imprime cada `RepoEvent` recibido:

```properties
logging.level.org.springframework.integration.handler.LoggingHandler=DEBUG
```

Salida real (una línea por evento, incluidos los de auditoría):

```
DEBUG ... o.s.integration.handler.LoggingHandler : GenericMessage [payload=RepoEvent [specversion=1.0, type=org.alfresco.event.audit.entry.Created, id=6dc5b189-...
```

Para el JSON tal cual, cualquier cliente STOMP/JMS sirve: suscríbete al topic `alfresco.repo.event2`
(STOMP en el puerto 61613, mismas credenciales del broker).

## 9.6 Configurar qué emite el repositorio

En `alfresco-global.properties` (o `-D` en `JAVA_OPTS`) del repositorio. Valores por defecto en ACS 26.2
(leídos del `repository.properties` de la instancia):

```properties
repo.event2.enabled=true
repo.event2.topic.endpoint=amqp:topic:alfresco.repo.event2
# Tipos, aspectos y asociaciones que NO generan eventos
repo.event2.filter.nodeTypes=sys:*, fm:*, cm:thumbnail, cm:failedThumbnail, cm:rating, rma:rmsite include_subtypes, usr:user
repo.event2.filter.nodeAspects=sys:*
repo.event2.filter.childAssocTypes=rn:rendition
# Usuarios cuyas acciones no generan eventos (vacío por defecto); p. ej. tu cuenta de servicio
repo.event2.filter.users=
# Propiedades que se omiten del payload
repo.event2.filter.nodeProperties=
```

> ⚠️ Estas propiedades son **globales**: afectan a **todos** los consumidores del topic, no sólo a tu extensión.

### Prueba real: `repo.event2.filter.users`

Parece la forma ideal de evitar bucles: que las acciones de la cuenta de servicio de la extensión no generen eventos.
Lo probamos en ACS 26.2 + Elasticsearch con `repo.event2.filter.users=svc-curso` (y reinicio del repositorio):

| Acción | ¿Evento? |
|--------|:--------:|
| `svc-curso` crea `filtrado-svc.txt` | ❌ |
| `admin` crea `visible-admin.txt` | ✅ |
| `admin` edita el título de `filtrado-svc.txt` | ✅ (el filtro mira quién hace la acción, no de quién es el nodo) |
| `svc-curso` edita la descripción de `filtrado-svc.txt` | ❌ |

El filtro funciona… pero en 26.2 **la indexación de Elasticsearch también se alimenta de estos eventos**:

| Búsqueda | Resultado |
|----------|-----------|
| Descripción puesta por `svc-curso` | ❌ no se encuentra |
| Texto completo del fichero subido por `svc-curso` | ❌ no se encuentra |
| Nombre de `filtrado-svc.txt` | ✅ sólo porque después lo editó `admin` y ese evento llevó el estado completo del nodo |
| Todo lo hecho por `admin` | ✅ |

**Conclusión:** con Elasticsearch, filtrar un usuario hace que **sus cambios no se indexen**. Úsalo sólo si esa cuenta
nunca modifica nada que deba poder buscarse; en general es más seguro evitar los bucles con filtros en la extensión
(lecciones 5 y 7). Con Solr no debería ocurrir, porque indexa consultando las transacciones del repositorio (*tracking*) en lugar
de leer eventos (no probado: la instancia del curso usa Elasticsearch).

Siguiente: [Lección 10 — Procesamiento asíncrono y reconciliación](10-asincrono-y-reconciliacion.md)
