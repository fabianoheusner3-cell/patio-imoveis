param(
    [string]$Email = 'fabianoheusner3@gmail.com',
    [string]$Destinatario = ''
)
$ErrorActionPreference = 'Stop'
if ($Email -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Informe um email valido no parametro -Email.'
}
if ([string]::IsNullOrWhiteSpace($Destinatario)) { $Destinatario = $Email }
if ($Destinatario -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Informe um destinatario valido no parametro -Destinatario.'
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven nao encontrado. Instale o Maven e configure o PATH.' }
$raizProjeto = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repositorioMaven = [IO.Path]::GetFullPath((Join-Path $raizProjeto '..\.m2\repository'))
$variaveisAnteriores = @{}
foreach ($nomeVariavel in @('GMAIL_USERNAME','GMAIL_APP_PASSWORD','IMOBILIARIA_EMAIL','ADMIN_EMAIL','ADMIN_PASSWORD')) {
    $variaveisAnteriores[$nomeVariavel] = [Environment]::GetEnvironmentVariable($nomeVariavel, 'Process')
}
$senhaSeguraGmail = $null
try {
    Write-Host 'Use uma senha de app do Google, nao a senha normal da conta.'
    Write-Host 'Crie em https://myaccount.google.com/apppasswords (requer verificacao em duas etapas).'
    $senhaSeguraGmail = Read-Host 'Senha de app do Gmail (entrada oculta)' -AsSecureString
    $env:GMAIL_APP_PASSWORD = ([System.Net.NetworkCredential]::new('', $senhaSeguraGmail)).Password -replace '\s',''
    if ($env:GMAIL_APP_PASSWORD.Length -ne 16) { throw 'A senha de app deve conter 16 caracteres, sem contar os espacos.' }
    $env:GMAIL_USERNAME = $Email
    $env:IMOBILIARIA_EMAIL = $Destinatario
    if (-not $env:ADMIN_EMAIL) { $env:ADMIN_EMAIL = 'admin@example.com' }
    if (-not $env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD = 'AdminTeste123!' }
    Write-Host 'Iniciando API com Gmail SMTP. A senha fica somente na memoria deste processo.'
    Write-Host 'Nenhum email de teste e enviado automaticamente. Use Tenho interesse ou Enviar ao cliente na interface.'
    Push-Location -LiteralPath $raizProjeto
    try {
        & mvn "-Dmaven.repo.local=$repositorioMaven" '-Dspring-boot.run.profiles=gmail' spring-boot:run
        if ($LASTEXITCODE -ne 0) { throw 'O backend foi encerrado com erro. Confira o log acima.' }
    } finally { Pop-Location }
} finally {
    foreach ($nomeVariavel in $variaveisAnteriores.Keys) {
        [Environment]::SetEnvironmentVariable($nomeVariavel, $variaveisAnteriores[$nomeVariavel], 'Process')
    }
    if ($null -ne $senhaSeguraGmail) { $senhaSeguraGmail.Dispose() }
}
