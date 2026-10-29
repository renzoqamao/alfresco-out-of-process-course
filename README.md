# Curso: extensiones *out-of-process* para Alfresco Content Services 26.2

Aprende desde cero a construir extensiones que corren **fuera** del repositorio de Alfresco usando el
[Alfresco Java SDK](https://github.com/Alfresco/alfresco-java-sdk) (Event API + REST API) con Spring Boot.

Referencia oficial: [Out-of-Process Extension Points (ACS 26.2)](https://docs.hyland.com/r/Alfresco/Alfresco-Content-Services/26.2/Alfresco-Content-Services/Develop/Out-of-Process-Extension-Points)

## Temario

| # | Lección | Qué aprendes |
|---|---------|--------------|
| 1 | [Introducción](docs/01-introduccion.md) | In-process vs out-of-process, arquitectura de eventos |
| 2 | [Preparar el entorno](docs/02-entorno.md) | JDK, Maven, ACS 26.2 en Docker, ActiveMQ |
| 3 | [Crear el proyecto](docs/03-crear-proyecto.md) | Spring Boot desde cero, `pom.xml`, `application.properties` |
| 4 | [Tu primer event handler](docs/04-primer-handler.md) | `OnNodeCreatedEventHandler`, el objeto `RepoEvent` |
| 5 | [Filtros de eventos](docs/05-filtros.md) | Filtros del SDK, combinarlos, crear uno propio y testearlo |
| 6 | [La REST API desde Java](docs/06-rest-api.md) | `NodesApi`, `SearchApi`, paginación, errores |
| 7 | [Reaccionar a eventos con la REST API](docs/07-reaccionar.md) | Etiquetar, comentar, actualizar nodos; cadenas de eventos; qué pasa cuando un handler falla |
| 8 | [Spring Integration](docs/08-spring-integration.md) | La alternativa a los handlers; reenviar eventos a un webhook |
| 9 | [El modelo de eventos](docs/09-modelo-de-eventos.md) | Tipos de evento, JSON, `resourceBefore`, `extensionAttributes` |
| 10 | [Procesamiento asíncrono y reconciliación](docs/10-asincrono-y-reconciliacion.md) | `@Async`, pool de hilos, checkpoint y recuperación de eventos perdidos |
| 11 | [Modelo de contenido propio](docs/11-modelo-de-contenido.md) | Modelo dinámico desplegado por la extensión, reglas sobre tipos propios |
| 12 | [Despliegue y buenas prácticas](docs/12-despliegue.md) | Jar, Docker, variables de entorno, lista de revisión para producción |
| 13 | [Ejercicios](docs/13-ejercicios.md) | Práctica guiada con soluciones |

Las lecciones van de menos a más: 1–3 preparan el terreno, 4–9 cubren lo esencial del SDK y 10–12 los temas
avanzados que necesita una extensión en producción.

Las notas marcadas con **☕ Java** o **🌱 Spring Boot** explican conceptos del lenguaje o del framework
para quien viene de otro stack. Si ya los conoces, sáltalas.

## El proyecto

La carpeta [`proyecto/`](proyecto) contiene la extensión **terminada** que se construye a lo largo del curso.
Cada clase indica en su Javadoc en qué lección aparece.

```
proyecto/
├── pom.xml
├── Dockerfile                                  # Lección 12
└── src/
    ├── main/java/com/curso/alfresco/
    │   ├── OopExtensionApplication.java            # Lección 3
    │   ├── handler/
    │   │   ├── NodeCreatedLoggerHandler.java       # Lección 4
    │   │   ├── TitleChangedHandler.java            # Lección 5
    │   │   ├── PdfUploadedHandler.java             # Lecciones 5 y 7
    │   │   ├── TxtAutoTitleHandler.java            # Lección 7
    │   │   ├── ConfidentialTxtHandler.java         # Lección 10
    │   │   ├── FacturaCreatedHandler.java          # Lección 11
    │   │   └── FacturaPagadaHandler.java           # Lección 11
    │   ├── filter/
    │   │   ├── NameEndsWithFilter.java             # Lección 5
    │   │   └── PropertySetFilter.java              # Lección 5.4
    │   ├── rest/StartupCheck.java                  # Lección 6
    │   ├── integration/DeletedNodesFlow.java       # Lección 8
    │   ├── async/AsyncConfig.java                  # Lección 10
    │   ├── service/ConfidentialClassifier.java     # Lección 10
    │   ├── reconcile/                              # Lección 10
    │   │   ├── Checkpoint.java
    │   │   ├── CheckpointFlow.java
    │   │   └── ReconciliationRunner.java
    │   └── model/ModelBootstrap.java               # Lección 11
    ├── main/resources/
    │   ├── application.properties
    │   └── models/factura-model.xml                # Lección 11
    └── test/java/com/curso/alfresco/filter/        # tests de los filtros (mvn test)
```

En [`docs/eventos-ejemplo/`](docs/eventos-ejemplo) están los JSON reales de eventos capturados de ACS 26.2.

> El código del curso (proyecto, fragmentos de las lecciones y soluciones de los ejercicios) se compiló y se
> probó contra una instancia ACS 26.2 Enterprise con Elasticsearch. Las secciones que no se pudieron probar lo
> indican explícitamente (OAuth2, Docker).

Ejecutarla (con ACS en `localhost:8080` y ActiveMQ en `localhost:61616`):

```bash
cd proyecto
mvn spring-boot:run
```

## Versiones usadas

| Componente | Versión |
|------------|---------|
| Alfresco Content Services | 26.2 |
| Alfresco Java SDK | 7.3.3 |
| Spring Boot | 4.0.6 (la que exige el SDK) |
| Java | 17 o superior |
| Maven | 3.9+ |
