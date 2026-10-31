param(
    [ValidateRange(1024, 65535)]
    [int]$ApiTestPort = 18080,
    [ValidateRange(1024, 65535)]
    [int]$IntegratedTestPort = 18081
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jarPath = Join-Path $repoRoot 'target/bikestore-async-0.0.1-SNAPSHOT.jar'
$envFile = Join-Path $repoRoot '.env'
$demoVhost = 'bikestore-evaluation-demo'
$expectedDemoVhost = 'bikestore-evaluation-demo'
$evidenceRoot = Join-Path $repoRoot 'docs/evidencia/procesos'
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDirectory = Join-Path $evidenceRoot $runId
$rawLogDirectory = Join-Path $repoRoot "target/proceso-pruebas/$runId"
$javaCommand = Get-Command java -ErrorAction Stop
$ownedProcesses = [System.Collections.Generic.List[object]]::new()
$activeRoles = @{}
$script:port8080OwnersBefore = @()
$rabbitConfig = @{}
$environmentNames = @(
    'SPRING_RABBITMQ_HOST', 'SPRING_RABBITMQ_PORT', 'SPRING_RABBITMQ_USERNAME',
    'SPRING_RABBITMQ_PASSWORD', 'SPRING_RABBITMQ_VIRTUAL_HOST'
)
$previousEnvironment = @{}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'No existe el JAR. Ejecuta primero: .\mvnw.cmd -DskipTests package'
}
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'No se encontró .env. Configura las credenciales locales de RabbitMQ y vuelve a ejecutar.'
}

foreach ($line in [System.IO.File]::ReadLines($envFile)) {
    if ($line -match '^\s*(RABBITMQ_USER|RABBITMQ_PASSWORD)\s*=\s*(.*?)\s*$') {
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $rabbitConfig[$Matches[1]] = $value
    }
}
if (-not $rabbitConfig.RABBITMQ_USER -or -not $rabbitConfig.RABBITMQ_PASSWORD) {
    throw 'El archivo .env debe definir RABBITMQ_USER y RABBITMQ_PASSWORD. No se muestran sus valores.'
}

function Invoke-RabbitCtl {
    param([string[]]$Arguments)

    $output = & docker compose exec -T rabbitmq rabbitmqctl @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'Falló la consulta a RabbitMQ de BikeStore; revisa docker compose ps.'
    }
    return $output
}

function Assert-DemoVhost {
    if ($demoVhost -ne $expectedDemoVhost) {
        throw 'Se canceló una operación porque el vhost no coincide con el aislamiento esperado.'
    }
    $vhosts = Invoke-RabbitCtl -Arguments @('list_vhosts', 'name')
    if ($vhosts -notcontains $demoVhost) {
        throw 'El vhost aislado no existe; no se realizará ninguna operación sobre colas.'
    }
}

function Get-QueueState {
    Assert-DemoVhost
    $lines = Invoke-RabbitCtl -Arguments @(
        'list_queues', '-p', $demoVhost, 'name', 'messages_ready', 'messages_unacknowledged', 'consumers'
    )
    $queues = @{}
    foreach ($line in $lines) {
        $columns = $line.Trim() -split '\s+'
        if ($columns.Count -ge 4 -and $columns[1] -match '^\d+$' -and
                $columns[2] -match '^\d+$' -and $columns[3] -match '^\d+$') {
            $queues[$columns[0]] = [pscustomobject]@{
                Ready = [int]$columns[1]
                Unacknowledged = [int]$columns[2]
                Consumers = [int]$columns[3]
            }
        }
    }
    foreach ($queueName in @('bikestore.payment', 'bikestore.payment.retry',
            'bikestore.email', 'bikestore.payment.dead')) {
        if (-not $queues.ContainsKey($queueName)) {
            $queues[$queueName] = [pscustomobject]@{ Ready = 0; Unacknowledged = 0; Consumers = 0 }
        }
    }
    return $queues
}

