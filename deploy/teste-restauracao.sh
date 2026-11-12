#!/bin/sh
# Teste de restauração automatizado: ambiente limpo -> massa completa -> backup.sh -> destrói os volumes -> ambiente
# limpo -> restaurar.sh -> confere que tudo voltou igual e que o aceite da massa continua valendo (Playwright,
# frontend/e2e/restauracao.spec.ts). Num projeto do compose separado (fiscalhub-restauracao, porta 8088), com segredos e
# chave age descartáveis. Requer Docker e Node (npx playwright). Uso: deploy/teste-restauracao.sh
. "$(dirname "$0")/lib.sh"

export COMPOSE_PROJETO=fiscalhub-restauracao
export COMPOSE_ARQUIVOS="-f compose.yaml -f compose.dev.yaml -f deploy/compose.restauracao.yaml"
TRAB="$(mktemp -d)"
export ENV_ARQUIVO="$TRAB/.env"
export BACKUP_DIR="$TRAB/backups"
URL=http://localhost:8088

limpar() {
	dc down -v --remove-orphans >/dev/null 2>&1 || true
	rm -rf "$TRAB"
}
trap limpar EXIT

aleatorio() { openssl rand -base64 "$1" | tr -d '\n/+='; }
age_gerar_chave > "$TRAB/chave.txt"
PUBLICA="$(sed -n 's/^# public key: //p' "$TRAB/chave.txt")"
cat > "$ENV_ARQUIVO" <<EOF
DOMINIO=localhost
ACME_EMAIL=teste@localhost
UPLOAD_MAX_SIZE_MB=500
DB_PASSWORD=$(aleatorio 24)
JWT_SECRET=$(aleatorio 48)
CHAVE_DADOS=$(openssl rand -base64 32)
MINIO_ROOT_USER=fiscalhub-admin
MINIO_ROOT_PASSWORD=$(aleatorio 24)
S3_ACCESS_KEY=fiscalhub-app
S3_SECRET_KEY=$(aleatorio 24)
MINIO_KMS_SECRET_KEY=fiscalhub-sse:$(openssl rand -base64 32)
BACKUP_AGE_DESTINATARIO=$PUBLICA
EOF

playwright() {
	(cd "$RAIZ/frontend" && E2E_URL=$URL RESTAURACAO_FASE="$1" RESTAURACAO_ARQUIVO="$TRAB/estado.json" \
		npx playwright test e2e/restauracao.spec.ts --reporter=line)
}

log "1/5 ambiente limpo"
dc up -d --build --wait --wait-timeout 400
log "2/5 carga da massa"
playwright carga
log "3/5 backup"
BACKUP="$(sh "$RAIZ/deploy/backup.sh" | tail -n 1)"
[ -f "$BACKUP" ] || { echo "backup não gerado" >&2; exit 1; }
log "4/5 destruindo os volumes e subindo um ambiente vazio"
dc down -v
dc up -d --wait --wait-timeout 400
RESTAURAR_SEM_PERGUNTAR=1 sh "$RAIZ/deploy/restaurar.sh" "$BACKUP" "$TRAB/chave.txt"
log "5/5 conferência"
playwright conferencia
log "teste de restauração: OK ($(du -h "$BACKUP" | cut -f1) cifrados)"
