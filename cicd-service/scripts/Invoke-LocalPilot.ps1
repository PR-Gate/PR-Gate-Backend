param([int]$WaitSeconds = 180)
$ErrorActionPreference = 'Stop'
$serviceDirectory = Split-Path $PSScriptRoot -Parent
Push-Location $serviceDirectory
$previousToken = $env:SONAR_TOKEN
$previousHost = $env:SONAR_HOST_URL
try {
    $settings = @{}
    Get-Content -LiteralPath '.env' | ForEach-Object {
        if ($_ -match '^\s*(SONAR_BASE_URL|SONAR_TOKEN|SERVER_PORT)\s*=\s*(.*?)\s*$') {
            $settings[$matches[1]] = $matches[2]
        }
    }
    if (-not $settings['SONAR_TOKEN']) { throw 'SONAR_TOKEN is missing in .env' }
    $env:SONAR_TOKEN = $settings['SONAR_TOKEN']
    $env:SONAR_HOST_URL = $settings['SONAR_BASE_URL']
    if (-not $env:SONAR_HOST_URL) { $env:SONAR_HOST_URL = 'http://localhost:9000' }
    $port = $settings['SERVER_PORT']; if (-not $port) { $port = '8082' }
    $serviceUrl = 'http://localhost:' + $port
    $health = Invoke-RestMethod -Uri ($serviceUrl + '/actuator/health') -TimeoutSec 10
    if ($health.status -ne 'UP') { throw 'Start cicd-service normally in another terminal first' }

    # Fingerprint the actual local source tree, including uncommitted changes. This is not a GitHub commit.
    $files = @(Get-ChildItem -LiteralPath 'src' -File -Recurse) + @(Get-Item -LiteralPath 'pom.xml')
    $fingerprint = ($files | Sort-Object FullName | ForEach-Object {
        $_.FullName.Substring($serviceDirectory.Length) + ':' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }) -join "`n"
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $revision = ([BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($fingerprint)))).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }

    & mvn 'spring-boot:run' '-Dspring-boot.run.profiles=local-pilot' "-Dspring-boot.run.arguments=--pilot.phase=prepare --pilot.revision=$revision"
    if ($LASTEXITCODE -ne 0) { throw 'Local pilot preparation failed' }
    $manifest = Get-Content -LiteralPath 'target/local-pilot.json' -Raw | ConvertFrom-Json
    & mvn 'verify' 'org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar' `
        "-Dsonar.projectKey=$($manifest.sonarProjectKey)" `
        "-Dsonar.scm.revision=$revision" `
        "-Dsonar.analysis.prgateRepositoryId=$($manifest.repositoryId)" `
        "-Dsonar.analysis.prgateRunId=$($manifest.runId)" `
        "-Dsonar.analysis.prgateRunAttempt=$($manifest.attempt)" `
        "-Dsonar.analysis.prgateBranch=$($manifest.branch)"
    if ($LASTEXITCODE -ne 0) { throw 'Build or scanner failed; the pilot has not been marked successful' }
    & mvn 'spring-boot:run' '-Dspring-boot.run.profiles=local-pilot' '-Dspring-boot.run.arguments=--pilot.phase=finish'
    if ($LASTEXITCODE -ne 0) { throw 'Could not record pilot completion' }

    # Internal local-only verification does not bypass the protected HTTP queries.
    & mvn 'spring-boot:run' '-Dspring-boot.run.profiles=local-pilot' "-Dspring-boot.run.arguments=--pilot.phase=verify --pilot.wait-seconds=$WaitSeconds"
    if ($LASTEXITCODE -ne 0) { throw 'Pilot result verification failed' }
    $result = Get-Content -LiteralPath 'target/local-pilot-result.json' -Raw | ConvertFrom-Json
    Write-Host ('PILOT OK. Pipeline: ' + $manifest.pipelineId + '; Quality Gate: ' + $result.sonarAnalysis.qualityGateStatus)
    Write-Host 'Evidence: target/local-pilot-result.json (synthetic Projects/GitHub references).'
    exit 0
} finally {
    $env:SONAR_TOKEN = $previousToken
    $env:SONAR_HOST_URL = $previousHost
    Pop-Location
}
