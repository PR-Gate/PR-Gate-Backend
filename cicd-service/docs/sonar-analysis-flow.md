# Análisis y recepción de resultados

El código académico se compila y analiza en un runner CI aislado, separado de
PRGate, SonarQube y PostgreSQL. El microservicio no ejecuta Maven ni código de alumnos.

## Recepción implementada

`POST /api/v1/webhooks/sonar` valida `X-Sonar-Webhook-HMAC-SHA256` con
`SONAR_WEBHOOK_SECRET`, guarda el cuerpo firmado en una bandeja PostgreSQL y
responde 202. Un worker procesa entradas cada diez segundos. Esto permite que el
webhook de GitHub llegue después. Cada entrada admite veinte intentos separados
por treinta segundos; agotados los intentos, queda conservada para intervención,
sin eliminación automática ni endpoint de reenvío todavía.

El worker consulta `/api/ce/task` para obtener el identificador real de análisis
y comprobar tarea exitosa/proyecto. Dentro de una transacción distinta, asocia
el análisis por ID GitHub del repositorio, ID de workflow e intento; comprueba
clave Sonar, UUID académico, rama y commit. Una clave de análisis solo puede
pertenecer a una ejecución. Una ejecución conserva un resultado inmutable;
recalcular el Quality Gate después no sobrescribe la evidencia original.

El cuerpo debe incluir `revision`, `taskId`, `analysedAt`, `project`, `qualityGate`
y estas propiedades del scanner:

- `sonar.analysis.prgateRepositoryId`
- `sonar.analysis.prgateRunId`
- `sonar.analysis.prgateRunAttempt`
- `sonar.analysis.prgateBranch`

Las métricas solo se guardan si aparecen como condiciones globales del Quality
Gate: coverage, vulnerabilities, duplicated_lines_density, sqale_debt_ratio.
Ausencia significa null, no cero. Las métricas `new_*` no se confunden con métricas
globales. Obtener todas las métricas de un análisis histórico requiere otro flujo;
no se consulta la última métrica del proyecto porque podría ser de otro commit.
Los errores de análisis y eventos sin correlación se reconocen sin asociar resultado.

## Configuración de la instancia

En SonarQube: Administration → Configuration → Webhooks, registrar una vez la URL
del receptor y el mismo secreto de `SONAR_WEBHOOK_SECRET`. Para prueba local con
ambos procesos Windows: `http://localhost:8082/api/v1/webhooks/sonar`.
La cuenta del SONAR_TOKEN debe poder consultar tareas y Browse de los proyectos.
Confirmar permisos/contrato en `/web_api` de la instancia desplegada.

## Plantilla del scanner

`templates/github-actions/java-maven-evaluation.yml.template` es una plantilla
para un repositorio Java/Maven, no un workflow instalado. CI ejecuta build y
pruebas en las ramas; Sonar Community Build se ejecuta solo por push a la rama
configurada. No se pasan parámetros comerciales de branch/pull request al scanner.

La futura GitHub App administrará la instalación de la plantilla y estas variables:
PRGATE_ANALYSIS_BRANCH, PRGATE_SONAR_URL y PRGATE_SONAR_PROJECT_KEY. El secreto
PRGATE_SONAR_ANALYSIS_TOKEN debe ser de análisis para ese proyecto, nunca el
token técnico de administración del microservicio. La automatización para generar
y distribuir esos tokens todavía está pendiente. También debe fijarse la versión
del plugin scanner Maven elegida para la instancia antes de publicar el workflow.

La URL de Sonar debe ser accesible desde el runner: localhost en GitHub-hosted
no apunta a la PC del desarrollador. No desplegar el runner de alumnos en el host
del backend. La generación de cobertura (por ejemplo JaCoCo) debe estar configurada
en el proyecto; Sonar no ejecuta pruebas ni inventa cobertura.

## Pendiente para un piloto completo

Aplicar V3/V4, configurar webhook/secreto, registrar un repositorio mediante la
integración Projects, instalar GitHub App/workflow, disponer de runner aislado
y ejecutar un análisis real. Hasta entonces la plantilla y recepción no prueban
el flujo externo completo. La adaptación de frontend depende de su build system.
