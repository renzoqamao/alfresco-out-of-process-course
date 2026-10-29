# Lección 2 — Preparar el entorno

## Herramientas

| Herramienta | Versión mínima | Comprobar |
|-------------|----------------|-----------|
| JDK | 17 | `java -version` |
| Maven | 3.6 (recomendado 3.9) | `mvn -v` |
| Docker + Docker Compose | reciente | `docker compose version` |
| IDE | IntelliJ IDEA, VS Code (Extension Pack for Java) o Eclipse | — |

> ☕ **Java — JDK vs JRE.** Para *compilar* necesitas el **JDK** (incluye `javac`). Asegúrate de que
> `JAVA_HOME` apunte al JDK 17+ y de que `mvn -v` muestre esa misma versión: Maven usa `JAVA_HOME`, no el `java` del `PATH`.

## Alfresco Content Services 26.2

Puedes usar cualquier despliegue de ACS 26.2 (Enterprise o Community). La forma más sencilla es el
repositorio oficial [acs-deployment](https://github.com/Alfresco/acs-deployment) con Docker Compose:

```bash
git clone https://github.com/Alfresco/acs-deployment.git
cd acs-deployment/docker-compose
docker compose -f community-compose.yaml up -d   # o compose.yaml para Enterprise (requiere acceso a quay.io y licencia)
```

> ⚠️ **Enterprise necesita una licencia válida.** Sin licencia (o con una vencida) el repositorio arranca en
> **modo solo lectura**: la REST API responde `403 ... The system is currently in read-only mode`, no se pueden
> crear documentos y, por tanto, **no se generan eventos**. Revisa el log con
> `docker logs <contenedor-alfresco> | grep -i license`.

### Lo que la extensión necesita del entorno

| Recurso | URL por defecto | Para qué |
|---------|-----------------|----------|
| REST API | `http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1` | Actuar sobre el repositorio |
| ActiveMQ (OpenWire) | `tcp://localhost:61616` | Recibir eventos |
| Consola ActiveMQ | `http://localhost:8161` (admin/admin) | Ver el topic y los consumidores |

> 🔐 En ACS 26.2 el broker (imagen `alfresco/alfresco-activemq:6.x`) **exige autenticación** también en el
> puerto 61616. Las credenciales son las variables `ACTIVEMQ_ADMIN_LOGIN` / `ACTIVEMQ_ADMIN_PASSWORD` del
> contenedor (por defecto `admin`/`admin`); el propio repositorio se conecta con `messaging.broker.username/password`.
> Compruébalo con `docker inspect <contenedor-activemq> --format '{{range .Config.Env}}{{println .}}{{end}}'`.

El puerto **61616 debe estar publicado** al host. En los compose oficiales ya lo está; si usas otro, revisa
que el servicio `activemq` tenga `ports: - 61616:61616`.

### Verificación

```bash
# 1. El repositorio responde y está en modo escritura
curl -u admin:admin http://localhost:8080/alfresco/api/-default-/public/alfresco/versions/1/nodes/-root-

# 2. ActiveMQ acepta conexiones (PowerShell)
Test-NetConnection localhost -Port 61616
```

En la consola de ActiveMQ (`http://localhost:8161` → *Topics*) deberías ver el topic **`alfresco.repo.event2`**.
Cuando conectes tu extensión, la columna *Number Of Consumers* subirá a 1 (o más).

> 💡 Ese topic es la "fuente de verdad" de este curso. Si algún día no recibes eventos, lo primero es mirar
> aquí si los mensajes se están encolando (*Messages Enqueued*) y si tu aplicación figura como consumidor.

Siguiente: [Lección 3 — Crear el proyecto](03-crear-proyecto.md)
