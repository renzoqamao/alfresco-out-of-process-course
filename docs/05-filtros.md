# Lección 5 — Filtros de eventos

Un handler sin filtro recibe **todos** los eventos de su tipo. Con `getEventFilter()` decides cuáles procesar.

## 5.1 Filtros que trae el SDK

Paquete `org.alfresco.event.sdk.handling.filter`:

| Filtro | Acepta el evento si… |
|--------|----------------------|
| `IsFileFilter.get()` / `IsFolderFilter.get()` | el nodo es archivo / carpeta |
| `NodeTypeFilter.of("cm:content")` | el nodo es de ese tipo |
| `MimeTypeFilter.of("application/pdf")` | el contenido tiene ese mimetype |
| `NodeAspectFilter.of("cm:titled")` | el nodo tiene ese aspecto |
| `AspectAddedFilter.of(...)` / `AspectRemovedFilter.of(...)` | se agregó / quitó el aspecto en este cambio |
| `PropertyChangedFilter.of("cm:title")` | la propiedad existía antes y cambió de valor |
| `PropertyAddedFilter.of(...)` / `PropertyRemovedFilter.of(...)` | ⚠️ ver 5.4 |
| `PropertyValueFilter.of(prop, valor)` | la propiedad tiene ese valor |
| `PropertyCurrentValueFilter` / `PropertyPreviousValueFilter` | valor actual / anterior |
| `ContentAddedFilter.get()` / `ContentChangedFilter.get()` | se agregó / cambió el contenido binario |
| `NodeMovedFilter.get()` | el nodo cambió de carpeta |
| `NodeTypeChangedFilter.get()` | se especializó el tipo |
| `AssocTypeFilter.of(...)` | la asociación es de ese tipo |
| `EventTypeFilter.NODE_CREATED`, `NODE_UPDATED`… | el evento es de ese tipo (útil en Spring Integration) |

## 5.2 Combinar filtros

`EventFilter` tiene `and`, `or` y `negate`:

```java
@Override
public EventFilter getEventFilter() {
    return IsFileFilter.get().and(MimeTypeFilter.of("application/pdf"));
}
```

Más ejemplos:

```java
// Documentos HTML a los que les cambió el título
PropertyChangedFilter.of("cm:title").and(NodeTypeFilter.of("cm:content")).and(MimeTypeFilter.of("text/html"))

// Cualquier cosa que NO sea carpeta
IsFolderFilter.get().negate()
```

> ☕ **Java — interfaces funcionales y lambdas.** `EventFilter` está anotada con `@FunctionalInterface`: tiene un
> único método abstracto, `test(event)`. Eso permite escribir un filtro como **lambda** en una línea:
>
> ```java
> return IsFolderFilter.get()
>     .and(event -> "html".equalsIgnoreCase(((NodeResource) event.getData().getResource()).getName()));
> ```

## 5.3 Un filtro propio (y su test)

Para lógica reutilizable conviene una clase. `NameEndsWithFilter` acepta nodos cuyo nombre termina en un sufijo:

```java
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
```

> ☕ **Java — *pattern matching* con `instanceof` (Java 16+).** `x instanceof NodeResource node` comprueba el tipo
> **y** declara la variable `node` ya convertida. Equivale a un `if (x instanceof NodeResource) { NodeResource node = (NodeResource) x; ... }`.

> ☕ **Java — el patrón *static factory* (`of`).** El SDK usa `MimeTypeFilter.of(...)` en vez de `new`. Lo imitamos
> para que nuestros filtros se lean igual que los del SDK.

Los filtros son lógica pura: **se testean sin Alfresco**, construyendo eventos con los *builders* del modelo:

```java
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
```

Ejecuta los tests con `mvn test`.

## 5.4 Trampa real: `resourceBefore` solo trae lo que cambió

Al probar este curso contra ACS 26.2 encontramos esto. Al asignar un título a un documento nuevo, el
repositorio emite **dos** `node.Updated`:

```jsonc
// 1º evento: se asigna cm:title
"resourceBefore": { "properties": { "cm:title": null }, "aspectNames": ["cm:versionable","cm:auditable"] }

// 2º evento: se registra el aspecto cm:titled (no cambió ninguna propiedad)
"resourceBefore": { "aspectNames": ["cm:versionable","cm:titled","cm:auditable"] }
```

`resourceBefore` **no es el nodo completo anterior**, sino sólo los campos que cambiaron. Y `PropertyAddedFilter`
considera "agregada" toda propiedad que *no aparece* en `resourceBefore` y sí después… así que **acepta los dos
eventos** y tu handler corre dos veces.

La forma fiable: **si la clave está presente en `resourceBefore.properties` (aunque sea `null`), cambió**.
Eso hace `PropertySetFilter` (`proyecto/.../filter/PropertySetFilter.java`):

```java
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
```

Y el handler que lo usa:

```java
@Component
public class TitleChangedHandler implements OnNodeUpdatedEventHandler {

    @Override
    public EventFilter getEventFilter() {
        return PropertySetFilter.of("cm:title");
    }

    @Override
    public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
        NodeResource after = (NodeResource) event.getData().getResource();
        Map<String, ?> before = ((NodeResource) event.getData().getResourceBefore()).getProperties();
        LOGGER.info("[TITULO] '{}': '{}' -> '{}'", after.getName(), before.get("cm:title"), after.getProperties().get("cm:title"));
    }
}
```

Resultado real:

```
[TITULO] 'otra.txt': 'null' -> 'Texto: otra.txt'
[TITULO] 'otra.txt': 'Texto: otra.txt' -> 'Editado a mano'
```

`PropertySetFilterTest` cubre los cuatro casos (agregada, modificada, otro cambio, eliminada).

> 💡 **Lección general:** antes de confiar en un filtro, mira el JSON real del evento (lección 9). Los tests con
> eventos construidos a mano sólo valen si reproducen lo que el repositorio realmente envía.

Siguiente: [Lección 6 — La REST API desde Java](06-rest-api.md)
