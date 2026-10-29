# Lección 6 — La REST API desde Java

Los eventos te dicen **qué pasó**; la REST API te permite **hacer algo**. El starter
`alfresco-acs-java-rest-api-spring-boot-starter` genera clientes Java (con OpenFeign) para toda la API pública
y los registra como *beans* listos para inyectar.

## 6.1 Clientes disponibles

| Paquete | Clientes (interfaces) | Endpoint base |
|---------|----------------------|---------------|
| `org.alfresco.core.handler` | `NodesApi`, `ContentApi`, `TagsApi`, `CommentsApi`, `SitesApi`, `PeopleApi`, `GroupsApi`, `VersionsApi`, `RenditionsApi`, `SharedLinksApi`, `TrashcanApi`, `CategoriesApi`, `ActionsApi`, `AuditApi`, `QueriesApi`… | `/alfresco/versions/1` |
| `org.alfresco.search.handler` | `SearchApi` | `/search/versions/1` |
| `org.alfresco.model.handler` | `TypesApi`, `AspectsApi` | `/model/versions/1` |
| `org.alfresco.discovery.handler` | `DiscoveryApi` | `/discovery` |
| `org.alfresco.auth.handler` | `AuthenticationApi` | `/authentication/versions/1` |

Cada método corresponde a un endpoint documentado en el **API Explorer** de tu instancia
(`http://localhost:8080/api-explorer`). Los parámetros van en el mismo orden que en la documentación;
los opcionales que no uses se pasan como `null`.

## 6.2 Primera llamada: listar Company Home

`proyecto/src/main/java/com/curso/alfresco/rest/StartupCheck.java`:

```java
@Component
@ConditionalOnProperty(name = "curso.startup-check.enabled", havingValue = "true")
public class StartupCheck implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupCheck.class);

    private final NodesApi nodesApi;

    public StartupCheck(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public void run(String... args) {
        NodeChildAssociationPaging children = nodesApi
                .listNodeChildren("-root-", 0, 100, null, null, null, null, null, null)
                .getBody();

        LOGGER.info("Conectado a Alfresco. Company Home contiene {} nodos:", children.getList().getPagination().getCount());
        children.getList().getEntries()
                .forEach(e -> LOGGER.info("  - {} ({})", e.getEntry().getName(), e.getEntry().getNodeType()));
    }
}
```

Salida real:

```
Conectado a Alfresco. Company Home contiene 42 nodos:
  - Data Dictionary (cm:folder)
  - Guest Home (cm:folder)
  - Shared (cm:folder)
  - Sites (st:sites)
  ...
```

> 🌱 **Spring Boot — inyección por constructor.** No hacemos `new NodesApi()`: declaramos el parámetro en el
> constructor y Spring entrega el bean que creó el starter. Es la forma recomendada (frente a `@Autowired` en
> atributos) porque el campo puede ser `final` y la clase se puede instanciar en un test pasándole un *mock*.

> 🌱 **Spring Boot — `CommandLineRunner` y `@ConditionalOnProperty`.** Un `CommandLineRunner` se ejecuta una vez,
> justo después de arrancar. `@ConditionalOnProperty` hace que el bean sólo exista si
> `curso.startup-check.enabled=true`; puedes desactivarlo sin tocar código:
> `mvn spring-boot:run -Dspring-boot.run.arguments=--curso.startup-check.enabled=false`.

### Alias útiles de nodeId

| Alias | Nodo |
|-------|------|
| `-root-` | Company Home |
| `-shared-` | Shared |
| `-my-` | Home del usuario autenticado |

## 6.3 Anatomía de una respuesta

Todos los métodos devuelven `ResponseEntity<T>`: `getStatusCode()`, `getHeaders()` y `getBody()`.
El cuerpo sigue la estructura JSON de la API:

```
NodeEntry                     → { "entry": { ...nodo... } }
NodeChildAssociationPaging    → { "list": { "pagination": {...}, "entries": [ { "entry": {...} } ] } }
```

Por eso aparece tanto `.getBody().getEntry()` y `.getBody().getList().getEntries()`.

## 6.4 Operaciones frecuentes

