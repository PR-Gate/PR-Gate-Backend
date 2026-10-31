# Autorización de consultas CI/CD

Las consultas HTTP requieren una identidad resuelta por el servidor a un UUID
local de usuario PRGate. Ese UUID no se toma de headers X-User-Id, parámetros,
body ni JWT decodificados sin validación. Sin principal autenticado se responde
401; un principal no resuelto a UUID recibe 403. El adaptador de autenticación
real (por ejemplo validación Cognito y resolución sub → UUID) sigue pendiente.
El código existente en feat-projects usa un perfil local explícito y un perfil
Cognito; no se ha habilitado automáticamente ese modo local en CI/CD.

ProjectEvaluationAccessPort solicita la autorización a Projects, propietario de
membresías y permisos docentes. Sin adaptador disponible, toda decisión se deniega.
No se ha inventado un endpoint de Projects ni una réplica local de sus grupos.
Antes de habilitar usuarios deben confirmarse autenticación y contrato de acceso
con Projects (incluyendo usuarios inactivos, bajas y cambios de grupo).

El detalle inaccesible se presenta como 404; listados inaccesibles devuelven lista
vacía, sin consultar las ejecuciones. Los registros históricos sin referencia
académica no se exponen. La autorización considera el UUID del proyecto almacenado
en la ejecución, evitando trasladar permisos entre proyectos por una futura
reasignación del repositorio. Los roles globales o un UUID conocido no dan acceso.

Los webhooks conservan su autenticación por HMAC y los puertos internos del piloto
siguen disponibles solo bajo el perfil local-pilot. Swagger documenta la API pero
no autentica al usuario automáticamente. La identidad verificable y la decisión
real de Projects son requisitos pendientes para habilitar consultas de usuarios;
esta implementación bloquea el acceso por defecto mientras se integran.
