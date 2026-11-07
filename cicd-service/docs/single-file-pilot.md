# Prueba local con un archivo Python

El arranque normal habilita `POST /api/v1/pilot/file-analyses` en el puerto
del servicio (8082 por defecto). Acepta un único archivo `.py` UTF-8 de hasta 256 KB mediante
`multipart/form-data` con campo `file`. No requiere un repositorio GitHub ni
registra una ejecución de pipeline en PostgreSQL: es una prueba puntual de
SonarQube, separada de la evaluación académica.

Requisitos: PostgreSQL y SonarQube disponibles, `SONAR_TOKEN` con permisos para
crear y analizar proyectos, y SonarScanner CLI instalado directamente en el
mismo equipo que ejecuta el microservicio.
Descargar el ZIP para Windows x64 o Linux x64 desde la documentación oficial:
https://docs.sonarsource.com/sonarqube-community-build/analyzing-source-code/scanners/sonarscanner
Descomprimirlo y configurar `SONAR_SCANNER_HOME` con la carpeta que contiene
`bin` (no con la carpeta de SonarQube Server).

Ejemplo `.env` en Windows (usar barras `/` para evitar escapes de properties):

```properties
SONAR_BASE_URL=http://localhost:9000
SONAR_SCANNER_HOME=C:/herramientas/sonar-scanner
```

En Linux/Zorin, usar por ejemplo `SONAR_SCANNER_HOME=/opt/sonar-scanner` y
comprobar que `bin/sonar-scanner` tenga permiso de ejecución.
El scanner utiliza `SONAR_BASE_URL` y `SONAR_TOKEN` del servicio.
Configurar estas variables junto con las credenciales de PostgreSQL en `.env`.

Desde `cicd-service`:

```powershell
mvn spring-boot:run
```

En otra terminal:

```powershell
curl.exe -X POST -F "file=@C:\ruta\demo.py;type=text/x-python" http://127.0.0.1:8082/api/v1/pilot/file-analyses
```

La respuesta devuelve `projectKey`, `analysisId`, `qualityGateStatus` y
`dashboardUrl`. Cada carga crea un proyecto SonarQube independiente para que
los resultados no se mezclen; se pueden eliminar manualmente tras la prueba.
El backend guarda el archivo temporalmente, ejecuta SonarScanner CLI localmente,
espera hasta 90 segundos al resultado y borra los archivos temporales.
No ejecuta el script Python. La prueba requiere que el servidor y el scanner
realmente estén disponibles; un error de infraestructura devuelve 502.

El endpoint aparece en Swagger bajo `single-python-file-controller`.
El flujo de repositorios continúa mediante GitHub Actions → SonarQube → webhook de PRGate.
