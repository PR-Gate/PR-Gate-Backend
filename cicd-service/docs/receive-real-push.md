# Recibir un push real sin Projects

Esta etapa recibe y registra metadatos firmados del evento push en
cicd.github_push_receipts. No exige proyecto académico ni registro previo del
repositorio. No ejecuta todavía el scanner: la respuesta dice analysisStarted=false.
Un evento repetido con el mismo X-GitHub-Delivery no crea otra fila.
No se cuentan líneas ni se decide elegibilidad a partir de un payload incompleto:
commitsInPayload es solo el número de commits incluidos, no el total garantizado
ni una medida de líneas/archivos modificados.

1. Configurar personalmente GITHUB_WEBHOOK_SECRET en cicd-service/.env.
   Es un secreto distinto del webhook de SonarQube.
2. Reconstruir y arrancar CI/CD (desde la raíz del backend):

```sh
docker compose --env-file cicd-service/.env -p prgate-cicd -f cicd-service/compose.yaml up -d --build cicd
```

3. En otra terminal, iniciar el proxy local que expone solo el webhook:

```sh
node cicd-service/scripts/github-webhook-proxy.mjs
```

4. En otra terminal, abrir un túnel temporal con la imagen oficial Cloudflare:

```sh
docker run --rm cloudflare/cloudflared:latest tunnel --no-autoupdate --url http://host.docker.internal:8095
```

Copiar la URL HTTPS trycloudflare.com que imprime el proceso. Es temporal;
mantener abiertas las dos terminales durante la prueba. Documentación:
https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

5. En la configuración de la GitHub App existente, activar el evento Push.
   Webhook URL: URL_HTTPS_DEL_TUNEL/api/v1/webhooks/github
   Webhook secret: el mismo GITHUB_WEBHOOK_SECRET de CI/CD.
   Mantener SSL verification habilitado. Si ya había un webhook de la App,
   coordinar el cambio con su administrador: afecta las entregas de esa App.
   Esta URL no es el callback OAuth de vinculación de cuenta; no modificarlo.
6. Hacer un push pequeño a uno de los repositorios autorizados. Esta recepción
   no ejecuta SonarQube, por lo que todavía no aplica umbrales ni exclusiones MD.
7. En GitHub App > Advanced > Recent deliveries, abrir la entrega push:
   debe responder HTTP 202 con PUSH_RECEIVED, repository, ref, before y after.
   DUPLICATE_PUSH indica que la misma entrega ya se guardó.

Con esto solo queda comprobada la recepción. El examen de cambios mediante Git
o la API y el disparo del análisis se implementan a continuación. Un HTTP 202
no equivale a un análisis Sonar completado.
