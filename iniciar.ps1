<#
.SYNOPSIS
    Inicializa o FiscalHub em ambiente local: confere pré-requisitos, prepara o
    backend/.env, garante o banco PostgreSQL e sobe o backend e o frontend.

.DESCRIPTION
    Substitui o antigo comandos_inicializacao.txt. Nenhum segredo fica neste script:
    a senha do banco e o JWT_SECRET ficam em backend/.env (não versionado), que o
    Spring Boot lê sozinho (spring.config.import=optional:file:.env).

    Na primeira execução, se o backend/.env não existir, o script:
      - copia o backend/.env.example;
      - pede a senha do PostgreSQL (digitação oculta);
      - gera um JWT_SECRET aleatório de 384 bits.

.PARAMETER Perfil
    Perfil do Spring: 'dev' (padrão; seeds de desenvolvimento, OpenAPI e logs
    detalhados) ou 'padrao' (sem perfil, como em produção).

.PARAMETER SemFrontend
    Sobe só o backend.

.PARAMETER PularInstalacao
    Não roda o 'npm ci' mesmo que as dependências do frontend estejam desatualizadas.

.PARAMETER NaoAbrirNavegador
    Não abre o navegador no fim.

.EXAMPLE
    .\iniciar.ps1
    .\iniciar.ps1 -SemFrontend
    .\iniciar.ps1 -Perfil padrao -NaoAbrirNavegador
#>
[CmdletBinding()]
param(
    [ValidateSet('dev', 'padrao')]
    [string]$Perfil = 'dev',
    [switch]$SemFrontend,
    [switch]$PularInstalacao,
    [switch]$NaoAbrirNavegador
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$Raiz        = $PSScriptRoot
$PastaBack   = Join-Path $Raiz 'backend'
$PastaFront  = Join-Path $Raiz 'frontend'
$ArquivoEnv  = Join-Path $PastaBack '.env'
$EnvExemplo  = Join-Path $PastaBack '.env.example'
$PortaBack   = 8080
$PortaFront  = 5173
$JavaMinimo  = 25
$NodeMinimo  = 20

# ── Saída ────────────────────────────────────────────────────────────────────
function Write-Etapa([string]$Texto) { Write-Host "`n▸ $Texto" -ForegroundColor Cyan }
function Write-Ok([string]$Texto)    { Write-Host "  ✓ $Texto" -ForegroundColor Green }
function Write-Aviso([string]$Texto) { Write-Host "  ! $Texto" -ForegroundColor Yellow }
function Stop-ComErro([string]$Texto) {
    Write-Host "`n✗ $Texto" -ForegroundColor Red
    exit 1
}

# ── Utilitários ──────────────────────────────────────────────────────────────

# Executa um programa externo sem que a saída de erro dele vire exceção do PowerShell.
function Invoke-Externo([string]$Programa, [string[]]$Argumentos) {
    $anterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $saida = & $Programa @Argumentos 2>&1 | ForEach-Object { "$_" }
        return [pscustomobject]@{ Codigo = $LASTEXITCODE; Saida = ($saida -join "`n") }
    } finally {
        $ErrorActionPreference = $anterior
    }
}

# Testa IPv4 e IPv6: o Vite, por padrão, escuta só em ::1 (localhost no Windows).
function Test-Porta([int]$Porta) {
    foreach ($endereco in @([System.Net.IPAddress]::Loopback, [System.Net.IPAddress]::IPv6Loopback)) {
        $cliente = New-Object System.Net.Sockets.TcpClient($endereco.AddressFamily)
        try {
            $tentativa = $cliente.BeginConnect($endereco, $Porta, $null, $null)
            if ($tentativa.AsyncWaitHandle.WaitOne(300) -and $cliente.Connected) { return $true }
        } catch {
            # porta fechada neste endereço; tenta o próximo
        } finally {
            $cliente.Close()
        }
    }
    return $false
}

function Wait-Porta([int]$Porta, [int]$Segundos, [string]$Nome) {
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        if (Test-Porta $Porta) { return }
        Start-Sleep -Seconds 2
        Write-Host '.' -NoNewline
    }
    Write-Host ''
    Stop-ComErro "$Nome não respondeu na porta $Porta em $Segundos s. Veja a janela '$Nome' para o erro."
}

function Get-VersaoMaior([string]$Texto) {
    if ($Texto -match '(\d+)(\.\d+)*') { return [int]$Matches[1] }
    return 0
}

