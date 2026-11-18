# Rúbricas, Quality Gates y estimación de IA

## Responsabilidades

- PRGate registra una rúbrica cuyo máximo suma **20 puntos** entre sus criterios.
- Métricas conserva versiones aprobadas y su evidencia por repositorio, rama,
  revisión e intento. El porcentaje de cumplimiento no es un puntaje estimado.
- RabbitMQ lleva las condiciones aprobadas a CI/CD y devuelve su confirmación.
- CI/CD crea un gate `prgate-<configurationId>` y lo asigna al proyecto técnico.
  Nunca modifica `Sonar way` ni el gate por defecto de toda la instancia.
- IA propone puntos por criterio, respetando su máximo. El backend suma esos
  puntos; no convierte el porcentaje técnico en una nota. `officialGrade=false`.

## Configuración privada (completar manualmente)

En Métricas:

```properties
SONAR_TOKEN=<token con Browse para recoger evidencia>
METRICS_API_TOKEN=<clave privada del backend que llama a Métricas>
METRICS_PROJECTS_TOKEN=<clave privada compartida con Projects>
AI_SERVICE_TOKEN=<mismo INTERNAL_SERVICE_TOKEN del MS de IA>
```

En Projects: `METRICS_PROJECTS_TOKEN`, con el mismo valor configurado en Métricas.
En CI/CD: `MESSAGING_ENABLED=true` y credenciales de RabbitMQ. Su `SONAR_TOKEN`
necesita administrar Quality Gates y asignarlos al proyecto. No es el webhook
secret ni un token que se entrega al alumno.

Los Compose proporcionan las URLs locales por `host.docker.internal`. Primero
levantar RabbitMQ y SonarQube; después actualizar Projects, Métricas, CI/CD e IA
con sus respectivos Compose. No ejecutar `down -v`. Las nuevas migraciones se
aplican al actualizar los servicios.

## API interna

Base: `/internal/v1/projects/{projectId}` en Métricas (puerto 8084).
Requiere `Authorization: Bearer <METRICS_API_TOKEN>` y `X-Actor-Id: <UUID local>`.
Ese UUID debe resolverlo un backend autenticado, nunca tomarse de un campo del
formulario. No entregar el token de servicio al frontend. La conexión del BFF
con esta API y la resolución Keycloak → usuario local son integración aparte.

Projects comprueba el usuario activo. Solo el docente responsable administra;
los integrantes activos de ese proyecto y su docente pueden consultar.
No basta estar en el mismo curso ni conocer el UUID de una evaluación. Si
Projects no está disponible, la solicitud se rechaza sin dar acceso por defecto.

### Publicar rúbrica

`POST /rubrics` (solo docente):

```json
{
  "title": "Calidad técnica",
  "criteria": [
    {"id":"11111111-1111-4111-8111-111111111111","description":"Pruebas","objective":"Verificar cobertura","maxPoints":12},
    {"id":"22222222-2222-4222-8222-222222222222","description":"Duplicación","objective":"Reducir código repetido","maxPoints":8}
  ]
}
```

Los IDs del ejemplo son ficticios. Los puntos deben sumar 20. Para otra versión:
`POST /rubrics/{rubricId}/versions`. Las versiones anteriores son inmutables.

### Aprobar condiciones

`POST /configurations` (solo docente):

```json
{
  "rubricId":"<UUID devuelto>",
  "rubricVersion":1,
  "repositoryId":123,
  "gitRef":"refs/heads/main",
  "planId":"<UUID de referencia del plan técnico>",
  "checks":[
    {"criterionId":"11111111-1111-4111-8111-111111111111","type":"METRIC","key":"coverage","operator":"GTE","threshold":80},
    {"criterionId":"22222222-2222-4222-8222-222222222222","type":"METRIC","key":"duplicated_lines_density","operator":"LTE","threshold":3}
  ]
}
```

Umbrales de ejemplo, no impuestos por el sistema. `planId` es una referencia de
trazabilidad: no importa ni aprueba automáticamente un plan del MS de IA.
La aprobación exige cubrir todos los criterios y una organización vinculada en
Projects. Los umbrales se sincronizan, pero los pesos no son condiciones Sonar.

