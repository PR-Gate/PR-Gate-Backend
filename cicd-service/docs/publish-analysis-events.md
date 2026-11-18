# CI/CD → RabbitMQ → Metrics

CI/CD puede exportar los recibos correlacionados del piloto mediante una outbox.
No cambia el contrato de los endpoints ni conecta las bases entre servicios.

## Habilitar

Levantar primero el RabbitMQ compartido y Métricas. En la configuración privada
de CI/CD agregar estas variables (no subir sus valores a Git):

```properties
MESSAGING_ENABLED=true
RABBITMQ_USERNAME=prgate
RABBITMQ_PASSWORD=<misma clave del broker>
```

Recrear CI/CD desde la raíz:

```bash
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml up -d --build cicd
```

No hace falta repetir el push. Al habilitarlo, los recibos `SUCCESS` ya guardados
y correlacionados por la vista V8 también se procesan. Las pruebas manuales de
archivo sin un push coincidente no se exportan.

## Entrega

1. El exportador consulta la tarea en SonarQube y obtiene su ID de análisis.
2. Busca ese análisis y verifica su revisión. Consulta el Quality Gate por ID de
   análisis, no por el estado más reciente del proyecto.
3. Guarda el evento con identidad estable y marca el recibo exportado en una
   misma transacción PostgreSQL.
4. El publicador envía un JSON persistente a `prgate.events` con routing key
   `cicd.analysis.completed.v1`, y espera confirmación y ausencia de devolución.
5. Métricas confirma su entrega después de guardar en su propia base. Si un
   reinicio provoca una reentrega, su deduplicación evita otro snapshot.

La outbox reintenta cada 30 segundos si falla la publicación; no marca enviado
un mensaje sin cola destinataria. Usa una reserva temporal para reducir envíos
simultáneos entre instancias. La entrega es al menos una vez, no exactamente una.
La preparación del evento hace hasta 20 intentos; los fallos se conservan con el
tipo de error y pueden revisarse en `cicd.analysis_export_receipts`. La búsqueda
de revisión examina hasta 1.000 análisis; si ya no está disponible no publica.

## Verificación administrativa

```sql
-- En la base propia de CI/CD:
SELECT event_id, published_at, attempts, last_error FROM cicd.analysis_event_outbox;
SELECT source_hash, exported_event_id, attempts, last_error FROM cicd.analysis_export_receipts;

-- En la base propia de Métricas, en otra conexión:
SELECT event_id, repository_id, commit_sha, sonar_analysis_id
FROM metrics.analysis_snapshots;
```

El contrato y el consumidor viven en `metrics-service`. Esta versión envía un
mapa de medidas vacío: evita asociar las medidas actuales de otra revisión al
resultado histórico. Las notas, las reglas detalladas, el contexto académico y
los permisos siguen pendientes. La identidad de ejecución de Actions todavía
proviene de las propiedades del scanner; no se verifica por la API de GitHub.
Estos eventos son evidencia técnica y no autorizan una calificación académica.