# Lê o .env no formato de .properties (CHAVE=valor; linhas com # são comentário).
function Read-ArquivoEnv([string]$Caminho) {
    $valores = @{}
    foreach ($linha in Get-Content -LiteralPath $Caminho -Encoding UTF8) {
        if ($linha -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            $valores[$Matches[1]] = $Matches[2].Trim().Replace('\\', '\')
        }
    }
    return $valores
}

# Grava ou substitui CHAVE=valor no .env, preservando o restante do arquivo.
function Set-ValorEnv([string]$Caminho, [string]$Chave, [string]$Valor) {
    $escapado = $Valor.Replace('\', '\\')       # barra invertida é escape em .properties
    $linhas = [System.Collections.Generic.List[string]]@(Get-Content -LiteralPath $Caminho -Encoding UTF8)
    $indice = -1
    for ($i = 0; $i -lt $linhas.Count; $i++) {
        if ($linhas[$i] -match "^\s*$Chave\s*=") { $indice = $i; break }
    }
    if ($indice -ge 0) { $linhas[$indice] = "$Chave=$escapado" } else { $linhas.Add("$Chave=$escapado") }
    $utf8SemBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Caminho, $linhas, $utf8SemBom)
}

function New-SegredoJwt {
    $bytes = New-Object byte[] 48
    $gerador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $gerador.GetBytes($bytes) } finally { $gerador.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function Read-SenhaOculta([string]$Pergunta) {
    $segura = Read-Host -Prompt $Pergunta -AsSecureString
    $ponteiro = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ponteiro) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ponteiro) }
}

function Find-Psql {
    $comando = Get-Command psql -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }
    $instalados = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object { Get-VersaoMaior $_.Directory.Parent.Name } -Descending
    if ($instalados) { return $instalados[0].FullName }
    return $null
}

function Start-Janela([string]$Titulo, [string]$Pasta, [string]$Comando) {
    $script = "`$Host.UI.RawUI.WindowTitle = '$Titulo'; Set-Location -LiteralPath '$Pasta'; $Comando"
    Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoExit', '-NoProfile', '-Command', $script) | Out-Null
}

# ── 1. Pré-requisitos ────────────────────────────────────────────────────────
Write-Etapa 'Conferindo pré-requisitos'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Stop-ComErro "Java não encontrado. Instale o JDK $JavaMinimo ou superior." }
$saidaJava = (Invoke-Externo 'java' @('-version')).Saida
$versaoJava = if ($saidaJava -match 'version "(\d+)') { [int]$Matches[1] } else { 0 }
if ($versaoJava -lt $JavaMinimo) { Stop-ComErro "Java $versaoJava encontrado; o backend exige Java $JavaMinimo ou superior." }
Write-Ok "Java $versaoJava"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Stop-ComErro 'Maven (mvn) não encontrado no PATH.' }
Write-Ok 'Maven'

if (-not $SemFrontend) {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Stop-ComErro "Node.js não encontrado. Instale o Node $NodeMinimo ou superior." }
    $versaoNode = Get-VersaoMaior (Invoke-Externo 'node' @('--version')).Saida
    if ($versaoNode -lt $NodeMinimo) { Stop-ComErro "Node $versaoNode encontrado; o frontend exige Node $NodeMinimo ou superior." }
    Write-Ok "Node $versaoNode"
}

foreach ($ocupada in @(@{ Porta = $PortaBack; Nome = 'backend' }, @{ Porta = $PortaFront; Nome = 'frontend' })) {
    if ($SemFrontend -and $ocupada.Nome -eq 'frontend') { continue }
    if (Test-Porta $ocupada.Porta) {
        Stop-ComErro ("A porta $($ocupada.Porta) ($($ocupada.Nome)) já está em uso. Encerre o processo que a ocupa " +
            "(ex.: Get-NetTCPConnection -LocalPort $($ocupada.Porta) | Select OwningProcess) e rode o script de novo.")
    }
}
Write-Ok "Portas $PortaBack e $PortaFront livres"

# ── 2. backend/.env ──────────────────────────────────────────────────────────
Write-Etapa 'Preparando backend/.env'

if (-not (Test-Path -LiteralPath $ArquivoEnv)) {
    Copy-Item -LiteralPath $EnvExemplo -Destination $ArquivoEnv
    Write-Ok 'Criado a partir do .env.example'
}
$config = Read-ArquivoEnv $ArquivoEnv

if (-not $config['DB_PASSWORD']) {
    $senha = Read-SenhaOculta 'Senha do PostgreSQL (fica só em backend/.env)'
    if (-not $senha) { Stop-ComErro 'A senha do banco é obrigatória.' }
    Set-ValorEnv $ArquivoEnv 'DB_PASSWORD' $senha
    Write-Ok 'DB_PASSWORD gravada'
}
if (-not $config['JWT_SECRET'] -or $config['JWT_SECRET'].Length -lt 44) {
    Set-ValorEnv $ArquivoEnv 'JWT_SECRET' (New-SegredoJwt)
    Write-Ok 'JWT_SECRET novo gerado (tokens antigos deixam de valer)'
}
$config = Read-ArquivoEnv $ArquivoEnv
Write-Ok 'Variáveis obrigatórias presentes'

# ── 3. Banco de dados ────────────────────────────────────────────────────────
Write-Etapa 'Conferindo o banco PostgreSQL'