```java
// Obtener un nodo con sus propiedades y la ruta
Node node = nodesApi.getNode(nodeId, List.of("properties", "path"), null, null).getBody().getEntry();

// Crear una carpeta
NodeBodyCreate body = new NodeBodyCreate().name("Facturas").nodeType("cm:folder");
Node folder = nodesApi.createNode(parentId, body, true /* autoRename */, null, null, null, null).getBody().getEntry();

// Actualizar propiedades (el aspecto cm:titled se agrega solo)
nodesApi.updateNode(nodeId, new NodeBodyUpdate().properties(Map.of("cm:title", "Mi título")), null, null);

// Descargar el contenido
InputStream in = nodesApi.getNodeContent(nodeId, true, null, null).getBody().getInputStream();

// Buscar con AFTS
SearchRequest req = new SearchRequest()
        .query(new RequestQuery().language(RequestQuery.LanguageEnum.AFTS).query("TYPE:'cm:content' AND cm:name:'*.pdf'"))
        .paging(new RequestPagination().maxItems(25).skipCount(0));
ResultSetPaging result = searchApi.search(req).getBody();
```

> 🔎 **La búsqueda depende del motor de indexación** que tenga tu ACS: **Solr** (Search Services) o
> **Elasticsearch** (Search Enterprise, como en la instancia del curso). Ambos aceptan AFTS, pero Elasticsearch
> no soporta todas sus funciones (algunos comodines, `PATH`/`ANCESTOR` según la versión, CMIS-QL…) y los resultados
> de comodines pueden diferir. Además el índice es **eventualmente consistente**: un nodo recién creado puede
> tardar unos segundos en aparecer. Prueba tus consultas en el API Explorer de *tu* instancia antes de programarlas.
> La consulta de la [lección 10](10-asincrono-y-reconciliacion.md) (tipo + mimetype + rango de `cm:modified`)
> está probada contra Elasticsearch.

> ☕ **Java — imports con el mismo nombre.** Tanto `org.alfresco.core.model` como `org.alfresco.search.model`
> tienen una clase `Node`. Importa las clases concretas (no `.*`) o el compilador dirá *reference to Node is ambiguous*.

## 6.5 Errores

Si la API responde 4xx/5xx, Feign lanza `feign.FeignException` (subclases como `FeignException.NotFound`,
`FeignException.Conflict`…). Captúrala cuando el error sea esperable:

```java
try {
    nodesApi.getNode(id, null, null, null);
} catch (FeignException.NotFound e) {
    LOGGER.info("El nodo {} ya no existe", id);   // típico: lo borraron después de emitirse el evento
}
```

> 💡 Recuerda que los eventos son **asíncronos**: cuando tu handler consulta un nodo, éste puede haber sido
> modificado o borrado después del evento. `404` en un handler no es raro; trátalo con calma.

## 6.6 Autenticación

Basic (lo que usamos en el curso):

```properties
content.service.security.basicAuth.username=admin
content.service.security.basicAuth.password=admin
```