Las condiciones métricas soportan GTE/LTE: por ejemplo, pasar con cobertura
`>=80` se traduce a fallar con `<80` en SonarQube. EQ/GT/LT y umbrales distintos
para la misma métrica se rechazan; no se aproximan ni redondean silenciosamente.
MANUAL representa revisión del docente. RULE valida la existencia de la regla,
pero no pasa automáticamente sin evidencia de ejecución; no crea condiciones
de Quality Gate por sí misma ni modifica perfiles de calidad.

`GET /configurations/{id}/quality-gate` devuelve PENDING, DELIVERED, APPLIED,
FAILED o SUPERSEDED. Esperar **APPLIED antes del siguiente push de prueba**.
Una política nueva no modifica el resultado histórico de un análisis pasado.
`POST /configurations/{id}/quality-gate/retry` reintenta una configuración activa
con otra versión de solicitud, sin cambiar la rúbrica. Solo el docente puede hacerlo.

Los mensajes viajan por `prgate.commands` con clave
`metrics.quality-gate.requested.v1`; CI/CD confirma por `prgate.events` con clave
`cicd.quality-gate.configured.v1`. Las outbox son persistentes. Las solicitudes
antiguas no sustituyen versiones nuevas recibidas fuera de orden.

CI/CD resuelve la clave Sonar desde su registro del repositorio. En el piloto
puede usar recibos independientes de push y análisis coincidentes, si tienen
una única clave Sonar. Nunca recibe esa clave desde el formulario. Si todavía
no puede resolverla, conserva la solicitud para reintento.

## Evaluación e historial

- `POST /evaluations`: `{ "configurationId":"...", "eventId":"..." }`.
  Solo docente; permite evaluar explícitamente un análisis histórico.
- `GET /evaluations?repositoryId=...&page=0&size=20`.
- `GET /evaluations/{id}` y `/evaluations/{id}/analysis`.
- `POST /evaluations/{id}/analysis/retry`: reintenta evidencia pendiente o no
  disponible, solo docente; no sobrescribe evidencia ya recopilada.
- `POST /evaluations/{id}/manual-reviews` con `checkId`, `passed` y
  `justification`. Solo docente; una revisión no sobrescribe otra.
- `POST /evaluations/{id}/grade-estimate`: solicita estimación de IA (docente).
- `GET /evaluations/{id}/grade-estimate`: consulta la estimación de esa evidencia.

La evaluación automática aplica la configuración activa a análisis posteriores
a su aprobación. No reinterpreta todo el historial con nuevos criterios. Las
evaluaciones anteriores conservan su configuración; si llega evidencia pendiente,
se completa sin perder revisiones manuales.

La API devuelve `technicalCompliance`, con porcentajes de cumplimiento y máximos
por criterio. La estimación se consulta aparte: máximo 20, justificaciones,
puntaje por criterio, proveedor/modelo y fecha. Con evidencia incompleta,
`estimatedScore=null`, `status=PARTIAL`; se informa el subtotal conocido sin
normalizarlo a 20. Modificar la evidencia invalida la proyección previa para la
consulta actual, sin borrar su registro en IA. El resultado idéntico se reutiliza
para no repetir llamadas al proveedor por la misma evidencia.

## Límites de evidencia

Se recogen métricas globales: cobertura, duplicación, líneas, bugs,
vulnerabilidades, code smells, deuda en minutos y ratio de deuda. No se mezclan
con `new_*`. Se verifica tarea/proyecto/ID/revisión y se enlaza la historia por
fecha. Si hay dos análisis en el mismo segundo, falta historia o falta un valor,
se conserva como no evaluable. Nunca se utiliza el estado actual de otra revisión
para completar un resultado histórico.

Los eventos del piloto conservan los IDs declarados de Actions; todavía no se
contrastan por la API de GitHub. La evaluación es por repositorio/grupo, no una
atribución de puntaje individual basada en el autor del commit.