function Write-Evidence {
    param([string]$Text)
    Add-Content -LiteralPath (Join-Path $runDirectory 'escenarios.log') -Value $Text -Encoding utf8
}

function Start-AppProcess {
    param(
        [string]$Name,
        [string]$Profiles,
        [int]$Port = 0
    )

    $sequence = @($ownedProcesses | Where-Object { $_.Name -eq $Name }).Count + 1
    $logName = "$Name-$sequence"
    $stdout = Join-Path $rawLogDirectory "$logName.stdout.log"
    $stderr = Join-Path $rawLogDirectory "$logName.stderr.log"
    $arguments = @('-jar', ('"' + $jarPath + '"'), "--spring.rabbitmq.virtual-host=$demoVhost")
    $profileLabel = $Profiles
    if ([string]::IsNullOrWhiteSpace($Profiles)) {
        $profileLabel = 'default (integrated)'
    } else {
        $arguments += "--spring.profiles.active=$Profiles"
    }
    if ($Port -gt 0) {
        $arguments += "--server.port=$Port"
    }
    $process = Start-Process -FilePath $javaCommand.Source -ArgumentList ($arguments -join ' ') `
        -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $record = [pscustomobject]@{
        Name = $Name
        Profiles = $profileLabel
        Process = $process
        Stdout = $stdout
        Stderr = $stderr
        StartedAt = [DateTimeOffset]::Now.ToString('o')
        StoppedAt = ''
    }
    $ownedProcesses.Add($record)
    $activeRoles[$Name] = $record
    Write-Evidence "PROCESO_INICIADO rol=$Name perfiles=$Profiles pid=$($process.Id) hora=$($record.StartedAt)"
    return $record
}

function Stop-OwnedProcess {
    param([object]$Record)

    if ($null -eq $Record) { return }
    $Record.Process.Refresh()
    if (-not $Record.Process.HasExited) {
        Stop-Process -Id $Record.Process.Id -ErrorAction Stop
        if (-not $Record.Process.WaitForExit(10000)) {
            Stop-Process -Id $Record.Process.Id -Force -ErrorAction Stop
            [void]$Record.Process.WaitForExit(10000)
        }
    }
    if ($Record.StoppedAt) { return }
    $Record.StoppedAt = [DateTimeOffset]::Now.ToString('o')
    if ($activeRoles.ContainsKey($Record.Name) -and $activeRoles[$Record.Name].Process.Id -eq $Record.Process.Id) {
        $activeRoles.Remove($Record.Name)
    }
    Write-Evidence "PROCESO_DETENIDO rol=$($Record.Name) pid=$($Record.Process.Id) hora=$($Record.StoppedAt)"
}

function Assert-ProcessAlive {
    param([object]$Record)
    $Record.Process.Refresh()
    if ($Record.Process.HasExited) {
        $log = Get-ProcessLog -Record $Record
        $lastLines = ($log -split "`r?`n" | Select-Object -Last 12) -join ' '
        throw "El proceso $($Record.Name) (PID $($Record.Process.Id)) terminó inesperadamente. Últimas líneas: $lastLines"
    }
}

function Get-ProcessLog {
    param([object]$Record)
    $parts = @()
    foreach ($path in @($Record.Stdout, $Record.Stderr)) {
        if (Test-Path -LiteralPath $path) {
            $parts += Get-Content -LiteralPath $path -Raw -Encoding Default -ErrorAction SilentlyContinue
        }
    }
    return ($parts -join "`n")
}

function Assert-WorkerHasNoWebServer {
    param([object]$Record)

    $listeners = Get-NetTCPConnection -State Listen -OwningProcess $Record.Process.Id -ErrorAction SilentlyContinue
    if ($listeners) {
        throw "El proceso worker $($Record.Name) abrió un puerto de escucha: $($listeners.LocalPort -join ', ')."
    }
    if ((Get-ProcessLog -Record $Record) -match 'Tomcat|Undertow|Netty started') {
        throw "El proceso worker $($Record.Name) inició un servidor web según su log."
    }
}

