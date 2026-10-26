param([ValidateSet('Build', 'Api', 'Broker', 'Consumer', 'Receipts', 'Test')][string]$Action = 'Build')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    switch ($Action) {
        'Build' {
            & .\backend\mvnw.cmd -f backend/pom.xml clean package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
            & .\backend\mvnw.cmd -f messaging/pom.xml clean package
            if ($LASTEXITCODE -ne 0) { throw 'Messaging build failed' }
        }
        'Test' {
            & .\backend\mvnw.cmd -f backend/pom.xml test
            if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }
            & .\backend\mvnw.cmd -f messaging/pom.xml test
            if ($LASTEXITCODE -ne 0) { throw 'Messaging tests failed' }
        }
        'Api' {
            Push-Location -LiteralPath (Join-Path $projectRoot 'backend')
            try { & java -jar target/repair-tickets-0.0.1-SNAPSHOT.jar }
            finally { Pop-Location }
            if ($LASTEXITCODE -ne 0) { throw 'API exited with an error' }
        }
        default {
            $mode = @{ Broker = 'broker'; Consumer = 'consume'; Receipts = 'receipts' }[$Action]
            & java -jar messaging/target/repair-messaging-0.0.1-SNAPSHOT.jar $mode
            if ($LASTEXITCODE -ne 0) { throw 'Messaging process exited with an error' }
        }
    }
} finally { Pop-Location }
