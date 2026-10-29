# Lección 11 — Modelo de contenido propio

En proyectos reales casi nunca se trabaja sólo con `cm:content` y `cm:folder`: cada negocio define sus
**tipos** y **propiedades** (facturas, contratos, expedientes…). En esta lección:

1. Definimos un modelo de **facturas**.
2. La propia extensión lo **despliega en Alfresco sin reiniciarlo** (modelo dinámico).
3. Escribimos reglas de negocio que reaccionan a ese tipo y sus propiedades.

## 11.1 El modelo

`proyecto/src/main/resources/models/factura-model.xml`:

```xml
<model name="fac:facturaModel" xmlns="http://www.alfresco.org/model/dictionary/1.0">
  <description>Modelo de facturas del curso out-of-process</description>
  <author>Curso ACS OOP</author>
  <version>1.0</version>

  <imports>
    <import uri="http://www.alfresco.org/model/dictionary/1.0" prefix="d"/>
    <import uri="http://www.alfresco.org/model/content/1.0" prefix="cm"/>
  </imports>

  <namespaces>
    <namespace uri="http://www.curso.com/model/factura/1.0" prefix="fac"/>
  </namespaces>

  <constraints>
    <constraint name="fac:estados" type="LIST">
      <parameter name="allowedValues">
        <list>
          <value>PENDIENTE</value>
          <value>APROBADA</value>
          <value>PAGADA</value>
          <value>RECHAZADA</value>
        </list>
      </parameter>
    </constraint>
  </constraints>

  <types>
    <type name="fac:factura">
      <title>Factura</title>
      <parent>cm:content</parent>
      <properties>
        <property name="fac:numero"><title>Numero</title><type>d:text</type></property>
        <property name="fac:proveedor"><title>Proveedor</title><type>d:text</type></property>
        <property name="fac:importe"><title>Importe</title><type>d:double</type></property>
        <property name="fac:estado">
          <title>Estado</title>
          <type>d:text</type>
          <default>PENDIENTE</default>
          <constraints><constraint ref="fac:estados"/></constraints>
        </property>
        <property name="fac:fechaPago"><title>Fecha de pago</title><type>d:date</type></property>
      </properties>
    </type>
  </types>
</model>
```

| Elemento | Para qué |
|----------|----------|
| `namespace` + `prefix` | Identifica tu modelo. Usa una URI propia y un prefijo corto que no choque con otros (`cm`, `sys`, `st`… están reservados) |
| `type` con `parent` `cm:content` | Una factura **es un** documento: hereda nombre, contenido, versiones, etc. |
| `d:text`, `d:double`, `d:date` | Tipos de datos. Otros: `d:int`, `d:long`, `d:boolean`, `d:datetime`, `d:noderef`… |
| `constraint` `LIST` | El repositorio **rechaza** valores fuera de la lista |
| `default` | Valor que el repositorio asigna al crear el nodo |

## 11.2 Desplegarlo sin reiniciar: modelos dinámicos

Un **modelo dinámico** es un fichero XML de tipo `cm:dictionaryModel` guardado en *Company Home › Data Dictionary
› Models*. Cuando su propiedad `cm:modelActive` vale `true`, Alfresco lo carga al momento, sin reiniciar.
Como todo eso se hace con la REST API, **la extensión puede desplegar su propio modelo**
(`proyecto/.../model/ModelBootstrap.java`):

```java
@Component
@Order(0)
@ConditionalOnProperty(name = "curso.model-bootstrap.enabled", havingValue = "true")
public class ModelBootstrap implements CommandLineRunner {

    private static final String MODELS_PATH = "Data Dictionary/Models";
    private static final String MODEL_FILE = "factura-model.xml";

    private final NodesApi nodesApi;

    public ModelBootstrap(NodesApi nodesApi) {
        this.nodesApi = nodesApi;
    }

    @Override
    public void run(String... args) throws IOException {
        try {
            nodesApi.getNode("-root-", null, MODELS_PATH + "/" + MODEL_FILE, null);
            LOGGER.info("[MODELO] {} ya esta desplegado", MODEL_FILE);
            return;
        } catch (FeignException.NotFound e) {
            LOGGER.info("[MODELO] Desplegando {}", MODEL_FILE);
        }

        String modelsFolderId = nodesApi.getNode("-root-", null, MODELS_PATH, null).getBody().getEntry().getId();
        String modelId = nodesApi.createNode(modelsFolderId,
                        new NodeBodyCreate().name(MODEL_FILE).nodeType("cm:dictionaryModel"), null, null, null, null, null)
                .getBody().getEntry().getId();

        byte[] xml = new ClassPathResource("models/" + MODEL_FILE).getContentAsByteArray();
        nodesApi.updateNodeContent(modelId, xml, null, null, null, null, null);

        // Al activarlo, Alfresco valida el XML: si es invalido, esta llamada falla con 4xx/5xx
        nodesApi.updateNode(modelId, new NodeBodyUpdate().properties(Map.of("cm:modelActive", true)), null, null);
        LOGGER.info("[MODELO] {} activo (id {})", MODEL_FILE, modelId);
    }
}
```

