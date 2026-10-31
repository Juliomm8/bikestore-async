$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $repoRoot '.env'
$demoVhost = 'bikestore-evaluation-demo'
$evidenceRoot = Join-Path $repoRoot 'docs/evidencia/demostracion'
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDirectory = Join-Path $evidenceRoot $runId

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'No se encontró .env. Configura las credenciales locales de RabbitMQ y vuelve a ejecutar.'
}

$localConfig = @{}
foreach ($line in [System.IO.File]::ReadLines($envFile)) {
    if ($line -match '^\s*(RABBITMQ_USER|RABBITMQ_PASSWORD)\s*=\s*(.*?)\s*$') {
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $localConfig[$Matches[1]] = $value
    }
}

if (-not $localConfig.RABBITMQ_USER -or -not $localConfig.RABBITMQ_PASSWORD) {
    throw 'El archivo .env debe definir RABBITMQ_USER y RABBITMQ_PASSWORD. No se muestran sus valores.'
}

Push-Location $repoRoot
$environmentNames = @(
    'SPRING_RABBITMQ_HOST', 'SPRING_RABBITMQ_PORT', 'SPRING_RABBITMQ_USERNAME',
    'SPRING_RABBITMQ_PASSWORD', 'SPRING_RABBITMQ_VIRTUAL_HOST'
)
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $containerOutput = & docker compose ps -q rabbitmq
    $dockerExitCode = $LASTEXITCODE
    $containerId = [string]($containerOutput | Select-Object -First 1)
    if ($dockerExitCode -ne 0 -or -not $containerId.Trim()) {
        throw 'RabbitMQ de BikeStore no está iniciado. Inicia únicamente con: docker compose up -d rabbitmq'
    }

    $healthOutput = & docker inspect --format '{{.State.Health.Status}}' $containerId
    $dockerExitCode = $LASTEXITCODE
    $health = [string]($healthOutput | Select-Object -First 1)
    if ($dockerExitCode -ne 0 -or $health.Trim() -ne 'healthy') {
        throw 'RabbitMQ de BikeStore no está healthy. Revisa docker compose ps; no se modificaron otros contenedores.'
    }

    $vhosts = & docker compose exec -T rabbitmq rabbitmqctl list_vhosts name 2>&1
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) { throw 'No se pudieron inspeccionar los vhosts de RabbitMQ.' }
    if ($vhosts -notcontains $demoVhost) {
        & docker compose exec -T rabbitmq rabbitmqctl add_vhost $demoVhost *> $null
        $dockerExitCode = $LASTEXITCODE
        if ($dockerExitCode -ne 0) { throw 'No se pudo crear el vhost aislado de la demostración.' }
    }

    & docker compose exec -T rabbitmq rabbitmqctl set_permissions -p $demoVhost `
        $localConfig.RABBITMQ_USER '.*' '.*' '.*' *> $null
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) { throw 'No se pudieron conceder permisos al usuario local en el vhost de demostración.' }

    $beforeQueues = & docker compose exec -T rabbitmq rabbitmqctl list_queues -p $demoVhost `
        name messages_ready messages_unacknowledged consumers 2>&1
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) { throw 'No se pudieron revisar las colas del vhost de demostración.' }
    foreach ($line in $beforeQueues) {
        $columns = $line.Trim() -split '\s+'
        if ($columns.Count -ge 4 -and $columns[1] -match '^\d+$' -and
                $columns[2] -match '^\d+$' -and $columns[3] -match '^\d+$') {
            if ([int]$columns[1] -gt 0 -or [int]$columns[2] -gt 0 -or [int]$columns[3] -gt 0) {
                throw 'El vhost de demostración tiene mensajes o consumidores activos; se cancela sin purgar nada.'
            }
        }
    }

    $env:SPRING_RABBITMQ_HOST = '127.0.0.1'
    $env:SPRING_RABBITMQ_PORT = '5672'
    $env:SPRING_RABBITMQ_USERNAME = $localConfig.RABBITMQ_USER
    $env:SPRING_RABBITMQ_PASSWORD = $localConfig.RABBITMQ_PASSWORD
    $env:SPRING_RABBITMQ_VIRTUAL_HOST = $demoVhost

    New-Item -ItemType Directory -Path $runDirectory | Out-Null
    $testOutput = & .\mvnw.cmd '-Dtest=OrderApiIntegrationTest,PaymentIntegrationTest' test 2>&1
    $testExitCode = $LASTEXITCODE

    $reportDirectory = Join-Path $repoRoot 'target/surefire-reports'
    $reportFiles = @(
        (Join-Path $reportDirectory 'com.bikestore.asyncapp.OrderApiIntegrationTest.txt'),
        (Join-Path $reportDirectory 'com.bikestore.asyncapp.PaymentIntegrationTest.txt')
    )
    $capturedLines = @()
    foreach ($report in $reportFiles) {
        if (Test-Path -LiteralPath $report) {
            $xmlReport = Join-Path $reportDirectory ("TEST-" + [System.IO.Path]::GetFileNameWithoutExtension($report) + '.xml')
            if (Test-Path -LiteralPath $xmlReport) {
                [xml]$parsedReport = Get-Content -LiteralPath $xmlReport -Raw
                foreach ($outputNode in $parsedReport.SelectNodes('//system-out | //system-err')) {
                    $capturedLines += $outputNode.InnerText -split "`r?`n"
                }
            }
        }
    }

    $evidenceLines = @($capturedLines | Where-Object {
        $_ -match 'BIKESTORE_DEMO ' -or $_ -match 'pedidoId=.*timestamp=.*thread=.*evento='
    })
    $evidencePath = Join-Path $runDirectory 'mensajes-y-eventos.log'
    $evidenceLines | Set-Content -LiteralPath $evidencePath -Encoding utf8

    $afterQueues = & docker compose exec -T rabbitmq rabbitmqctl list_queues -p $demoVhost `
        name messages_ready messages_unacknowledged consumers 2>&1
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) { throw 'No se pudo capturar el estado final de las colas.' }
    $afterQueues | Set-Content -LiteralPath (Join-Path $runDirectory 'colas-finales.txt') -Encoding utf8

    $exchanges = & docker compose exec -T rabbitmq rabbitmqctl list_exchanges -p $demoVhost name type durable 2>&1
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) { throw 'No se pudieron capturar los exchanges de la demostración.' }
    $exchanges | Set-Content -LiteralPath (Join-Path $runDirectory 'exchanges.txt') -Encoding utf8

    $bindings = & docker compose exec -T rabbitmq rabbitmqctl list_bindings -p $demoVhost source_name destination_name routing_key 2>&1
    $dockerExitCode = $LASTEXITCODE
    if ($dockerExitCode -ne 0) { throw 'No se pudieron capturar los bindings de la demostración.' }
    $bindings | Set-Content -LiteralPath (Join-Path $runDirectory 'bindings.txt') -Encoding utf8

    if ($testExitCode -ne 0) {
        $testOutput | Select-Object -Last 35
        throw "Las pruebas de demostración terminaron con código $testExitCode. Revisa el log de Maven; las colas principales no se tocaron."
    }

    $requiredEvidence = @(
        'pago-inmediato', 'pago-despues-de-reintentos', 'fallo-definitivo',
        'mensaje-dlq', 'mensaje-json-rabbitmq', 'PAYMENT_ATTEMPT', 'PAYMENT_DEAD_LETTER'
    )
    foreach ($marker in $requiredEvidence) {
        if (-not (Select-String -LiteralPath $evidencePath -SimpleMatch $marker -Quiet)) {
            throw "Falta la evidencia '$marker'. Las pruebas pasaron, pero la captura está incompleta."
        }
    }

    Write-Output 'Demostración RabbitMQ completada; no se utilizaron ni purgaron las colas del vhost principal.'
    Write-Output "Vhost: $demoVhost"
    Write-Output "Evidencia: $evidencePath"
    Write-Output "Topología: $runDirectory"
}
finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    Pop-Location
}
