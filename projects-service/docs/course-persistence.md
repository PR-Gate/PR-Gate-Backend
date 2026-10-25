# Persistencia de cursos

## Componentes

- `V1__create_courses.sql` crea la tabla `courses` mediante Flyway.
- `CourseJpaEntity` representa las columnas de PostgreSQL y permanece fuera del
  dominio.
- `CoursePersistenceMapper` transforma el agregado en su representación JPA.
- `SpringDataCourseRepository` encapsula el acceso Spring Data.
- `JpaCourseRepositoryAdapter` implementa el puerto `CourseRepository`. Guarda
  dentro de una transacción y propaga los fallos de persistencia.

Se mantienen UUID, nombre, sección, periodo académico, UUID del docente y fecha
de creación. `teacher_id` referencia al usuario de otro servicio; no existe una
clave foránea hacia la base de Usuarios.

El esquema se gestiona exclusivamente con Flyway. Hibernate utiliza
`ddl-auto: validate`, sin crear ni actualizar tablas automáticamente. Una vez
aplicada V1 en un entorno compartido, los cambios deben introducirse en nuevas
migraciones.

## Configuración de la aplicación

`application.yml` utiliza estas variables de entorno:

| Variable | Contenido |
| --- | --- |
| `DB_URL` | URL JDBC; por defecto `jdbc:postgresql://localhost:5432/prgate_projects`. |
| `DB_USERNAME` | Usuario PostgreSQL, obligatorio. |
| `DB_PASSWORD` | Contraseña PostgreSQL, obligatoria. |

La base indicada debe existir antes de arrancar la aplicación; Flyway crea las
tablas, no la base de datos. No se guardan credenciales en el repositorio.

## Verificación

Desde la carpeta `projects-service`:

```powershell
mvn test
mvn verify
```

`test` ejecuta pruebas unitarias y de arquitectura. `verify` ejecuta además
`JpaCourseRepositoryIT` mediante Failsafe. La prueba de integración requiere
Docker activo y crea un contenedor PostgreSQL temporal; no usa la base local ni
necesita configurar las variables anteriores. El contenedor usa la línea
`postgres:17-alpine`; su parche puede cambiar al obtener nuevamente esa etiqueta.

La integración comprueba migración desde una base vacía, validación del esquema
JPA, guardado confirmado mediante consulta SQL independiente, rechazo de nombres
vacíos y que Flyway no repita una migración aplicada. No se omite automáticamente
si Docker no está disponible.

## Alcance pendiente

Todavía faltan el adaptador HTTP de Usuarios, la composición del caso de uso y
el controller con seguridad. La persistencia no convierte por sí sola el caso de
uso de creación en un endpoint ejecutable.

No se añade unicidad por nombre, sección y periodo: aún no se ha definido esa
regla de negocio. Las tablas de proyectos, inscripciones y participaciones
corresponden a siguientes incrementos.
