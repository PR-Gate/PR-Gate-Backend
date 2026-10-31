# Piloto local reproducible

Este piloto analiza el código propio de cicd-service en la PC del desarrollador.
Usa una referencia académica ficticia, un ID de repositorio ficticio reservado para
la prueba y una ejecución simulada. No crea un proyecto en Projects ni ejecuta
GitHub Actions. El campo revision es una huella SHA256 del código local y no un
commit de GitHub. Estas referencias no deben usarse con grupos reales.

## Preparación

1. Iniciar SonarQube y configurar el webhook de instancia hacia
   http://localhost:8082/api/v1/webhooks/sonar con el secreto del .env.
2. Iniciar cicd-service normalmente con `mvn spring-boot:run` en otra terminal.
3. Verificar SONAR_BASE_URL, SONAR_TOKEN y SONAR_WEBHOOK_SECRET en el .env.
   La cuenta técnica necesita Create Projects, Browse y Execute Analysis sobre el
   proyecto piloto (revisar la plantilla de permisos). El token solo se usa aquí
   en el entorno local de confianza; jamás se copia al workflow de los alumnos.

## Ejecutar

Desde cicd-service, en PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Invoke-LocalPilot.ps1
```

El perfil local-pilot no abre un servidor HTTP. Prepara los datos mediante los
casos de uso, guarda un manifiesto en target y cierra su contexto. El servidor
normal sigue escuchando webhooks y procesando la bandeja. Después se ejecutan
pruebas y scanner Maven con una versión fija; el script confirma la asociación
con un verificador interno del perfil local-pilot. Las consultas HTTP exigen
autenticación y autorización; el piloto no abre una excepción en ellas. Se
conservan manifiesto y resultado en target, excluido de Git.

Un Quality Gate ERROR es un resultado válido del piloto de integración: no implica
que la calidad del código esté aprobada. No hay cobertura si no se generó su reporte.

## Alcance y datos persistidos

El recurso técnico de Sonar se reutiliza con su clave estable. Cada ejecución del
script crea otra ejecución sintética y otro análisis. El proyecto académico de
prueba usa UUID c04e8799-8ff9-450a-8c7b-110eb8f784ea; el ID GitHub simulado es
9000000000001. El piloto no borra datos automáticamente.

Prueba PostgreSQL, aprovisionamiento Sonar, scanner, webhook firmado, bandeja y
consultas. La recepción real desde Projects, GitHub App, Actions y despliegue del
workflow siguen fuera de este piloto.

## Resultado observado en desarrollo

El 31 de octubre de 2026 se ejecutó un scanner real y se consultó el análisis
asociado desde la API: pipeline COMPLETED, Quality Gate OK. Los identificadores
y el resultado quedan en target/local-pilot-result.json. Este resultado no acredita
cobertura: no se generó un reporte JaCoCo.

La prueba descubrió que el nombre de rama interno de Sonar puede ser main aunque
la rama Git de evaluación tenga otro nombre. La correlación usa la propiedad Git
enviada por el workflow, el commit, el proyecto técnico y el intento; no compara
ese nombre Git con la etiqueta interna de Sonar. Los mensajes pendientes se
recuperaron desde la bandeja después de corregir y reiniciar el servicio.