function Assert-DefaultPortUntouched {
    $currentOwners = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique | Sort-Object)
    $currentOwnerText = $currentOwners -join ','
    $initialOwnerText = $script:port8080OwnersBefore -join ','
    if ($currentOwnerText -ne $initialOwnerText) {
        throw 'Cambió el propietario de una escucha en 8080 durante la prueba; se detiene sin cerrar procesos externos.'
    }
}

function Wait-ForCondition {
    param([string]$Description, [scriptblock]$Condition, [int]$TimeoutSeconds = 40)

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        foreach ($runningProcess in $activeRoles.Values) { Assert-ProcessAlive -Record $runningProcess }
        if (& $Condition) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Tiempo agotado esperando: $Description"
}

function Wait-ApiReady {
    Wait-WebReady -Port $ApiTestPort
}

function Wait-WebReady {
    param([int]$Port)

    $uri = "http://127.0.0.1:$Port/"
    Wait-ForCondition -Description 'API REST lista en el puerto de prueba' -Condition {
        try {
            $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 2
            return $response.StatusCode -eq 200
        } catch {
            return $false
        }
    }
}

function Submit-Order {
    param([string]$Email)

    $request = @{
        customerEmail = $Email
        items = @(@{ bikeModel = 'Ruta Clara'; quantity = 1 })
    } | ConvertTo-Json -Depth 5 -Compress
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:$ApiTestPort/api/orders" `
        -Method Post -ContentType 'application/json' -Body $request -UseBasicParsing -TimeoutSec 10
    if ([int]$response.StatusCode -ne 202) {
        throw "El API devolvió HTTP $($response.StatusCode), se esperaba 202."
    }
    $accepted = $response.Content | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($accepted.pedidoId)) {
        throw 'La respuesta 202 no incluyó pedidoId.'
    }
    Write-Evidence "PEDIDO_ACEPTADO http=202 pedidoId=$($accepted.pedidoId) correo=$Email respuesta=$($response.Content)"
    return [string]$accepted.pedidoId
}

function Wait-LogEvent {
    param([object]$Record, [string]$PedidoId, [string]$Event)

    Wait-ForCondition -Description "$Event para pedidoId=$PedidoId en proceso $($Record.Name)" -Condition {
        (Get-ProcessLog -Record $Record) -match "pedidoId=$([regex]::Escape($PedidoId)).*evento=$([regex]::Escape($Event))"
    }
}

function Assert-Consumers {
    param([int]$Payment, [int]$Email)

    $queues = Get-QueueState
    if ($queues['bikestore.payment'].Consumers -ne $Payment -or
            $queues['bikestore.email'].Consumers -ne $Email) {
        throw "Consumidores inesperados: payment=$($queues['bikestore.payment'].Consumers), email=$($queues['bikestore.email'].Consumers); esperados payment=$Payment, email=$Email."
    }
}

function Assert-QueuesReady {
    param([string]$QueueName, [int]$Count)

    Wait-ForCondition -Description "$Count mensajes listos en $QueueName" -Condition {
        $queues = Get-QueueState
        $queues[$QueueName].Ready -eq $Count -and $queues[$QueueName].Unacknowledged -eq 0
    }
}

function Get-ManagementMessage {
    param([string]$QueueName)

    Assert-DemoVhost
    $encodedCredentials = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("$($rabbitConfig.RABBITMQ_USER):$($rabbitConfig.RABBITMQ_PASSWORD)"))
    $uri = "http://127.0.0.1:15672/api/queues/$([Uri]::EscapeDataString($demoVhost))/$QueueName/get"
    $body = @{ count = 1; ackmode = 'ack_requeue_true'; encoding = 'auto'; truncate = 50000 } |
        ConvertTo-Json -Compress
    $messages = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ Authorization = "Basic $encodedCredentials" } `
        -ContentType 'application/json' -Body $body -TimeoutSec 10
    if (@($messages).Count -ne 1) {
        throw "RabbitMQ Management no devolvió exactamente un mensaje de $QueueName."
    }
    return $messages[0]
}

function Get-ManagementConnections {
    Assert-DemoVhost
    $encodedCredentials = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes("$($rabbitConfig.RABBITMQ_USER):$($rabbitConfig.RABBITMQ_PASSWORD)"))
    $connections = Invoke-RestMethod -Uri 'http://127.0.0.1:15672/api/connections' `
        -Headers @{ Authorization = "Basic $encodedCredentials" } -TimeoutSec 10
    return @($connections)
}

