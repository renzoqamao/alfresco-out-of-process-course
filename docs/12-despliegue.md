# Lección 12 — Despliegue y buenas prácticas

## 12.1 Empaquetar

```bash
cd proyecto
mvn clean package
java -jar target/acs-oop-extension-1.0.0-SNAPSHOT.jar
```

> 🌱 **Spring Boot — *fat jar*.** `spring-boot-maven-plugin` empaqueta tu código **y todas las dependencias** en un
> único jar ejecutable. No hace falta Tomcat ni servidor de aplicaciones.

## 12.2 Configuración por entorno

Nunca recompiles para cambiar URLs o credenciales. Spring Boot lee, por orden de prioridad (resumido):
argumentos `--prop=valor` > variables de entorno > `application.properties`.

```bash
java -jar app.jar \
  --spring.activemq.broker-url=tcp://activemq-prod:61616 \
  --content.service.url=https://acs.miempresa.com
```

Equivalente con variables de entorno (*relaxed binding*: mayúsculas, `.` y `-` → `_`):

| Propiedad | Variable de entorno |
|-----------|---------------------|
| `spring.activemq.broker-url` | `SPRING_ACTIVEMQ_BROKER_URL` |
| `spring.activemq.user` / `password` | `SPRING_ACTIVEMQ_USER` / `SPRING_ACTIVEMQ_PASSWORD` |
| `content.service.url` | `CONTENT_SERVICE_URL` |
| `content.service.security.basicAuth.username` | `CONTENT_SERVICE_SECURITY_BASICAUTH_USERNAME` |
| `content.service.security.basicAuth.password` | `CONTENT_SERVICE_SECURITY_BASICAUTH_PASSWORD` |

También puedes usar **perfiles**: `application-prod.properties` + `--spring.profiles.active=prod`.

## 12.3 Docker

> *Esta sección no se probó en el curso (no se construyó ni ejecutó la imagen).*

`proyecto/Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jre
COPY target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Para unirla al compose de Alfresco, ejecútala en la **misma red** y usa los nombres de servicio
(`alfresco`, `activemq`) en lugar de `localhost`. Por ejemplo, con la red del compose local (`acs-262_default`):

```bash
mvn clean package
docker build -t acs-oop-extension proyecto
docker run --rm --network acs-262_default \
  -e SPRING_ACTIVEMQ_BROKER_URL=tcp://activemq:61616 \
  -e CONTENT_SERVICE_URL=http://alfresco:8080 \
  acs-oop-extension
```

O como servicio en el `docker-compose`:

```yaml
  acs-oop-extension:
    image: acs-oop-extension
    environment:
      SPRING_ACTIVEMQ_BROKER_URL: tcp://activemq:61616
      SPRING_ACTIVEMQ_USER: admin
      SPRING_ACTIVEMQ_PASSWORD: admin
      CONTENT_SERVICE_URL: http://alfresco:8080
    depends_on:
      - alfresco
      - activemq
```

> Alternativa sin Dockerfile: `mvn spring-boot:build-image` genera una imagen OCI con Buildpacks.

## 12.4 Buenas prácticas

Resumen de lo comprobado a lo largo del curso, como lista de revisión antes de producción.

### Los eventos se pierden si tu app no está escuchando

La suscripción al topic es **no durable**: los eventos ocurridos con la extensión parada no llegan nunca
(comprobado). Opciones:

- **Reconciliación al arrancar** ([lección 10](10-asincrono-y-reconciliacion.md#104-reconciliación-recuperar-lo-que-pasó-con-la-app-parada)).
- **Alfresco Event Gateway** (Enterprise): gestiona suscripciones y puede publicar en un destino propio.
- **ActiveMQ:** *virtual topics* o suscripciones durables en el broker (requiere tocar su configuración).

### Un handler que falla repite el evento… y luego lo pierde

Una excepción no capturada provoca hasta 6 reintentos del evento completo, bloquea a los handlers siguientes y,
al final, el evento se descarta ([lección 7.3](07-reaccionar.md#73-qué-pasa-cuando-un-handler-falla)).
Captura los errores permanentes; deja subir sólo los transitorios.

### Idempotencia

El mismo hecho puede procesarse más de una vez: reintentos del broker (que repiten también los handlers que ya
terminaron), reconciliación, varias instancias, cadenas de eventos. Diseña cada acción para que repetirla no cause
daño: "asegurar que tiene la etiqueta", no "agregar una etiqueta".

### Varias instancias = eventos duplicados

Cada instancia de la extensión es un suscriptor independiente del topic y **recibe todos los eventos** (lo vimos al
tener la extensión y un cliente STOMP conectados a la vez: ambos recibieron todo). No escales horizontalmente sin
coordinar el trabajo (bloqueo distribuido, una cola intermedia, o una sola instancia activa).

### No bloquees el listener

Los handlers se ejecutan **secuencialmente en el hilo del listener JMS**. Para trabajo pesado, delega en `@Async`
([lección 10](10-asincrono-y-reconciliacion.md)), sabiendo que pierdes los reintentos automáticos y el orden.

### Filtra lo antes posible

- En el repositorio: `repo.event2.filter.*` (lección 9.6).
- En tu app: filtros baratos (`IsFileFilter`, tipo, mimetype) antes que filtros que llaman a la REST API.

### Seguridad

- Cuenta de servicio con permisos mínimos; credenciales por variables de entorno o un gestor de secretos, nunca en el repositorio git.
- Restringe el acceso al broker (puertos 61616/61613 sólo desde la red interna).
- Los eventos llevan nombres, rutas y propiedades: trátalos como datos sensibles en tus logs.

### Observabilidad

- Loguea el `id` del evento y el `nodeId` en cada acción: permite correlacionar con los logs de Alfresco.
- Agrega `spring-boot-starter-actuator` para *health checks* (`/actuator/health`) si la despliegas en Kubernetes.

Siguiente: [Lección 13 — Ejercicios](13-ejercicios.md)
