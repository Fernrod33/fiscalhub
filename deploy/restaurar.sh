#!/bin/sh
# Restaura um backup do backup.sh: banco (substitui o atual) e bucket (espelho: arquivos que não estão no backup saem).
#   deploy/restaurar.sh /var/backups/fiscalhub/diario/fiscalhub-AAAAMMDD-HHMMSS.tar.age /caminho/da/chave-privada.txt
# O backend fica parado durante a restauração e volta no fim. Destrutivo: confirme com RESTAURAR_SEM_PERGUNTAR=1 ou
# digitando "restaurar".
. "$(dirname "$0")/lib.sh"

ARQUIVO="${1:?uso: restaurar.sh <backup.tar.age> <chave-privada-age>}"
CHAVE="${2:?informe o arquivo da chave privada do age}"
BUCKET="$(env_valor S3_BUCKET)"
BUCKET="${BUCKET:-fiscalhub-documentos}"

if [ "${RESTAURAR_SEM_PERGUNTAR:-}" != 1 ]; then
	printf 'Isto substitui o banco e o bucket do projeto %s por %s. Digite "restaurar": ' "$COMPOSE_PROJETO" "$ARQUIVO"
	read -r resposta
	[ "$resposta" = restaurar ] || { echo "cancelado"; exit 1; }
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
log "decifrando"
age_decifrar "$CHAVE" < "$ARQUIVO" | tar -C "$TMP" -xf -
PASTA="$(find "$TMP" -mindepth 1 -maxdepth 1 -type d -name 'fiscalhub-*' | head -n 1)"
[ -f "$PASTA/banco.dump" ] || { echo "backup sem banco.dump" >&2; exit 1; }

log "parando o backend"
dc stop backend
dc up -d --wait postgres minio
log "recriando o banco"
dc exec -T postgres psql -U fiscalhub -d postgres -v ON_ERROR_STOP=1 \
	-c 'DROP DATABASE IF EXISTS fiscalhub WITH (FORCE)' -c 'CREATE DATABASE fiscalhub OWNER fiscalhub'
dc exec -T postgres pg_restore -U fiscalhub -d fiscalhub --no-owner --exit-on-error < "$PASTA/banco.dump"
log "restaurando o bucket $BUCKET"
mc_ "$PASTA" mirror --quiet --overwrite --remove /dados/minio "local/$BUCKET"
log "subindo o backend"
dc up -d --wait backend
log "restauração concluída"
