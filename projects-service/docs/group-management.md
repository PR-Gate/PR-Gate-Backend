# Gestión de grupos: liderazgo, organización y traslados

## Casos de uso

| Contrato | Entrada | Actor | Resultado |
| --- | --- | --- | --- |
| `DesignateProjectLeaderUseCase` | `projectId`, `studentId` | Docente responsable | Líder designado o reemplazado |
| `RemoveProjectLeaderUseCase` | `projectId` | Docente responsable | Grupo sin líder |
| `LinkGitHubOrganizationUseCase` | `projectId`, `organizationLogin` | Líder actual | ID y login confirmados por GitHub |
| `TransferProjectMemberUseCase` | `sourceProjectId`, `targetProjectId`, `studentId` | Docente responsable | Nueva membresía activa |

`Actor` representa la identidad autenticada local de Users y nunca debe obtenerse
del cuerpo de una petición. No se han agregado controllers ni autenticación en
este incremento de gestión de grupos. Los servicios siguen siendo Java puro; su
composición está en infraestructura. La conexión HTTP con Users se ha retirado.
Hasta implementar una proyección local con RabbitMQ, el directorio de usuarios
permanece desconectado y las operaciones que lo consultan responden `503`.
Las pruebas inyectan usuarios simulados para verificar los flujos completos.

## Liderazgo

El docente responsable debe estar activo. El candidato debe ser un alumno activo
según Users y miembro activo del grupo. La designación reemplaza al líder anterior;
repetir la misma designación es válido. Retirar el liderazgo es idempotente.
Ni el reemplazo ni la retirada eliminan la organización vinculada.

## Organización de GitHub

Se recibe un login, no una URL ni un ID proporcionado por el cliente. Se normaliza
y valida su sintaxis. El adaptador consulta `GET https://api.github.com/orgs/{login}`
según la [documentación oficial de GitHub](https://docs.github.com/en/rest/orgs/orgs?apiVersion=2022-11-28#get-an-organization).
Se guarda el ID estable y el login devuelto, comprobando que el tipo sea `Organization`.

La consulta usa tiempos de espera de conexión de 5 segundos y lectura de 10 segundos,
sin seguir redirecciones ni aceptar servidores arbitrarios. Es una consulta pública
sin token: está sujeta a los límites de GitHub. Un 404 representa ausencia; errores
de autorización, límite de peticiones, red, servidor o respuesta inválida producen
`GitHubDirectoryException` y no modifican el grupo.

Se verifica al líder antes de consultar GitHub y otra vez dentro de la transacción.
Así, un cambio de líder o traslado durante la consulta impide un registro obsoleto.
Una organización ya vinculada no se reemplaza. No se impone unicidad global entre
grupos, puesto que esa regla no ha sido acordada.

Esta validación comprueba existencia y tipo de organización. La propiedad de la
organización y la pertenencia real del alumno en GitHub no se verifican en este
incremento; requerirían definir el vínculo de identidad y permisos de GitHub.
PRGate no modifica miembros, invitaciones ni repositorios externos.

## Traslado e historial

El traslado solo se permite dentro del mismo curso, hacia un grupo diferente y
con admisión aprobada. La membresía activa debe seguir perteneciendo al grupo de
origen solicitado. La operación cierra su intervalo y crea una membresía con un
nuevo ID, usando el mismo instante para ambos extremos. Se mantienen los IDs
anteriores para cualquier referencia histórica.

Si el alumno era líder, se retira esa designación. La organización permanece
vinculada al grupo de origen. El alumno no se convierte automáticamente en líder
del destino. El cambio de organización en GitHub sigue siendo manual.

El traslado es una decisión del docente sobre un alumno ya admitido; no exige que
el alumno esté conectado ni consulta su estado actual en Users. No revoca ni
modifica su admisión. La validación del estado activo del docente sí es obligatoria.

## Transacciones y concurrencia

`ProjectUnitOfWork` es un puerto de aplicación. Su adaptador inicia una transacción,
bloquea la fila del curso y carga el proyecto después del bloqueo. Las operaciones
de gestión del mismo curso siguen ese orden para serializar traslados y cambios
de líder u organización. No se realizan consultas externas durante el bloqueo.
La transacción tiene un tiempo máximo configurado de 15 segundos.

`ProjectMembershipRepository.close` realiza una actualización condicional sobre ID,
curso, proyecto, alumno, fecha de ingreso y estado activo. Una versión obsoleta o
ya cerrada produce conflicto. El índice parcial de V4 sigue protegiendo un solo
grupo activo por alumno y curso. Cierre, retirada del liderazgo y nueva inserción
se confirman juntos; cualquier fallo revierte todos esos cambios.

Las futuras operaciones que modifiquen estos datos deben usar el mismo puerto.
`ProjectRepository.save` por sí solo no evita sobrescrituras concurrentes: no debe
usarse para futuras ediciones de grupos fuera de esta coordinación.

## Verificación

`mvn verify` ejecuta pruebas contra PostgreSQL temporal para autorizaciones,
reemplazo y retirada del líder, conservación de organización e historial,
rollback ante fallos y carreras entre traslados, designaciones y registros de
organización. El transporte GitHub se prueba con respuestas HTTP simuladas;
las pruebas no dependen de GitHub ni de Users reales.
