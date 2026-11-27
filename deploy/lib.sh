#!/bin/sh
# Funções comuns aos scripts de operação (backup.sh, restaurar.sh, teste-restauracao.sh). Todos usam o mesmo compose:
# COMPOSE_ARQUIVOS e COMPOSE_PROJETO mudam o alvo (o padrão é a produção na raiz do repositório).
set -eu

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_ARQUIVOS="${COMPOSE_ARQUIVOS:--f compose.yaml}"
COMPOSE_PROJETO="${COMPOSE_PROJETO:-fiscalhub}"
ENV_ARQUIVO="${ENV_ARQUIVO:-$RAIZ/.env}"

dc() {
	# shellcheck disable=SC2086 # COMPOSE_ARQUIVOS são vários -f separados por espaço
	(cd "$RAIZ" && docker compose -p "$COMPOSE_PROJETO" --env-file "$(caminho_docker "$(dirname "$ENV_ARQUIVO")")/$(basename "$ENV_ARQUIVO")" \
		$COMPOSE_ARQUIVOS "$@")
}

# variável do .env (sem exportar o arquivo inteiro para o ambiente)
env_valor() {
	sed -n "s/^$1=//p" "$ENV_ARQUIVO" | tail -n 1
}

# Caminho que o Docker entende (no Git Bash do Windows, o caminho do Windows; no Linux, o próprio)
caminho_docker() {
	(cd "$1" && (pwd -W 2>/dev/null || pwd))
}

# age (https://age-encryption.org): o do sistema ou, sem ele, numa imagem Alpine descartável. stdin -> stdout.
AGE_IMAGEM="alpine:3.22"
age_cifrar() { # $1 = chave pública (age1...)
	if command -v age >/dev/null 2>&1; then
		age -r "$1"
	else
		docker run --rm -i "$AGE_IMAGEM" sh -c 'apk add --no-cache -q age >/dev/null && age -r "$0"' "$1"
	fi
}
age_decifrar() { # $1 = arquivo da chave privada
	if command -v age >/dev/null 2>&1; then
		age -d -i "$1"
	else
		MSYS_NO_PATHCONV=1 docker run --rm -i -v "$(caminho_docker "$(dirname "$1")"):/chaves:ro" "$AGE_IMAGEM" \
			sh -c 'apk add --no-cache -q age >/dev/null && age -d -i "/chaves/$0"' "$(basename "$1")"
	fi
}
age_gerar_chave() { # stdout: a chave privada (a pública vai num comentário "# public key: age1...")
	if command -v age-keygen >/dev/null 2>&1; then
		age-keygen 2>/dev/null
	else
		docker run --rm "$AGE_IMAGEM" sh -c 'apk add --no-cache -q age >/dev/null && age-keygen 2>/dev/null'
	fi
}

# AWS CLI numa imagem fixada. Credenciais só por variável de ambiente (nunca na linha de comando nem em arquivo).
# host.docker.internal: no teste, o "S3" é um MinIO publicado no host (sem efeito na AWS de verdade).
AWS_IMAGEM="amazon/aws-cli:2.42.4@sha256:4a1a668837d5f97ced202e03827da1bc0b48cda946be89909cd7a02023f62921"
aws_() { # $1 = pasta montada em /dados; o resto, o comando do aws; AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY no ambiente
	pasta="$1"
	shift
	MSYS_NO_PATHCONV=1 docker run --rm --add-host host.docker.internal:host-gateway -v "$(caminho_docker "$pasta"):/dados" 		-e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-sa-east-1}" 		${AWS_ENDPOINT_URL:+-e AWS_ENDPOINT_URL="$AWS_ENDPOINT_URL"} "$AWS_IMAGEM" "$@"
}

# mc (cliente do MinIO) no contêiner minio-init, com a pasta local em /dados
mc_() {
	pasta="$1"
	shift
	usuario=""
	if [ "$(uname -s)" = Linux ]; then usuario="--user $(id -u):$(id -g)"; fi
	# shellcheck disable=SC2086
	MSYS_NO_PATHCONV=1 dc run --rm --no-deps $usuario -v "$(caminho_docker "$pasta"):/dados" -e MC_CONFIG_DIR=/tmp/mc \
		--entrypoint mc minio-init "$@"
}

log() {
	printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}