function Get-DemoManagementConnectionCount {
    $connections = @(Get-ManagementConnections)
    return @($connections | Where-Object { $_.vhost -eq $expectedDemoVhost }).Count
}

function Assert-ManagementConnectionCount {
    param([int]$Expected)

    $lastCount = -1
    $lastVhosts = ''
    try {
        Wait-ForCondition -Description "RabbitMQ Management refleje $Expected conexiones del vhost aislado" -TimeoutSeconds 15 -Condition {
            $allConnections = @(Get-ManagementConnections)
            $lastCount = @($allConnections | Where-Object { $_.vhost -eq $expectedDemoVhost }).Count
            $lastVhosts = @($allConnections | ForEach-Object { $_.vhost } | Sort-Object -Unique) -join ', '
            $lastCount -eq $Expected
        }
    } catch {
        throw "RabbitMQ Management informó $lastCount conexiones del vhost aislado; se esperaban $Expected. Vhosts visibles: $lastVhosts. $($_.Exception.Message)"
    }
}

function Write-QueueSnapshot {
    param([string]$FileName)

    Assert-DemoVhost
    $lines = Invoke-RabbitCtl -Arguments @(
        'list_queues', '-p', $demoVhost, 'name', 'messages_ready', 'messages_unacknowledged', 'consumers'
    )
    $lines | Set-Content -LiteralPath (Join-Path $runDirectory $FileName) -Encoding utf8
}

function Assert-NoPendingDemoWork {
    $queues = Get-QueueState
    foreach ($name in $queues.Keys) {
        if ($queues[$name].Ready -ne 0 -or $queues[$name].Unacknowledged -ne 0 -or
                $queues[$name].Consumers -ne 0) {
            throw "El vhost aislado no está vacío ($name). Se cancela sin purgar mensajes ni detener procesos ajenos."
        }
    }
}