En producción lo habitual es OAuth2 con Keycloak (Identity Service): ver el
[Extra](#extra--autenticación-oauth2-con-keycloak) al final de esta lección.

Y si necesitas algo propio: `content.service.security.delegated=true` y un bean que implemente
`DelegatedAuthenticationProvider`.

> 🔐 Usa una **cuenta de servicio** con los permisos mínimos, no `admin`, fuera del entorno de desarrollo.

---

## Extra — Autenticación OAuth2 con Keycloak

> *Sección opcional. La configuración del SDK está tomada de su código fuente (`OAuth2Configuration`,
> `OAuth2FeignRequestInterceptor`); no se probó en el curso porque la instancia de prueba no tenía Keycloak.*

### Cómo funciona

La extensión no actúa en nombre de una persona, así que usa el *grant* **`client_credentials`**: se autentica
como un cliente de Keycloak con su `client-id` y `client-secret`.

```
 Extensión ──(client-id + secret)──▶ Keycloak ──▶ access token (JWT)
     │
     └──(Authorization: Bearer <token>)──▶ REST API de Alfresco ──▶ valida el token contra Keycloak
```

1. La configuración OAuth2 del SDK se activa **sólo** si existe la propiedad
   `spring.security.oauth2.client.registration.alfresco-rest-api.provider`. El registro debe llamarse exactamente `alfresco-rest-api`.
2. Spring Security pide el token al `token-uri`.
3. `OAuth2FeignRequestInterceptor` agrega `Authorization: Bearer <token>` a cada llamada de `NodesApi`, `TagsApi`… y lo renueva cuando expira.
4. **Tu código no cambia**: los handlers del curso funcionan igual.

### Configuración de la extensión

```properties
# Quitar content.service.security.basicAuth.*
spring.security.oauth2.client.registration.alfresco-rest-api.provider=alfresco-identity-service
spring.security.oauth2.client.registration.alfresco-rest-api.client-id=mi-extension
spring.security.oauth2.client.registration.alfresco-rest-api.client-secret=secreto
spring.security.oauth2.client.registration.alfresco-rest-api.authorization-grant-type=client_credentials
spring.security.oauth2.client.provider.alfresco-identity-service.token-uri=http://keycloak:8080/realms/alfresco/protocol/openid-connect/token
```

> ⚠️ **Quita las propiedades `content.service.security.basicAuth.*`.** Cada mecanismo se activa sólo por la presencia
> de su propiedad; si dejas ambas, se registran los dos interceptores de Feign y las peticiones llevan dos cabeceras de autenticación.

> ⚠️ El README y el ejemplo del SDK usan `.../auth/realms/...`. Ese prefijo `/auth` sólo existe en Keycloak
> **anterior a la 17**; en versiones actuales la URL es `/realms/<realm>/...`.

> 🔐 El `client-secret` nunca va en git. Usa un *placeholder*:
> `...alfresco-rest-api.client-secret=${MI_EXTENSION_SECRET}` y define la variable `MI_EXTENSION_SECRET` en el entorno.
> No intentes la variable "relajada" `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_...`: `alfresco-rest-api` es una
> clave de mapa con guiones y Spring Boot no puede reconstruirla desde una variable de entorno.

### Qué necesitas en la infraestructura

1. **Keycloak** (o Alfresco Identity Service) accesible para la extensión y para el repositorio, con un realm (p. ej. `alfresco`).
2. **El repositorio configurado para aceptar tokens** (en `JAVA_OPTS` o `alfresco-global.properties`, y reiniciar):
   ```properties
   authentication.chain=identity-service1:identity-service,alfrescoNtlm1:alfrescoNtlm
   identity-service.auth-server-url=http://keycloak:8080
   identity-service.realm=alfresco
   identity-service.resource=alfresco
   ```
   Mantener `alfrescoNtlm1` en la cadena permite seguir usando usuarios locales (p. ej. `admin` por Basic).
3. **Un cliente en Keycloak para la extensión**: tipo confidencial (*Client authentication* activado) y
   *Service accounts roles* activado. De su pestaña *Credentials* salen el `client-id` y el `client-secret`.
4. **Un usuario en Alfresco para la cuenta de servicio.** Keycloak emite el token con
   `preferred_username = service-account-<client-id>` (p. ej. `service-account-mi-extension`), y Alfresco identifica
   al usuario por ese nombre. Créalo (o compruébalo tras la primera llamada) y dale **sólo** los permisos que la
   extensión necesita: es la identidad con la que quedarán registradas sus acciones (`modifiedByUser` en los eventos).

### Comprobación

```bash
# 1. Obtener un token a mano
curl -s -d grant_type=client_credentials -d client_id=mi-extension -d client_secret=secreto \
  http://localhost:8180/realms/alfresco/protocol/openid-connect/token

# 2. Usarlo contra Alfresco
curl -H "Authorization: Bearer <access_token>" \
  http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1/people/-me-
```

Si `people/-me-` devuelve `service-account-mi-extension`, la extensión funcionará con la configuración de arriba.
Para depurar el lado de la extensión: `logging.level.org.alfresco.rest.sdk.feign.oauth2=DEBUG`.

---

Siguiente: [Lección 7 — Reaccionar a eventos con la REST API](07-reaccionar.md)
