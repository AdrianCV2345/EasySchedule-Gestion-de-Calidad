$envFile = Join-Path $PSScriptRoot "..\.env"
foreach ($line in Get-Content $envFile) {
    if ($line -match '^([A-Z_]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1].Trim(), $Matches[2].Trim(), "Process")
    }
}
if (-not $env:SONAR_HOST_URL) { $env:SONAR_HOST_URL = "http://localhost:9000" }

# El analisis corre los tests con H2 (perfil test). El .env fuerza el perfil dev con
# Postgres, y las variables de entorno tienen mayor prioridad que application-test.properties,
# asi que se limpian para que no rompan la ejecucion de :test.
$env:SPRING_PROFILES_ACTIVE = "test"
Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_DATASOURCE_USERNAME, Env:SPRING_DATASOURCE_PASSWORD, Env:DB_URL -ErrorAction SilentlyContinue

.\gradlew.bat sonar