Push-Location $repoRoot
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    New-Item -ItemType Directory -Path $runDirectory | Out-Null
    New-Item -ItemType Directory -Path $rawLogDirectory -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $runDirectory 'escenarios.log') -Value "INICIO runId=$runId vhost=$demoVhost apiTestPort=$ApiTestPort" -Encoding utf8

    $container = & docker compose ps -q rabbitmq
    if ($LASTEXITCODE -ne 0 -or -not [string]($container | Select-Object -First 1)) {
        throw 'RabbitMQ de BikeStore no está iniciado; no se iniciaron otros contenedores.'
    }
    $containerId = [string]($container | Select-Object -First 1)
    $health = & docker inspect --format '{{.State.Health.Status}}' $containerId
    if ($LASTEXITCODE -ne 0 -or [string]($health | Select-Object -First 1).Trim() -ne 'healthy') {
        throw 'RabbitMQ de BikeStore no está healthy. Revisa docker compose ps.'
    }

    $existingPort = Get-NetTCPConnection -State Listen -LocalPort $ApiTestPort -ErrorAction SilentlyContinue
    $integratedPortInUse = Get-NetTCPConnection -State Listen -LocalPort $IntegratedTestPort -ErrorAction SilentlyContinue
    if ($existingPort -or $integratedPortInUse) {
        throw "Un puerto de prueba ($ApiTestPort o $IntegratedTestPort) está ocupado; no se cerró el proceso que lo utiliza."
    }
    $script:port8080OwnersBefore = @(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique | Sort-Object)

    $knownVhosts = Invoke-RabbitCtl -Arguments @('list_vhosts', 'name')
    if ($knownVhosts -notcontains $demoVhost) {
        & docker compose exec -T rabbitmq rabbitmqctl add_vhost $demoVhost *> $null
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el vhost aislado.' }
    }
    Assert-DemoVhost
    Assert-NoPendingDemoWork
    if ((Get-DemoManagementConnectionCount) -ne 0) {
        throw 'Hay conexiones existentes en el vhost aislado; se cancela sin cerrar conexiones ajenas.'
    }
    & docker compose exec -T rabbitmq rabbitmqctl set_permissions -p $demoVhost `
        $rabbitConfig.RABBITMQ_USER '.*' '.*' '.*' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron conceder permisos en el vhost aislado.' }

    $env:SPRING_RABBITMQ_HOST = '127.0.0.1'
    $env:SPRING_RABBITMQ_PORT = '5672'
    $env:SPRING_RABBITMQ_USERNAME = $rabbitConfig.RABBITMQ_USER
    $env:SPRING_RABBITMQ_PASSWORD = $rabbitConfig.RABBITMQ_PASSWORD
    $env:SPRING_RABBITMQ_VIRTUAL_HOST = $demoVhost

    $integrated = Start-AppProcess -Name 'integrated' -Profiles '' -Port $IntegratedTestPort
    Wait-WebReady -Port $IntegratedTestPort
    Wait-ForCondition -Description 'modo integrado registra los dos consumidores' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.payment'].Consumers -eq 1 -and $queues['bikestore.email'].Consumers -eq 1
    }
    Assert-ManagementConnectionCount -Expected 1
    Assert-ProcessAlive -Record $integrated
    Write-Evidence "MODO_INTEGRADO_COMPATIBLE defaultProfile=true pid=$($integrated.Process.Id) port=$IntegratedTestPort paymentConsumers=1 emailConsumers=1"
    Stop-OwnedProcess -Record $integrated
    Wait-ForCondition -Description 'modo integrado detenido sin dejar consumidores' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.payment'].Consumers -eq 0 -and $queues['bikestore.email'].Consumers -eq 0
    }

    $api = Start-AppProcess -Name 'api' -Profiles 'api' -Port $ApiTestPort
    Wait-ApiReady
    Assert-Consumers -Payment 0 -Email 0
    Assert-ManagementConnectionCount -Expected 0
    Write-Evidence "MODO_VERIFICADO perfil=api pid=$($api.Process.Id) HTTP=200 rabbitManagementConnections=0-hasta-publicar paymentConsumers=0 emailConsumers=0"

    $payment = Start-AppProcess -Name 'payment' -Profiles 'payment,evaluation'
    Wait-ForCondition -Description 'PaymentWorker conectado sin EmailWorker' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.payment'].Consumers -eq 1 -and $queues['bikestore.email'].Consumers -eq 0
    }
    Assert-WorkerHasNoWebServer -Record $payment
    Assert-DefaultPortUntouched
    Assert-ManagementConnectionCount -Expected 1
    Write-Evidence "MODO_VERIFICADO perfiles=payment,evaluation pid=$($payment.Process.Id) rabbitManagementConnections=1 paymentConsumers=1 emailConsumers=0 webListeners=0"

    $email = Start-AppProcess -Name 'email' -Profiles 'email'
    Wait-ForCondition -Description 'ambos workers conectados en procesos distintos' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.payment'].Consumers -eq 1 -and $queues['bikestore.email'].Consumers -eq 1
    }
    Assert-WorkerHasNoWebServer -Record $email
    Assert-DefaultPortUntouched
    Assert-ManagementConnectionCount -Expected 2
    $concurrentIds = @($api.Process.Id, $payment.Process.Id, $email.Process.Id) | Select-Object -Unique
    if ($concurrentIds.Count -ne 3) { throw 'API, payment y email no tienen tres PID distintos.' }
    Write-Evidence "ESCENARIO_A procesos_independientes=true apiPid=$($api.Process.Id) paymentPid=$($payment.Process.Id) emailPid=$($email.Process.Id) rabbitManagementConnections=2-antes-publicacion paymentConsumers=1 emailConsumers=1"
    $normalOrderId = Submit-Order -Email 'demo-normal@example.test'
    Wait-LogEvent -Record $payment -PedidoId $normalOrderId -Event 'PAYMENT_PAID'
    Wait-LogEvent -Record $email -PedidoId $normalOrderId -Event 'EMAIL_SENT'
    Assert-ManagementConnectionCount -Expected 3
    Assert-QueuesReady -QueueName 'bikestore.payment' -Count 0
    Write-Evidence "ESCENARIO_A_VERIFICADO pedidoId=$normalOrderId pago=PAID correo=EMAIL_SENT rabbitManagementConnections=3"

    Stop-OwnedProcess -Record $email
    Assert-Consumers -Payment 1 -Email 0
    Assert-ManagementConnectionCount -Expected 2
    Wait-ApiReady
    Assert-ProcessAlive -Record $api
    Assert-ProcessAlive -Record $payment
    Write-Evidence "INDEPENDENCIA email_detenido=true api_sigue_activa=true payment_sigue_activo=true rabbitManagementConnections=2 apiPid=$($api.Process.Id) paymentPid=$($payment.Process.Id)"

    $emailOfflineOrderId = Submit-Order -Email 'demo-email-off@example.test'
    Wait-LogEvent -Record $payment -PedidoId $emailOfflineOrderId -Event 'PAYMENT_PAID'
    Assert-QueuesReady -QueueName 'bikestore.email' -Count 1
    if ((Get-ProcessLog -Record $email) -match [regex]::Escape($emailOfflineOrderId)) {
        throw 'El proceso EmailWorker detenido registró un evento inesperado.'
    }
    Write-Evidence "ESCENARIO_B_PENDIENTE pedidoId=$emailOfflineOrderId pago=PAID emailQueueReady=1 rabbitManagementConnections=2 apiPid=$($api.Process.Id) paymentPid=$($payment.Process.Id)"

    $email = Start-AppProcess -Name 'email' -Profiles 'email'
    Wait-ForCondition -Description 'EmailWorker recupera el evento pendiente' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.email'].Consumers -eq 1 -and $queues['bikestore.email'].Ready -eq 0 -and
                (Get-ProcessLog -Record $email) -match "pedidoId=$([regex]::Escape($emailOfflineOrderId)).*evento=EMAIL_SENT"
    }
    Assert-WorkerHasNoWebServer -Record $email
    Assert-DefaultPortUntouched
    Assert-ManagementConnectionCount -Expected 3
    Write-Evidence "ESCENARIO_B_VERIFICADO pedidoId=$emailOfflineOrderId mismoPedidoEnPagoYCorreo=true rabbitManagementConnections=3 emailPid=$($email.Process.Id)"
    Stop-OwnedProcess -Record $email

    Stop-OwnedProcess -Record $payment
    Assert-Consumers -Payment 0 -Email 0
    Wait-ApiReady
    Assert-ProcessAlive -Record $api

    $email = Start-AppProcess -Name 'email' -Profiles 'email'
    Wait-ForCondition -Description 'perfil email registra exclusivamente su consumidor' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.payment'].Consumers -eq 0 -and $queues['bikestore.email'].Consumers -eq 1
    }
    Assert-WorkerHasNoWebServer -Record $email
    Assert-DefaultPortUntouched
    Write-Evidence "MODO_VERIFICADO perfil=email pid=$($email.Process.Id) rabbitManagementConnections=2 paymentConsumers=0 emailConsumers=1 webListeners=0"
    Stop-OwnedProcess -Record $email
    Assert-Consumers -Payment 0 -Email 0
    Assert-ManagementConnectionCount -Expected 1

    $paymentOfflineOrderId = Submit-Order -Email 'demo-payment-off@example.test'
    Assert-QueuesReady -QueueName 'bikestore.payment' -Count 1
    Assert-Consumers -Payment 0 -Email 0
    Write-Evidence "ESCENARIO_C_PENDIENTE pedidoId=$paymentOfflineOrderId HTTP=202 paymentQueueReady=1 rabbitManagementConnections=1 apiSigueActiva=true apiPid=$($api.Process.Id)"

    $payment = Start-AppProcess -Name 'payment' -Profiles 'payment,evaluation'
    Wait-ForCondition -Description 'PaymentWorker recupera el pedido pendiente' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.payment'].Consumers -eq 1 -and $queues['bikestore.payment'].Ready -eq 0 -and
                (Get-ProcessLog -Record $payment) -match "pedidoId=$([regex]::Escape($paymentOfflineOrderId)).*evento=PAYMENT_PAID"
    }
    Assert-WorkerHasNoWebServer -Record $payment
    Assert-DefaultPortUntouched
    Assert-ManagementConnectionCount -Expected 2
    Assert-QueuesReady -QueueName 'bikestore.email' -Count 1
    Write-Evidence "ESCENARIO_C_VERIFICADO pedidoId=$paymentOfflineOrderId pago=PAID rabbitManagementConnections=2 paymentPid=$($payment.Process.Id)"

    $email = Start-AppProcess -Name 'email' -Profiles 'email'
    Wait-ForCondition -Description 'EmailWorker confirma el pedido recuperado' -Condition {
        $queues = Get-QueueState
        $queues['bikestore.email'].Consumers -eq 1 -and $queues['bikestore.email'].Ready -eq 0 -and
                (Get-ProcessLog -Record $email) -match "pedidoId=$([regex]::Escape($paymentOfflineOrderId)).*evento=EMAIL_SENT"
    }
    Assert-WorkerHasNoWebServer -Record $email
    Assert-DefaultPortUntouched
    Assert-ManagementConnectionCount -Expected 3
    Write-Evidence "ESCENARIO_C_CORREO pedidoId=$paymentOfflineOrderId EMAIL_SENT=true rabbitManagementConnections=3 emailPid=$($email.Process.Id)"

    $failureOrderId = Submit-Order -Email 'demo-fail-final@example.test'
    Wait-LogEvent -Record $payment -PedidoId $failureOrderId -Event 'PAYMENT_DEAD_LETTER'
    Assert-QueuesReady -QueueName 'bikestore.payment.dead' -Count 1
    $attemptPattern = "(?m)pedidoId=$([regex]::Escape($failureOrderId))[^\r\n]*evento=PAYMENT_ATTEMPT"
    $attemptCount = [regex]::Matches((Get-ProcessLog -Record $payment), $attemptPattern).Count
    if ($attemptCount -ne 4) {
        throw "El proceso PaymentWorker registró $attemptCount intentos para $failureOrderId; se esperaban cuatro."
    }
    $queuesAfterFailure = Get-QueueState
    if ($queuesAfterFailure['bikestore.email'].Ready -ne 0 -or
            (Get-ProcessLog -Record $email) -match "pedidoId=$([regex]::Escape($failureOrderId)).*evento=EMAIL_SENT") {
        throw 'El pago fallido generó una confirmación de correo.'
    }

    $dlqMessage = Get-ManagementMessage -QueueName 'bikestore.payment.dead'
    $dlqPayload = [string]$dlqMessage.payload | ConvertFrom-Json
    if ($dlqPayload.order.pedidoId -ne $failureOrderId -or [int]$dlqPayload.attempts -ne 4) {
        throw 'El contenido recibido desde Management no coincide con el pedido fallido de cuatro intentos.'
    }
    $dlqPayload | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $runDirectory 'mensaje-dlq.json') -Encoding utf8
    Write-Evidence "ESCENARIO_D_VERIFICADO pedidoId=$failureOrderId PAYMENT_DEAD_LETTER=true intentos=$($dlqPayload.attempts) intentosRegistrados=$attemptCount emailQueueReady=0 emailSent=false"

    Write-QueueSnapshot -FileName 'colas-antes-de-limpieza.txt'

    Stop-OwnedProcess -Record $email
    Stop-OwnedProcess -Record $payment
    Stop-OwnedProcess -Record $api
    Wait-ForCondition -Description 'todos los procesos liberaron su conexión RabbitMQ' -Condition {
        (Get-DemoManagementConnectionCount) -eq 0
    }
    foreach ($queueName in @('bikestore.payment', 'bikestore.payment.retry', 'bikestore.email', 'bikestore.payment.dead')) {
        $afterStop = Get-QueueState
        foreach ($currentName in $afterStop.Keys) {
            if ($afterStop[$currentName].Consumers -ne 0) {
                throw "Quedó un consumidor activo ($currentName); no se limpiará ninguna cola."
            }
        }
    }

    Assert-DemoVhost
    $finalQueues = Get-QueueState
    foreach ($queueName in @('bikestore.payment', 'bikestore.payment.retry', 'bikestore.email')) {
        if ($finalQueues[$queueName].Ready -ne 0 -or $finalQueues[$queueName].Unacknowledged -ne 0) {
            throw "La cola $queueName contiene trabajo pendiente; no se limpiará el vhost."
        }
    }
    if ($finalQueues['bikestore.payment.dead'].Ready -ne 1 -or
            $finalQueues['bikestore.payment.dead'].Unacknowledged -ne 0) {
        throw 'La DLQ no contiene exactamente el mensaje de evidencia; no se limpiará.'
    }
    & docker compose exec -T rabbitmq rabbitmqctl purge_queue -p $demoVhost 'bikestore.payment.dead' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo retirar el mensaje de prueba de la DLQ aislada.' }
    Assert-NoPendingDemoWork
    Write-QueueSnapshot -FileName 'colas-finales.txt'

    $tab = [string][char]9
    $processLines = @("rol${tab}perfil${tab}pid${tab}inicio${tab}detencion")
    foreach ($record in $ownedProcesses) {
        $processLines += "$($record.Name)${tab}$($record.Profiles)${tab}$($record.Process.Id)${tab}$($record.StartedAt)${tab}$($record.StoppedAt)"
    }
    $processLines | Set-Content -LiteralPath (Join-Path $runDirectory 'procesos.tsv') -Encoding utf8
    $eventLines = @()
    $startupLines = @()
    foreach ($record in $ownedProcesses) {
        foreach ($line in ((Get-ProcessLog -Record $record) -split "`r?`n")) {
            if ($line -match 'pedidoId=.*evento=(PAYMENT_ATTEMPT|PAYMENT_FAILED|PAYMENT_RETRY_SCHEDULED|PAYMENT_PAID|PAYMENT_DEAD_LETTER|EMAIL_SENT)') {
                $eventLines += $line
            }
            if ($line -match 'No active profile set, falling back|The following .* profile(s)? (is|are) active|Tomcat started on port') {
                $startupLines += $line
            }
        }
    }
    $eventLines | Set-Content -LiteralPath (Join-Path $runDirectory 'eventos-workers.log') -Encoding utf8
    $startupLines | Set-Content -LiteralPath (Join-Path $runDirectory 'perfiles-y-servidor-web.log') -Encoding utf8
    Write-Evidence "FIN resultado=APROBADO evidencia=$runDirectory vhost=$demoVhost"
    Write-Output 'Verificación distribuida completada: tres PID independientes y escenarios A–D aprobados.'
    Write-Output "Evidencia: $runDirectory"
}
finally {
    foreach ($record in @($ownedProcesses.ToArray())) {
        try { Stop-OwnedProcess -Record $record } catch { }
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    Pop-Location
}
