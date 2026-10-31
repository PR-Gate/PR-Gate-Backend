# Feedback con OpenAI

`OpenAiFeedbackAdapter` implementa el puerto `FeedbackGenerator` usando Responses
API y JSON Schema estricto. No requiere dependencias adicionales al starter web.

Configura `OPENAI_API_KEY` y `OPENAI_MODEL` en el entorno del proceso Java.
El modelo seleccionado debe admitir Structured Outputs en Responses API.
Spring Boot no carga `.env` automáticamente; configura el IDE para cargarlo.

El servicio de aplicación puede inyectar `FeedbackGenerator` y usarlo así,
con el plan correspondiente al assessment:

```java
// assessment debe estar en ANALYZED y plan debe ser su plan aprobado.
var request = new FeedbackRequest(plan.criteria(), assessment.findings());
var result = feedbackGenerator.generate(request);
assessment.completeFeedback(result.summary(), result.feedback());
// Guardar assessment mediante AssessmentRepository.
```

Los identificadores devueltos se validan contra los criterios y hallazgos de la
solicitud. La llamada no modifica el agregado. El caso de uso decide cuándo
guardar el resultado o registrar `assessment.fail(...)` ante un
`FeedbackGenerationException`.

La conexión tiene un límite de 10 segundos y la lectura de 60 segundos. Los
errores HTTP, respuestas incompletas, rechazos y contenido inválido se traducen
a `FeedbackGenerationException`. No se reintenta automáticamente ni se exponen
los cuerpos de error del proveedor. Se envía `store=false`.

Las pruebas usan HTTP simulado y no necesitan una clave ni consumen la API:

```powershell
cd ai-service
mvn test
```

Este paso implementa el adaptador; aún no expone un endpoint ni persiste feedback.

Referencia: https://developers.openai.com/api/docs/guides/structured-outputs
