# Modelo de dominio del IA Service

Un unico contexto, con varios agregados internos. Sin dependencias de Spring,
JPA, SDKs ni anotaciones de serializacion.

## Agregados e invariantes

- TechnicalAssessmentPlan: snapshot inmutable de una version de rubrica y sus
  criterios. Todo criterio tiene comprobacion tecnica o revision manual explicita.
  El catalogo valida mecanismos, destinos, lenguaje y parametros antes de aprobar.
  Una aprobacion fallida no cambia estado; un plan aprobado no se modifica.
  Para cambiar la rubrica o sus comprobaciones se crea otro plan.
- Assessment: referencia el plan aprobado, alumno y revision exacta de entrega.
  Estados PENDING -> ANALYZING -> ANALYZED -> COMPLETED. El feedback solo usa
  hallazgos existentes del mismo criterio. FAILED conserva etapa y motivo;
  retry vuelve a PENDING si fallo el analisis, o ANALYZED si fallo el feedback.
  Un analisis completado sin hallazgos es valido; un fallo no equivale a exito.
- LearningTest: cuestionario inicial o de refuerzo para un alumno. DRAFT ->
  PUBLISHED. El inicial requiere plan aprobado; el refuerzo exige evaluacion con
  feedback completado y preguntas vinculadas a hallazgos reales del criterio.
  La publicacion no puede repetirse. No existe un bounded context learning.

Los agregados no tienen setters. Las referencias cruzadas retenidas son IDs y
snapshots, no objetos mutables de otros agregados. Las colecciones se copian y
no se pueden modificar desde fuera.

## Objetos de valor

Identidades UUID tipadas: PlanId, AssessmentId, LearningTestId, CriterionId,
FindingId y QuestionId. Igualdad por valor mediante records.

RubricReference, SubmissionReference, RubricCriterion, TechnicalCheck,
TechnicalFinding, FeedbackItem y LearningQuestion son snapshots inmutables.
Los IDs de criterio, hallazgo y pregunta permiten enlazar evidencia de forma
estable; estos snapshots no tienen repositorios ni ciclo de vida independiente.
Student y Submission se referencian; no se duplican sus modelos.

## Politicas y repositorios

TechnicalCheckCatalog es una politica de dominio sobre un snapshot del catalogo.
Su implementacion futura debe verificar destinos y valores de parametros, no
solo aceptar cualquier salida del LLM. No realiza llamadas OpenAI en el dominio.

Cada agregado tiene un contrato de repositorio propio. Las implementaciones
deberan resolver rehidratacion, idempotencia y control de concurrencia sin
permitir que las entidades JPA entren al dominio.

## Alcance y siguientes decisiones

Esta primera implementacion modela planes, hallazgos localizados en codigo,
feedback basado en hallazgos y cuestionarios. Todavia no incluye metrica como
evidencia de feedback, intentos/respuestas, puntuaciones ni progreso longitudinal.
Un refuerzo sin hallazgos necesita definir otra fuente de evidencia pedagogica;
no se inventan errores cuando el analisis no encuentra ninguno.

LearningQuestion contiene solucion y pauta interna. La capa de aplicacion debe
proyectar un DTO del estudiante que las excluya; no exponer agregados directamente.

No se agregan eventos de dominio ni una abstraccion generica de agregado sin
un consumidor o necesidad concreta. Al integrar trabajos asincronos se definira
la publicacion transaccional y la semantica de entrega de eventos.

## Verificacion

DomainModelTest cubre aprobacion atomica, cobertura, referencias de evidencia,
revision de entrega, reintentos y colecciones inmutables, sin consumir APIs.
Ejecutar con Java 21 y Maven: mvn test.