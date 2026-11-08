$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $allowed = @('OPENAI_API_KEY','OPENAI_MODEL','OPENAI_BASE_URL','INTERNAL_SERVICE_TOKEN',
        'SONAR_HOST_URL','SONAR_TOKEN','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME',
        'SPRING_DATASOURCE_PASSWORD','SERVER_PORT')
    if (Test-Path -LiteralPath '.env') {
        foreach ($line in (Get-Content -LiteralPath '.env')) {
            if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
                $envName = $Matches[1]
                $envValue = $Matches[2].Trim()
                if ($envValue.Length -ge 2 -and
                    (($envValue.StartsWith('"') -and $envValue.EndsWith('"')) -or
                     ($envValue.StartsWith("'") -and $envValue.EndsWith("'")))) {
                    $envValue = $envValue.Substring(1, $envValue.Length - 2)
                }
                if ($allowed -contains $envName -and
                    -not [Environment]::GetEnvironmentVariable($envName, 'Process')) {
                    [Environment]::SetEnvironmentVariable($envName, $envValue, 'Process')
                }
            }
        }
    }
    foreach ($envName in @('OPENAI_API_KEY','OPENAI_MODEL','INTERNAL_SERVICE_TOKEN')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($envName, 'Process'))) {
            throw "Configure $envName in .env or in the process environment"
        }
    }
    if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
        $jdk = Get-ChildItem -Path "$env:USERPROFILE/.vscode/extensions/redhat.java-*/jre/*/bin/java.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $jdk.FullName) }
        else { throw 'Install Java 21+ or configure JAVA_HOME' }
    }
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) { $mavenPath = $maven.Source }
    else {
        $maven = Get-ChildItem -Path "$env:USERPROFILE/.m2/wrapper/dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if (-not $maven) { throw 'Install Maven 3.6.3+ or make it available in PATH' }
        $mavenPath = $maven.FullName
    }
    & $mavenPath spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Application exited with code $LASTEXITCODE" }
} finally { Pop-Location }