Tres pasos, en este orden: **crear** el nodo `cm:dictionaryModel`, **subir** el XML (`updateNodeContent`) y
**activarlo**. `@Order(0)` hace que corra antes que los demás `CommandLineRunner`.

> 🌱 **Spring — `ClassPathResource`.** Todo lo que está en `src/main/resources` acaba dentro del jar. `ClassPathResource`
> lo lee desde ahí, funcione la app desde el IDE, con `mvn spring-boot:run` o con `java -jar`.

> ☕ **Java — `getNode` con `relativePath`.** El tercer parámetro de `getNode` es una ruta relativa al nodo indicado.
> `getNode("-root-", null, "Data Dictionary/Models", null)` resuelve una carpeta por su ruta sin buscar.
> Si no existe, la API responde 404 y Feign lanza `FeignException.NotFound`, que aquí usamos como "no está desplegado".

Salida real (1ª y 2ª ejecución):

```
[MODELO] Desplegando factura-model.xml
[MODELO] factura-model.xml activo (id c0f91661-d004-4392-b916-61d0040392a1)
...
[MODELO] factura-model.xml ya esta desplegado
```

### Cuándo usar otra vía

| Vía | Ventaja | Inconveniente |
|-----|---------|---------------|
| **Modelo dinámico** (esta lección) | Sin reiniciar; lo despliega la extensión | Vive en el repositorio: hay que versionarlo y gobernarlo como código |
| **Model Manager** (Share/Admin) | Gráfico, sin XML | Difícil de versionar y de llevar entre entornos |
| **Bootstrap in-process** (JAR/AMP) | El modelo viaja con el despliegue de ACS | Requiere reiniciar ACS: justo lo que queremos evitar |

> ⚠️ **Cambiar un modelo en uso es delicado.** Puedes **agregar** propiedades o tipos, pero no borrar ni cambiar el
> tipo de datos de una propiedad mientras existan nodos que la usen (incluidos los que están en la papelera).
> Para desactivar o borrar el modelo, antes hay que eliminar definitivamente todos sus nodos.

## 11.3 Reglas de negocio sobre el tipo propio

**Regla 1 — Aprobación automática:** al crear una factura, si `fac:importe` ≤ umbral se aprueba sola; si no, queda
`PENDIENTE` con un comentario (`FacturaCreatedHandler.java`):

```java
@Override
public EventFilter getEventFilter() {
    // NodeTypeFilter compara el tipo exacto: no incluye subtipos
    return NodeTypeFilter.of("fac:factura");
}

@Override
public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
    NodeResource node = (NodeResource) event.getData().getResource();
    // Las propiedades d:double llegan como Number desde el JSON del evento
    Number importe = (Number) node.getProperties().get("fac:importe");
    if (importe == null) {
        LOGGER.warn("[FACTURA] '{}' sin fac:importe, se deja PENDIENTE", node.getName());
        return;
    }
    if (importe.doubleValue() <= umbral) {
        nodesApi.updateNode(node.getId(), new NodeBodyUpdate().properties(Map.of("fac:estado", "APROBADA")), null, null);
        LOGGER.info("[FACTURA] {} ({}) aprobada automaticamente", node.getProperties().get("fac:numero"), importe);
    } else {
        commentsApi.createComment(node.getId(),
                new CommentBody().content("Importe mayor a " + umbral + ": requiere aprobacion manual"), null);
        LOGGER.info("[FACTURA] {} ({}) requiere aprobacion manual", node.getProperties().get("fac:numero"), importe);
    }
}
```

```properties
curso.factura.umbral-aprobacion=1000
```

**Regla 2 — Fecha de pago:** cuando `fac:estado` cambia a `PAGADA`, se registra la fecha (`FacturaPagadaHandler.java`).
Reutiliza el `PropertySetFilter` de la lección 5 y combina tres filtros:

