# IA Service: arquitectura hexagonal y DDD

Estado: capa domain implementada; sin endpoints ni adaptadores implementados.
Stack: Java 21, Spring Boot 3.4.4. Paquete: com.prgate.ai.

## Limite del servicio

IA Service constituye un unico contexto para las capacidades de asistencia
inteligente de la tesis. Rubricas, analisis y refuerzo son conceptos y casos de
uso internos, no bounded contexts independientes. No se crean submodulos rubric,
assessment o learning. La estructura de carpetas sigue las capas hexagonales.

## Estructura

    com.prgate.ai
    |-- AiServiceApplication.java
    |-- domain
    |   |-- model
    |   |-- policy
    |   +-- repository
    |-- application
    |   |-- port/in
    |   |-- port/out
    |   |-- service
    |   +-- dto
    |-- infrastructure
    |   |-- inbound/rest
    |   |-- inbound/jobs
    |   +-- outbound
    |       |-- openai
    |       |-- sonarqube
    |       |-- scanner
    |       +-- persistence
    +-- bootstrap

La capa domain contiene agregados y objetos de valor. Ver [modelo de dominio](domain.md). Los paquetes de otras capas conservan package-info.java.
Los componentes siguientes se implementaran cuando se aborde cada flujo.

## Responsabilidades y dependencias

- domain: modelos, objetos de valor, invariantes y contratos de repositorio.
- application: casos de uso, puertos de entrada/salida y DTOs propios.
- infrastructure: REST, trabajos asincronos, OpenAI, SonarQube y persistencia.
- bootstrap: construccion e inyeccion de dependencias con Spring.

Dominio sin Spring, JPA o SDKs. Aplicacion depende del dominio y de sus propios
puertos; no depende de infraestructura. Adaptadores implementan los puertos.
Entidades JPA y tipos de proveedores no atraviesan la frontera del adaptador.

## Casos de uso previstos

- TranslateRubricService: convertir rubrica en un plan tecnico verificable.
- GenerateInitialTestService: generar un test inicial a partir de objetivos.
- AnalyzeSubmissionService: coordinar el analisis de una entrega.
- GenerateFeedbackService: explicar hallazgos con lenguaje pedagogico.
- GenerateReinforcementTestService: generar un test sobre objetivos pendientes.

Puertos de salida: RubricTranslationPort, AnalysisCatalogPort, CodeAnalysisPort,
FeedbackGenerationPort y LearningTestGenerationPort. Cada puerto usa contratos
del servicio; OpenAI y SonarQube son implementaciones externas.

## Flujo funcional

Rubrica -> propuesta IA -> validacion del plan -> analisis SonarQube de la
entrega -> hallazgos verificados -> feedback IA -> test de refuerzo.
El test inicial es opcional y precede a la entrega. Test significa cuestionario
o ejercicio pedagogico; generar pruebas unitarias ejecutables seria otro caso.

## Integracion SonarQube

La propuesta IA se valida contra reglas y parametros del catalogo instalado.
Un listado libre no se convierte automaticamente en reglas ejecutables.
Clasificar cada criterio como regla Sonar, metrica, comprobacion externa o
revision manual. No inventar identificadores ni dar por aprobados criterios
sin cobertura automatica. La importacion externa recibe hallazgos ya detectados;
no implementa nuevas reglas. Versionar planes y evitar modificar perfiles
compartidos durante analisis concurrentes.

Referencia: https://docs.sonarsource.com/sonarqube-server/quality-standards-administration/managing-quality-profiles/editing-a-custom-quality-profile
Referencia: https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/importing-external-issues/generic-issue-import-format

## Reglas y trazabilidad

Vincular cada evaluacion a alumno, entrega/revision, rubrica y plan versionados.
El feedback referencia evidencia real y el test referencia objetivos concretos.
Un analisis fallido no significa ausencia de errores. Las soluciones y pautas
no se incluyen en la respuesta del estudiante. Validar estructura y semantica
del resultado IA antes de aceptarlo.

Persistir trabajos asincronos con correlacion e idempotencia. Ejecutar scanner y
herramientas sobre codigo estudiantil en un worker aislado del backend y sus
secretos. No mantener transacciones abiertas durante llamadas externas.
Estados propuestos: PENDING, ANALYZING, ANALYZED, GENERATING_FEEDBACK,
GENERATING_TEST, COMPLETED y FAILED; conservar la etapa fallida para reintentos.

## OpenAI y configuracion prevista

Adaptador Responses API con SDK oficial Java y modelo configurable.
Referencia: https://developers.openai.com/api/docs/guides/text
Configurar claves exclusivamente en servidor, timeout, limites y reintentos
acotados. Versionar prompts y esquemas. Tratar codigo y rubricas como datos,
separados de las instrucciones. Registrar modelo, version, latencia y tokens
sin guardar contenido estudiantil en logs por defecto.

## Implementacion pendiente

Confirmar lenguaje de entregas, version de SonarQube y rubrica de ejemplo.
Implementar primero traduccion validada, luego analisis asincrono, feedback y
test. Verificar reglas de dominio, puertos con dobles, contratos de adaptadores,
idempotencia y exposicion de soluciones. Evaluar calidad con docentes y medir
aprendizaje mediante resultados; generar tests no demuestra mejora por si solo.