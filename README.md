# PRGate Backend

Monorepo de los microservicios backend de PRGate. Cada microservicio mantiene
su código, dependencias, configuración, migraciones, pruebas y documentación
dentro de su propia carpeta.

## Estructura actual

```text
PR-Gate-Backend/
├── LICENSE
├── README.md
└── projects-service/
    ├── README.md
    ├── .gitignore
    ├── pom.xml
    ├── docs/
    └── src/
        ├── main/
        └── test/
```

Los siguientes microservicios se añadirán como carpetas hermanas de
`projects-service`, por ejemplo `users-service`. No deben anidarse dentro de otro
microservicio ni crear repositorios Git independientes dentro del monorepo.

## Servicios

| Carpeta | Responsabilidad | Documentación |
| --- | --- | --- |
| `projects-service` | Cursos, admisiones, grupos/proyectos, participaciones y organizaciones vinculadas. | [README del servicio](projects-service/README.md) |

Cada servicio se construye desde su carpeta. Actualmente no existe un POM padre
en la raíz. Para verificar Proyectos:

```powershell
cd projects-service
mvn verify
```

La raíz queda reservada para documentación del monorepo, licencia y futuras
herramientas compartidas de integración. La configuración específica de cada
servicio permanece en su carpeta.