```java
@Override
public EventFilter getEventFilter() {
    return NodeTypeFilter.of("fac:factura")
            .and(PropertySetFilter.of("fac:estado"))                       // el estado cambió en este evento…
            .and(PropertyCurrentValueFilter.of("fac:estado", "PAGADA"));   // …y ahora vale PAGADA
}

@Override
public void handleEvent(RepoEvent<DataAttributes<Resource>> event) {
    NodeResource node = (NodeResource) event.getData().getResource();
    if (node.getProperties().get("fac:fechaPago") != null) {
        return; // idempotente: ya tenia fecha
    }
    String hoy = LocalDate.now().toString();
    nodesApi.updateNode(node.getId(), new NodeBodyUpdate().properties(Map.of("fac:fechaPago", hoy)), null, null);
    LOGGER.info("[FACTURA] {} pagada el {}", node.getProperties().get("fac:numero"), hoy);
}
```

¿Por qué no hay bucle? Al escribir `fac:fechaPago` se genera otro `node.Updated`, pero en él `fac:estado` **no
aparece** en `resourceBefore`, así que `PropertySetFilter` lo descarta (lección 5.4).

## 11.4 Probarlo

```bash
API=http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1

# Crear facturas: basta con indicar el tipo y sus propiedades
curl -u admin:admin -H "Content-Type: application/json" \
  -d '{"name":"F-001.pdf","nodeType":"fac:factura","properties":{"fac:numero":"F-001","fac:proveedor":"ACME","fac:importe":500}}' \
  "$API/nodes/<ID_CARPETA>/children"

curl -u admin:admin -H "Content-Type: application/json" \
  -d '{"name":"F-002.pdf","nodeType":"fac:factura","properties":{"fac:numero":"F-002","fac:proveedor":"ACME","fac:importe":5000}}' \
  "$API/nodes/<ID_CARPETA>/children"

# Marcar F-002 como pagada
curl -u admin:admin -X PUT -H "Content-Type: application/json" \
  -d '{"properties":{"fac:estado":"PAGADA"}}' "$API/nodes/<ID_F-002>"

# Un valor fuera de la lista: el repositorio responde 422
curl -u admin:admin -X PUT -H "Content-Type: application/json" \
  -d '{"properties":{"fac:estado":"INVENTADO"}}' "$API/nodes/<ID_F-001>"
```

Log real:

```
[FACTURA] F-001 (500.0) aprobada automaticamente
[FACTURA] F-002 (5000.0) requiere aprobacion manual
[FACTURA] F-002 pagada el 2026-10-29
```

Estado final consultado por REST:

```
F-001.pdf  fac:estado=APROBADA  fac:importe=500.0
F-002.pdf  fac:estado=PAGADA    fac:importe=5000.0  fac:fechaPago=2026-10-29T00:00:00.000+0000
```

## 11.5 Cómo se ven las propiedades propias en el evento

Extracto real del `node.Updated` al marcar F-002 como pagada
(completo en [`eventos-ejemplo/factura-updated-pagada.json`](eventos-ejemplo/factura-updated-pagada.json)):

```jsonc
"resource": {
  "nodeType": "fac:factura",
  "properties": {
    "fac:numero": "F-002",
    "fac:proveedor": "ACME",
    "fac:importe": 5000.0,          // d:double → número JSON → Double en Java
    "fac:estado": "PAGADA",
    "fm:commentCount": 2,
    "cm:taggable": [ { "storeRef": {...}, "id": "a25c9f5d-..." } ]
  }
},
"resourceBefore": {
  "properties": { "fac:estado": "PENDIENTE" }   // sólo lo que cambió
}
```

Observaciones:

- El **valor por defecto** del modelo ya viene aplicado en el `node.Created`: `"fac:estado": "PENDIENTE"`.
- Los tipos de datos se convierten a JSON: números como números, fechas como texto ISO. Haz el *cast* con
  cuidado (`Number`, no `Double`, por si el JSON trae un entero).
- **F-002.pdf tenía 2 comentarios y la etiqueta `pdf`**: como su nombre termina en `.pdf`, Alfresco le asignó el
  mimetype `application/pdf` y **también** reaccionó `PdfUploadedHandler` (lección 7). Una extensión es un conjunto
  de reglas que actúan a la vez sobre los mismos nodos; diseña los filtros pensando en todas ellas.

## 11.6 Consultar el modelo desde Java

Para leer el diccionario (tipos, propiedades, restricciones) usa `TypesApi` / `AspectsApi` del paquete
`org.alfresco.model.handler`:

```java
Type type = typesApi.getType("fac:factura").getBody().getEntry();
LOGGER.info("{} hereda de {}", type.getTitle(), type.getParentId());
```

Siguiente: [Lección 12 — Despliegue y buenas prácticas](12-despliegue.md)
