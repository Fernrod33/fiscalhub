<#
.SYNOPSIS
    Gera versões anonimizadas de PDFs e SPED (.txt) reais para enviar ao suporte do FiscalHub (layout não reconhecido).

.DESCRIPTION
    Mesma estrutura, dados trocados: CNPJ e CPF por fictícios válidos (consistentes entre os arquivos), nomes, endereços,
    e-mails e telefones dos registros de identificação do SPED, e esses mesmos nomes nos PDFs. Os valores ficam iguais
    (ou multiplicados por -Escala). Roda no próprio computador, pelo Docker: nenhum arquivo sai da máquina.
    Revise o resultado antes de enviar: nome que não está num registro de identificação nem em -Nomes continua igual.

.PARAMETER Arquivos
    Os arquivos (.pdf e .txt). Envie juntos os do mesmo cliente: os nomes achados no SPED valem para os PDFs.

.PARAMETER Saida
    Pasta dos anonimizados (padrão: .\anonimizados).

.PARAMETER Nomes
    Arquivo de texto com outros nomes a trocar, um por linha (ex.: sócios que aparecem só no PDF).

.PARAMETER Escala
    Multiplica os valores em reais (ex.: 0.37). Quebra as somas do SPED: use só para PDF.

.EXAMPLE
    .\anonimizar.ps1 C:\cliente\relatorio-situacao-fiscal.pdf C:\cliente\ecd-2025.txt
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(Mandatory, Position = 0, ValueFromRemainingArguments)][string[]]$Arquivos,
    [string]$Saida = (Join-Path (Get-Location) 'anonimizados'),
    [string]$Nomes,
    [string]$Escala
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Precisa do Docker Desktop.' }
$imagem = 'fiscalhub-backend:local'
docker image inspect $imagem *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Montando a imagem do backend (uma vez só, alguns minutos)...'
    docker build -t $imagem (Join-Path $PSScriptRoot 'backend')
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao montar a imagem.' }
}
New-Item -ItemType Directory -Force $Saida | Out-Null

# cada arquivo entra numa pasta só de leitura; a saída é a única pasta gravável
$montagens = @('-v', "$((Resolve-Path $Saida).Path):/saida")
$argumentos = @('--saida', '/saida')
$i = 0
foreach ($a in $Arquivos) {
    $f = Get-Item -LiteralPath $a
    $montagens += @('-v', "$($f.FullName):/entrada/$i/$($f.Name):ro")
    $argumentos += "/entrada/$i/$($f.Name)"
    $i++
}
if ($Nomes) { $montagens += @('-v', "$((Resolve-Path $Nomes).Path):/nomes.txt:ro"); $argumentos += @('--nomes', '/nomes.txt') }
if ($Escala) { $argumentos += @('--escala', $Escala) }

# a saída de erro do Java não é falha (o código de saída é que diz)
$ErrorActionPreference = 'Continue'
docker run --rm --network none -e JAVA_TOOL_OPTIONS= @montagens --entrypoint java $imagem -cp /app `
    '-Dloader.main=com.fiscalhub.backend.ferramentas.Anonimizador' org.springframework.boot.loader.launch.PropertiesLauncher @argumentos
if ($LASTEXITCODE -ne 0) { throw 'A anonimização falhou.' }
Write-Host "Pronto: $Saida (revise antes de enviar)" -ForegroundColor Green
