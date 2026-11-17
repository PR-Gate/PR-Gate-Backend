# Comprobar SonarQube → CI/CD

Esta prueba comprueba entrega y almacenamiento del webhook de SonarQube.
No activa todavía SonarQube automáticamente después de un push. Los pushes
recibidos y los análisis sin datos de correlación siguen siendo evidencias
separadas; un HTTP 202 no demuestra que se haya evaluado un push específico.

1. Reconstruir CI/CD desde la raíz del backend:

```sh
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml up -d --build cicd
```

2. Reiniciar SOLO el proceso Node del proxy (Ctrl+C y ejecutar otra vez):

```sh
node cicd-service/scripts/github-webhook-proxy.mjs
```

Conservar abierto el túnel Cloudflare: no es necesario reiniciarlo para cambiar
el código del proxy. Si se reinicia el túnel y cambia su dirección, actualizar
tanto la URL GitHub como la de SonarQube.

3. En SonarQube: Administration → Configuration → Webhooks → Create.
   Name: PRGate CI/CD.
   URL: https://DOMINIO_ACTUAL_DEL_TUNEL/api/v1/webhooks/sonar
   Secret: mismo SONAR_WEBHOOK_SECRET configurado personalmente en CI/CD.
   No usar el GITHUB_WEBHOOK_SECRET ni el SONAR_TOKEN en ese campo.

4. En Swagger de CI/CD, usar POST /api/v1/pilot/file-analyses con un .py.
   Ese endpoint ejecuta realmente el scanner y SonarQube procesa el análisis.

5. En SonarQube, revisar la entrega del webhook. Debe dar HTTP 202.
   Si la interfaz permite ver la respuesta, mostrará SONAR_RECEIVED_UNCORRELATED
   para esta prueba de archivo, con projectKey y taskId.

6. Comprobar evidencia en PostgreSQL sin introducir credenciales en el comando:

```sh
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml exec postgres psql -U prgate_cicd -d prgate_cicd -c "SELECT received_at, project_key, task_id, status FROM cicd.sonar_webhook_receipts ORDER BY received_at DESC LIMIT 5;"
```

Los webhooks correlacionados con un workflow existente siguen en la bandeja de
procesamiento y reciben SONAR_QUEUED. El worker valida tarea, proyecto, rama,
commit e intento antes de asociarlos. El registro nuevo conserva las recepciones
firmadas incluso cuando esa correlación aún no existe. Un duplicado exacto no
crea otra fila. La tarea y el Quality Gate recibidos sin correlación no se
presentan como una ejecución de pipeline confirmada.
