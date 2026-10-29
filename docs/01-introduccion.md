# Lección 1 — Introducción

## ¿Qué es una extensión *out-of-process*?

Durante años la forma de extender Alfresco fue **in-process**: empaquetar código Java (un JAR o AMP)
que se despliega **dentro** del Tomcat del repositorio. Behaviours, policies, web scripts, acciones Java…
todo vive en la misma JVM que Alfresco.

Eso funciona, pero tiene costos:

- Cada cambio exige **reiniciar el repositorio**.
- Un bug (memoria, bucle, excepción) puede **tumbar Alfresco entero**.
- Las **actualizaciones** de Alfresco obligan a recompilar y revalidar las extensiones contra APIs internas.
- No escala por separado: si tu lógica es pesada, compite por CPU con el repositorio.

Una extensión **out-of-process** es una aplicación independiente (en este curso, Spring Boot) que:

1. **Escucha eventos** que el repositorio publica en un broker de mensajería (ActiveMQ).
2. **Actúa** sobre Alfresco usando su **REST API pública**.

```
  ┌──────────────────────┐   publica eventos   ┌─────────────┐   consume   ┌──────────────────────┐
  │  Repositorio ACS     │ ──────────────────▶ │  ActiveMQ   │ ──────────▶ │  Tu extensión        │
  │  (Tomcat :8080)      │                     │  topic:     │             │  (Spring Boot)       │
  │                      │ ◀────────────────── │  alfresco.  │             │                      │
  └──────────────────────┘    REST API (HTTP)  │  repo.event2│             └──────────┬───────────┘
            ▲                                   └─────────────┘                        │
            └──────────────────────────────────────────────────────────────────────────┘
                                   llamadas REST (crear, etiquetar, actualizar...)
```

## Ventajas y límites

| | In-process | Out-of-process |
|---|---|---|
| Despliegue | Reinicia ACS | Independiente, sin tocar ACS |
| Aislamiento de fallos | No | Sí |
| Upgrades de ACS | Recompilar contra APIs internas | Solo depende de la REST API y el modelo de eventos (estables) |
| Escalado | Junto con ACS | Por separado |
| Transaccionalidad | **Síncrono**, misma transacción: puede *impedir* una operación | **Asíncrono**, después del commit: solo puede *reaccionar* |
| Latencia | Inmediata | Milisegundos a segundos |

> **Regla práctica:** si necesitas *validar y rechazar* una operación (p. ej. "no se puede borrar esto"),
> necesitas código in-process. Si necesitas *reaccionar* a algo que ya pasó (clasificar, notificar,
> sincronizar con otro sistema, enriquecer metadatos), out-of-process es la mejor opción.

## El Alfresco Java SDK

El SDK tiene dos piezas que usaremos juntas:

| Librería | Artefacto (starter) | Para qué |
|----------|---------------------|----------|
| **Java Event API** | `alfresco-java-event-api-spring-boot-starter` | Conectarse a ActiveMQ, convertir el JSON a objetos `RepoEvent` y despacharlos a tus handlers |
| **Java REST API** | `alfresco-acs-java-rest-api-spring-boot-starter` | Clientes Java generados (OpenFeign) para la REST API: `NodesApi`, `TagsApi`, `SearchApi`... |

Ambos son *starters* de Spring Boot: basta con agregarlos al `pom.xml` y configurar unas propiedades.

> 🌱 **Spring Boot — ¿qué es un *starter*?**
> Un starter es una dependencia que trae otras dependencias **y** una *autoconfiguración*: clases que,
> al arrancar la aplicación, crean automáticamente los objetos (*beans*) que necesitas. Por eso no tendrás que
> escribir código para conectarte a ActiveMQ: el starter del SDK lo hace por ti leyendo `application.properties`.

## Cómo funciona por dentro (resumen)

1. Al hacer *commit* de una transacción, el repositorio genera eventos (`org.alfresco.event.node.Created`, etc.)
   y los publica como JSON en el **topic** `alfresco.repo.event2` de ActiveMQ.
2. El starter del SDK crea un *listener* JMS suscrito a ese topic.
3. Cada mensaje se transforma en un `RepoEvent<DataAttributes<Resource>>`.
4. El evento se enruta a dos posibles "salidas":
   - **Handlers**: tus clases que implementan `OnNodeCreatedEventHandler`, etc. (lecciones 4–7).
   - **Spring Integration**: un canal al que puedes conectar flujos (lección 8).

Siguiente: [Lección 2 — Preparar el entorno](02-entorno.md)