$urlBanco = if ($config['DB_URL']) { $config['DB_URL'] } else { 'jdbc:postgresql://localhost:5432/fiscalhub' }
if ($urlBanco -notmatch '^jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)') { Stop-ComErro "DB_URL inválida: $urlBanco" }
$hostBanco = $Matches[1]
$portaBanco = if ($Matches[2]) { $Matches[2] } else { '5432' }
$nomeBanco = $Matches[3]
if ($nomeBanco -notmatch '^[a-z_][a-z0-9_]*$') { Stop-ComErro "Nome de banco '$nomeBanco' fora do padrão (letras minúsculas, dígitos e _)." }
$usuarioBanco = if ($config['DB_USERNAME']) { $config['DB_USERNAME'] } else { 'postgres' }

$psql = Find-Psql
if (-not $psql) {
    Write-Aviso "psql não encontrado; não dá para conferir se o banco '$nomeBanco' existe. Se não existir, crie-o antes."
} else {
    $env:PGPASSWORD = $config['DB_PASSWORD']        # só neste processo, removida logo abaixo
    try {
        $base = @('-h', $hostBanco, '-p', $portaBanco, '-U', $usuarioBanco, '-d', 'postgres', '-tAc')
        $consulta = Invoke-Externo $psql ($base + "SELECT 1 FROM pg_database WHERE datname = '$nomeBanco'")
        if ($consulta.Codigo -ne 0) {
            Stop-ComErro ("Não consegui conectar ao PostgreSQL em ${hostBanco}:$portaBanco como '$usuarioBanco'.`n" +
                "  Confira se o serviço está rodando (services.msc) e a DB_PASSWORD em backend/.env.`n  Detalhe: $($consulta.Saida)")
        }
        if ($consulta.Saida.Trim() -eq '1') {
            Write-Ok "Banco '$nomeBanco' encontrado em ${hostBanco}:$portaBanco"
        } else {
            $criacao = Invoke-Externo $psql ($base + "CREATE DATABASE $nomeBanco")
            if ($criacao.Codigo -ne 0) { Stop-ComErro "Falha ao criar o banco '$nomeBanco': $($criacao.Saida)" }
            Write-Ok "Banco '$nomeBanco' criado (o Flyway cria as tabelas na subida)"
        }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

# ── 4. Dependências do frontend ──────────────────────────────────────────────
if (-not $SemFrontend) {
    Write-Etapa 'Conferindo dependências do frontend'
    $lockInstalado = Join-Path $PastaFront 'node_modules\.package-lock.json'
    $lockProjeto = Join-Path $PastaFront 'package-lock.json'
    $desatualizado = -not (Test-Path -LiteralPath $lockInstalado) -or
        ((Get-Item -LiteralPath $lockProjeto).LastWriteTime -gt (Get-Item -LiteralPath $lockInstalado).LastWriteTime)

    if ($desatualizado -and -not $PularInstalacao) {
        Push-Location -LiteralPath $PastaFront
        try {
            Write-Host '  Rodando npm ci (pode levar alguns minutos)...'
            $npm = Invoke-Externo 'npm.cmd' @('ci', '--no-audit', '--no-fund')
            if ($npm.Codigo -ne 0) { Stop-ComErro "npm ci falhou:`n$($npm.Saida)" }
        } finally { Pop-Location }
        Write-Ok 'Dependências instaladas'
    } elseif ($desatualizado) {
        Write-Aviso 'Dependências desatualizadas, mas -PularInstalacao foi informado.'
    } else {
        Write-Ok 'Dependências em dia'
    }
}

# ── 5. Backend ───────────────────────────────────────────────────────────────
Write-Etapa "Subindo o backend (perfil: $Perfil)"
$argPerfil = if ($Perfil -eq 'dev') { " '-Dspring-boot.run.profiles=dev'" } else { '' }
Start-Janela 'FiscalHub - backend' $PastaBack "mvn spring-boot:run$argPerfil"
Write-Host '  Aguardando a API' -NoNewline
Wait-Porta $PortaBack 240 'FiscalHub - backend'
Write-Host ''
Write-Ok "API no ar em http://localhost:$PortaBack"

# ── 6. Frontend ──────────────────────────────────────────────────────────────
if (-not $SemFrontend) {
    Write-Etapa 'Subindo o frontend'
    Start-Janela 'FiscalHub - frontend' $PastaFront 'npm run dev'
    Write-Host '  Aguardando o Vite' -NoNewline
    Wait-Porta $PortaFront 90 'FiscalHub - frontend'
    Write-Host ''
    Write-Ok "Frontend no ar em http://localhost:$PortaFront"
    if (-not $NaoAbrirNavegador) { Start-Process "http://localhost:$PortaFront" }
}

# ── Resumo ───────────────────────────────────────────────────────────────────
Write-Host "`nFiscalHub pronto." -ForegroundColor Green
Write-Host "  Backend : http://localhost:$PortaBack"
if ($Perfil -eq 'dev') { Write-Host "  OpenAPI : http://localhost:$PortaBack/v3/api-docs" }
if (-not $SemFrontend) {
    Write-Host "  Frontend: http://localhost:$PortaFront"
    if ($Perfil -eq 'dev') { Write-Host "  Design  : http://localhost:$PortaFront/laboratorio-design" }
}
Write-Host "  Para parar, feche as janelas 'FiscalHub - backend' e 'FiscalHub - frontend' (ou Ctrl+C nelas)."
