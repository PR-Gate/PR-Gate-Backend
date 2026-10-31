# Requirements Document

## Introduction

El `users-service` debe autenticar a los clientes mediante los JWT emitidos por
AWS Cognito. El cliente inicia sesión en Cognito, obtiene un access token y lo
presenta en `POST /users`. El servicio valida el token contra el JWKS de Cognito
(firma, issuer y expiración), extrae el claim `sub` y crea el perfil local con
un UUID propio. La respuesta es únicamente el perfil creado; no se emite ni
devuelve un token nuevo.

El objetivo es eliminar la confianza en un `cognitoSub` enviado por el cliente y
vincular cada perfil a una identidad verificada por Cognito. El backend no
persiste credenciales: solo guarda el perfil y el `cognitoSub`. El cliente
mantiene el access token en memoria y lo envía en cada solicitud, manteniendo el
estilo DDD/hexagonal existente y la persistencia en memoria actual.

## Requirements

### Requirement 1: Autenticación de solicitudes con JWT de Cognito

**Objective:** Como cliente autenticado en Cognito, quiero usar mi access token
en `POST /users`, para que el servicio confíe en mi identidad sin que yo declare
mi `sub`.

#### Acceptance Criteria

1. WHEN el cliente envía `POST /users` con `Authorization: Bearer <jwt>` THEN el users-service SHALL validar la firma del token con las claves públicas del JWKS de Cognito.
2. WHEN el users-service valida un token THEN el users-service SHALL verificar que el `iss` coincida con el issuer de Cognito configurado.
3. WHEN el users-service valida un token THEN el users-service SHALL verificar que el token no esté expirado.
4. WHEN el token es válido THEN el users-service SHALL extraer el claim `sub` como identidad Cognito del solicitante.
5. IF el token es válido y el `sub` aún no tiene perfil THEN el users-service SHALL crear el perfil y responder `201 Created`.
6. IF el token es válido y el `sub` ya tiene perfil THEN el users-service SHALL rechazar la creación con un error de conflicto de estado y no crear un perfil duplicado.
7. WHEN el users-service crea el perfil THEN el users-service SHALL asignar un UUID local como identificador del perfil, independiente del `sub`.
8. WHEN el users-service responde a `POST /users` THEN el users-service SHALL devolver el perfil creado y SHALL NOT devolver un token nuevo.
9. WHEN el cliente envía campos de identidad en el cuerpo de `POST /users` THEN el users-service SHALL ignorar cualquier `cognitoSub` del cuerpo y usar exclusivamente el `sub` del token.

### Requirement 2: Validación del cuerpo de registro

**Objective:** Como usuario autenticado, quiero enviar solo mis datos de perfil,
para que el alta sea simple y consistente.

#### Acceptance Criteria

1. WHEN el token es válido y el cuerpo contiene `firstName`, `lastName` y `contactEmail` THEN el users-service SHALL crear el perfil con esos datos.
2. IF el cuerpo de la solicitud no es válido o no puede interpretarse THEN el users-service SHALL responder `400 Bad Request` con el formato de error del servicio.
3. IF falta un dato obligatorio del perfil THEN el users-service SHALL responder `400 Bad Request` y no crear el perfil.

### Requirement 3: Manejo de errores de autenticación y autorización

**Objective:** Como cliente, quiero respuestas claras cuando mi token no es
aceptable, para poder renovarlo o corregir la solicitud.

#### Acceptance Criteria

1. IF la solicitud a una ruta protegida no incluye el header `Authorization` THEN el users-service SHALL responder `401 Unauthorized`.
2. IF el header `Authorization` no usa el esquema `Bearer` THEN el users-service SHALL responder `401 Unauthorized`.
3. IF el token tiene firma inválida, issuer incorrecto o está expirado THEN el users-service SHALL responder `401 Unauthorized`.
4. IF la firma del token no puede verificarse por indisponibilidad del JWKS THEN el users-service SHALL responder `401 Unauthorized` o `5xx`, y SHALL NOT crear el perfil.
5. WHEN el users-service devuelve un error de autenticación THEN el users-service SHALL usar el formato de error común (`ApiErrorResource`) o el formato estándar de Spring Security de forma consistente.
6. WHEN la solicitud es válida y autenticada THEN el users-service SHALL permitir el acceso sin exigir roles adicionales para `POST /users`.

