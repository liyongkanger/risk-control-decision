# Use file-backed H2 so local review data survives backend restarts.
$ErrorActionPreference = 'Stop'
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $scriptDirectory '..')).Path
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'pom.xml'))) {
    throw 'Cannot locate the project root.'
}
$dataDir = Join-Path $repoRoot '.local'
if (-not (Test-Path -LiteralPath $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir | Out-Null
}
$databasePath = (Join-Path $dataDir 'variable-review').Replace('\', '/')
$env:SPRING_PROFILES_ACTIVE = 'test'
$env:SPRING_DATASOURCE_URL = "jdbc:h2:file:$databasePath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'sa'
$env:SPRING_DATASOURCE_PASSWORD = ''
mvn '-Dspring-boot.run.useTestClasspath=true' '-Dspring-boot.run.arguments=--server.port=8081' spring-boot:run
