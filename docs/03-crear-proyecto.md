# Lección 3 — Crear el proyecto desde cero

Vamos a crear una aplicación Spring Boot vacía y convertirla en una extensión de Alfresco.

## 3.1 Generar el esqueleto

**Opción A — Spring Initializr (web):** entra a <https://start.spring.io> y elige:

- Project: **Maven** · Language: **Java** · Spring Boot: **4.0.x**
- Group: `com.curso.alfresco` · Artifact: `acs-oop-extension`
- Packaging: **Jar** · Java: **17**
- Dependencias: ninguna (las del SDK las agregamos a mano).

**Opción B — a mano:** crea esta estructura:

```
acs-oop-extension/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/curso/alfresco/OopExtensionApplication.java
    │   └── resources/application.properties
    └── test/java/com/curso/alfresco/
```

> ☕ **Java — paquetes y carpetas.** En Java el *paquete* (`package com.curso.alfresco;`) debe coincidir con la
> ruta de carpetas bajo `src/main/java`. Maven espera exactamente esa convención (`src/main/java`,
> `src/main/resources`, `src/test/java`).

## 3.2 El `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>
    <relativePath/>
  </parent>

  <groupId>com.curso.alfresco</groupId>
  <artifactId>acs-oop-extension</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <java.version>17</java.version>
    <alfresco-java-sdk.version>7.3.3</alfresco-java-sdk.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.alfresco</groupId>
      <artifactId>alfresco-java-event-api-spring-boot-starter</artifactId>
      <version>${alfresco-java-sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>org.alfresco</groupId>
      <artifactId>alfresco-acs-java-rest-api-spring-boot-starter</artifactId>
      <version>${alfresco-java-sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

Puntos importantes:

- **La versión de Spring Boot debe coincidir con la del SDK.** El SDK 7.3.x está compilado contra
  Spring Boot 4.0.x. Mezclar versiones mayores (p. ej. SDK 7.3 con Boot 3.x) produce errores
  `ClassNotFoundException`/`NoSuchMethodError` al arrancar. Puedes verificar la versión que usa el SDK en el
  `<parent>` de su POM: <https://repo1.maven.org/maven2/org/alfresco/alfresco-java-sdk/7.3.3/alfresco-java-sdk-7.3.3.pom>.
- Los artefactos 7.3.x están en **Maven Central**, no hace falta declarar repositorios extra. Si quisieras
  versiones de pre-release (`7.3.x-A.n`) agrega `https://artifacts.alfresco.com/nexus/content/groups/public` en `<repositories>`.

> 🌱 **Spring Boot — `spring-boot-starter-parent`.** Heredar de este POM te da versiones probadas de cientos de
> librerías (por eso `spring-boot-starter-test` no lleva `<version>`), la configuración del compilador según
> `java.version` y el plugin para empaquetar un *fat jar* ejecutable.

## 3.3 La clase principal

```java
package com.curso.alfresco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;

// El starter REST arrastra Spring Security; lo excluimos para no proteger una app que no expone endpoints.
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class OopExtensionApplication {

    public static void main(String[] args) {
        SpringApplication.run(OopExtensionApplication.class, args);
    }
}
```

> 🌱 **Spring Boot — `@SpringBootApplication`.** Combina tres cosas: marca la clase como configuración, activa la
> autoconfiguración (los starters) y activa el **component scan**: Spring busca clases anotadas con
> `@Component`, `@Service`, `@Configuration`… en este paquete **y sus subpaquetes** y las instancia.
> Por eso todos tus handlers deben estar bajo `com.curso.alfresco`.

## 3.4 `application.properties`

```properties
spring.application.name=acs-oop-extension

# --- Event API: conexión al broker ActiveMQ que usa Alfresco ---
spring.activemq.broker-url=tcp://localhost:61616
# ACS 26.2 trae ActiveMQ 6 con autenticación obligatoria
spring.activemq.user=admin
spring.activemq.password=admin
# Necesario para que Spring Boot cree el ActiveMQConnectionFactory que espera el SDK
spring.jms.cache.enabled=false
# Topic donde el repositorio publica los eventos (valor por defecto)
alfresco.events.topic-name=alfresco.repo.event2
alfresco.events.enable-handlers=true
alfresco.events.enable-spring-integration=true

# --- REST API: conexión al repositorio ---
content.service.url=http://localhost:8080
content.service.path=/alfresco/api/-default-/public/alfresco/versions/1
content.service.security.basicAuth.username=admin
content.service.security.basicAuth.password=admin

logging.level.com.curso.alfresco=DEBUG
```

| Propiedad | Significado |
|-----------|-------------|
| `spring.activemq.broker-url` | Dónde está ActiveMQ |
| `spring.activemq.user` / `.password` | Credenciales del broker. En ACS 26.2 son **obligatorias** (las del contenedor: `ACTIVEMQ_ADMIN_LOGIN` / `ACTIVEMQ_ADMIN_PASSWORD`, por defecto `admin`/`admin`) |
| `spring.jms.cache.enabled=false` | Hace que Spring Boot exponga un `ActiveMQConnectionFactory` "puro", que es el tipo que inyecta el SDK |
| `alfresco.events.topic-name` | Topic a escuchar. Solo cambia si usas el Event Gateway o un topic propio |
| `alfresco.events.enable-handlers` | Activa el despacho a clases `On...EventHandler` |
| `alfresco.events.enable-spring-integration` | Activa el canal de Spring Integration (lección 8) |
| `content.service.url` / `.path` | Base de la REST API |
| `content.service.security.basicAuth.*` | Credenciales Basic. Para OAuth2 ver el extra de la [lección 6](06-rest-api.md#extra--autenticación-oauth2-con-keycloak) |

> 🌱 **Spring Boot — *relaxed binding*.** `alfresco.events.topic-name`, `alfresco.events.topicName` y la
> variable de entorno `ALFRESCO_EVENTS_TOPICNAME` son equivalentes. Esto es lo que permite configurar la app en
> Docker sólo con variables de entorno (lección 12).

## 3.5 Compilar y arrancar

```bash
mvn clean package          # compila, ejecuta tests y genera target/acs-oop-extension-1.0.0-SNAPSHOT.jar
mvn spring-boot:run        # arranca la app
```

Aún no hay handlers, pero la app debe arrancar sin errores y quedar escuchando. Comprueba en la consola de
ActiveMQ que el topic `alfresco.repo.event2` tiene ahora **un consumidor más**.

### Errores típicos

| Síntoma | Causa |
|---------|-------|
| `Connection refused: localhost/127.0.0.1:61616` | ActiveMQ no publicado al host o URL incorrecta |
| `Setup of JMS message listener invoker failed ... Cause: Stopped` y luego `Could not refresh JMS Connection` cada 5 s | Faltan `spring.activemq.user/password`. El log del broker lo confirma: `User name [null] or password is invalid` |
| `APPLICATION FAILED TO START` … `Parameter 0 of method acsEventsListeningFlow … required a bean of type 'org.apache.activemq.ActiveMQConnectionFactory' that could not be found` | Falta `spring.jms.cache.enabled=false` (o vale `true`): Spring Boot envuelve la fábrica en un `CachingConnectionFactory` y el SDK no la encuentra |
| Con Spring Boot 3.x: no compila `org.springframework.boot.security.autoconfigure` y, si se corrige el import, `BeanDefinitionOverrideException: … 'restClientSsl' … already been defined` | Versión de Spring Boot distinta a la del SDK (se mezclan jars de Boot 3 y Boot 4). Usa la misma que el SDK |
| Página de login / 401 en la app | No se excluyó `SecurityAutoConfiguration` |

Siguiente: [Lección 4 — Tu primer event handler](04-primer-handler.md)