### Requirement 4: Configuración por entorno

**Objective:** Como operador, quiero configurar Cognito por variables de
entorno, para desplegar el servicio en distintos entornos sin recompilar.

#### Acceptance Criteria

1. WHEN el servicio arranca THEN el users-service SHALL obtener el issuer/JWKS de Cognito desde configuración externa (variables de entorno o `application-*.yml`).
2. IF la configuración de Cognito requerida está ausente o es inválida al iniciar en un perfil que la requiere THEN el users-service SHALL fallar el arranque con un mensaje claro.
3. WHERE el perfil local de desarrollo lo permita THE users-service SHALL ofrecer valores o instrucciones para probar sin Cognito real, sin debilitar la validación en entornos no locales.
4. WHEN se ejecuta en Docker Compose THEN el users-service SHALL recibir la configuración de Cognito mediante variables de entorno documentadas en `.env.example`.

### Requirement 5: Documentación OpenAPI

**Objective:** Como consumidor de la API, quiero documentación clara del esquema
de seguridad, para saber que debo enviar el access token de Cognito.

#### Acceptance Criteria

1. WHEN se consulta la especificación OpenAPI THEN el users-service SHALL declarar un esquema de seguridad bearer para las rutas de `/users` distinto del token de servicio interno.
2. WHEN se usa el botón Authorize de Swagger UI THEN el valor esperado SHALL ser el access token de Cognito.
3. WHEN se documenta `POST /users` THEN la especificación SHALL indicar que el `sub` se obtiene del token y SHALL NOT requerirlo en el cuerpo.
4. WHEN se actualiza el README o los docs THEN el users-service SHALL describir el flujo completo (login en Cognito, llamada con Bearer, respuesta con perfil sin token nuevo).

### Requirement 6: Pruebas automatizadas

**Objective:** Como equipo de desarrollo, queremos pruebas que verifiquen la
autenticación, para prevenir regresiones de seguridad.

#### Acceptance Criteria

1. WHEN se ejecutan las pruebas THEN el proyecto SHALL incluir pruebas de `POST /users` sin token, con token inválido, con token expirado y con `issuer` incorrecto.
2. WHEN se ejecutan las pruebas THEN el proyecto SHALL incluir una prueba de `POST /users` con token válido que verifique que el perfil usa el `sub` del token y un UUID local.
3. WHEN se ejecutan las pruebas THEN el proyecto SHALL incluir una prueba que verifique que un `cognitoSub` en el cuerpo no altera la identidad usada.
4. WHEN se ejecutan las pruebas THEN las pruebas existentes del contrato interno `/internal/**` SHALL seguir pasando.

### Requirement 7: Manejo seguro del token

**Objective:** Como responsable de seguridad, quiero que el access token de
Cognito nunca se persista en el backend, para reducir el impacto de una posible
filtración.

#### Acceptance Criteria

1. WHEN el users-service procesa una solicitud autenticada THEN el users-service SHALL NOT almacenar el access token, el refresh token ni el JWT completo en la base de datos.
2. WHEN el users-service procesa una solicitud autenticada THEN el users-service SHALL NOT escribir el header `Authorization` ni el token en logs.
3. WHEN el users-service persiste la identidad del usuario THEN el users-service SHALL guardar únicamente el perfil y el `cognitoSub`, nunca credenciales.
4. WHEN el cliente mantiene el access token THEN el cliente SHALL conservarlo en memoria y enviarlo como `Authorization: Bearer` en cada solicitud; el users-service SHALL asumir que no hay token persistido server-side.
5. WHERE la API se expone fuera de localhost THE users-service SHALL requerir TLS para evitar la exposición del token en tránsito.
6. WHEN el token expira THEN el users-service SHALL rechazar la solicitud con `401 Unauthorized` y el cliente SHALL renovarlo mediante Cognito.
