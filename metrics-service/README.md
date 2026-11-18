# Metrics service

Puerto 8084. PostgreSQL propio: base `prgate_metrics`, esquema `metrics` y volumen
independiente. No consulta tablas de otros microservicios.

## RabbitMQ local compartido

Desde la raíz del backend, en Git Bash:

```bash
export RABBITMQ_USERNAME=prgate
read -s -p "Clave de RabbitMQ: " RABBITMQ_PASSWORD; echo
export RABBITMQ_PASSWORD
docker compose -f compose.rabbitmq.yml up -d
```

Panel: http://localhost:15672. AMQP: puerto 5672, virtual host `prgate`.
Los usuarios iniciales se crean al inicializar el volumen. Cambiar las variables
después no cambia la clave del usuario ya creado. No eliminar volúmenes para
actualizar credenciales.

## Métricas en Docker

Copiar `.env.example` a `.env` y completar `DB_PASSWORD` y la misma clave de
RabbitMQ. Desde la raíz del backend:

```bash
docker compose --env-file metrics-service/.env -p prgate-metrics -f metrics-service/compose.yaml up -d --build
```

El Compose fija el hostname del broker en `host.docker.internal` para comunicarse
con el puerto publicado por el Compose compartido. La base no publica puertos.
Salud: http://localhost:8084/actuator/health. El proceso Java no carga `.env`
directamente; Docker Compose le entrega las variables.

## Contrato de entrada

- Exchange durable tipo topic: `prgate.events`.
- Routing key: `cicd.analysis.completed.v1`.
- Cola durable: `metrics.analysis.completed.v1`.
- Cola de errores: `metrics.analysis.completed.v1.dead`.
- JSON de ejemplo: `docs/analysis-completed-v1.json` (datos ficticios).

CI/CD será el productor. Debe verificar tarea, análisis, repositorio, revisión y
ejecución antes de publicar, conservar un `eventId` estable en los reintentos y
enviar mensajes persistentes con confirmaciones del broker. La publicación
mediante outbox se implementa en CI/CD y se habilita con `MESSAGING_ENABLED=true`.
No publicar desde el navegador ni dar
credenciales de RabbitMQ a los alumnos.

El consumidor guarda un snapshot inmutable antes de confirmar el mensaje.
Reentregar el mismo ID y contenido no genera otro registro. Reutilizar ese ID
con otro contenido se rechaza. Tras tres intentos fallidos, el listener rechaza
el mensaje sin reencolarlo y el broker lo envía a la cola de errores. La
deduplicación se aplica por `eventId`, no por SHA: dos ejecuciones pueden analizar
la misma revisión y deben conservar su identidad.

Las medidas omitidas son desconocidas, nunca cero. Las medidas deben corresponder
al análisis indicado: no adjuntar las medidas actuales de otra revisión. El
contrato inicial admite medidas globales; las de código nuevo no se confunden con
las globales. `sonarAnalysisId` no es lo mismo que `sonarTaskId`.

## Alcance actual

Persiste evidencia por repositorio/revisión, recoge medidas históricas globales,
versiona rúbricas de 20 puntos y calcula cumplimiento técnico. Las condiciones
aprobadas se sincronizan con SonarQube a través de CI/CD y RabbitMQ. Projects
resuelve permisos de docente e integrantes. Las consultas están en una API interna
protegida para el backend; no acepta el JWT del navegador directamente.

La estimación de puntaje pertenece al MS de IA y se mantiene separada del
cumplimiento. Es orientativa, no una nota oficial. Los valores sin evidencia no se
convierten en cero ni en aprobado. Los checks de reglas sin prueba de ejecución
quedan no evaluables; no se infiere su aprobación por ausencia de issues.

Configuración, endpoints y ejemplo: [rúbricas y Quality Gates](docs/rubrics-quality-gates.md).
