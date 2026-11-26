#!/bin/sh
# Teste de restauração automatizado: ambiente limpo -> massa completa -> backup.sh (com envio ao bucket externo) ->
# apaga a cópia local e destrói os volumes -> ambiente limpo -> baixa do bucket externo com a credencial de leitura ->
# restaurar.sh -> confere que tudo voltou igual e que o aceite da massa continua valendo (Playwright,
# frontend/e2e/restauracao.spec.ts). O bucket externo é um MinIO (s3-externo) com Object Lock em governance de 35 dias e
# um usuário só com PutObject, como o da AWS (docs/deploy/hostinger.md). Num projeto do compose separado (fiscalhub-restauracao, porta 8088), com segredos e
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
S3_EXTERNO_LEITURA_ID=backup-leitura
S3_EXTERNO_LEITURA_SEGREDO=$(aleatorio 24)
BACKUP_S3_BUCKET=fiscalhub-backups
BACKUP_S3_ACCESS_KEY=backup-envio
BACKUP_S3_SECRET_KEY=$(aleatorio 24)
BACKUP_S3_ENDPOINT=http://host.docker.internal:9100
EOF

# o bucket externo como na AWS: versionado, Object Lock (governance, 35 dias) e a credencial do servidor só com PutObject
preparar_s3_externo() {
	# shellcheck disable=SC2016 # as variáveis expandem dentro do contêiner
	dc exec -T -e BUCKET="$(env_valor BACKUP_S3_BUCKET)" -e ID="$(env_valor BACKUP_S3_ACCESS_KEY)" \
		-e SEGREDO="$(env_valor BACKUP_S3_SECRET_KEY)" s3-externo sh -eu -c '
		mc mb --with-lock "local/$BUCKET"
		mc retention set --default GOVERNANCE 35d "local/$BUCKET"
		printf "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"s3:PutObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}" "$BUCKET" > /tmp/envio.json
		mc admin policy create local backup-envio /tmp/envio.json
		mc admin user add local "$ID" "$SEGREDO"
		mc admin policy attach local backup-envio --user "$ID"'
}
# o mesmo aws do backup.sh, com a credencial de envio (a do servidor) ou a de leitura (fora do servidor)
como_envio() {
	AWS_ACCESS_KEY_ID="$(env_valor BACKUP_S3_ACCESS_KEY)" AWS_SECRET_ACCESS_KEY="$(env_valor BACKUP_S3_SECRET_KEY)" \
		AWS_ENDPOINT_URL="$(env_valor BACKUP_S3_ENDPOINT)" aws_ "$TRAB" "$@"
}
como_leitura() {
	AWS_ACCESS_KEY_ID="$(env_valor S3_EXTERNO_LEITURA_ID)" AWS_SECRET_ACCESS_KEY="$(env_valor S3_EXTERNO_LEITURA_SEGREDO)" \
		AWS_ENDPOINT_URL="$(env_valor BACKUP_S3_ENDPOINT)" aws_ "$TRAB" "$@"
}
falha_esperada() { # $1 = o que deveria ser recusado; o resto, o comando
	o_que="$1"
	shift
	if "$@" >/dev/null 2>&1; then echo "ERRO: $o_que deveria ser recusado" >&2; exit 1; fi
	log "recusado, como esperado: $o_que"
}

playwright() {
	(cd "$RAIZ/frontend" && E2E_URL=$URL RESTAURACAO_FASE="$1" RESTAURACAO_ARQUIVO="$TRAB/estado.json" \
		npx playwright test e2e/restauracao.spec.ts --reporter=line)
}

log "1/5 ambiente limpo"
dc up -d --build --wait --wait-timeout 400
preparar_s3_externo
log "2/5 carga da massa"
playwright carga
log "3/5 backup"
BACKUP="$(sh "$RAIZ/deploy/backup.sh" | tail -n 1)"
[ -f "$BACKUP" ] || { echo "backup não gerado" >&2; exit 1; }
NOME="$(basename "$BACKUP")"
BUCKET_EXTERNO="$(env_valor BACKUP_S3_BUCKET)"
falha_esperada "leitura com a credencial do servidor" como_envio s3 cp "s3://$BUCKET_EXTERNO/diario/$NOME" /dados/lido
falha_esperada "exclusão com a credencial do servidor" como_envio s3 rm "s3://$BUCKET_EXTERNO/diario/$NOME"
VERSAO="$(como_leitura s3api list-object-versions --bucket "$BUCKET_EXTERNO" --prefix "diario/$NOME" \
	--query 'Versions[0].VersionId' --output text | tr -d '\r')"
case "$VERSAO" in "" | None) echo "ERRO: versão do backup não encontrada no bucket externo" >&2; exit 1 ;; esac
falha_esperada "exclusão da versão travada (Object Lock), mesmo com a credencial de leitura" \
	como_leitura s3api delete-object --bucket "$BUCKET_EXTERNO" --key "diario/$NOME" --version-id "$VERSAO"
log "4/5 apagando a cópia local, destruindo os volumes (o s3-externo fica) e subindo um ambiente vazio"
rm -rf "$BACKUP_DIR"
dc rm -fsv backend frontend proxy postgres minio minio-init mailpit >/dev/null
docker volume rm "${COMPOSE_PROJETO}_postgres" "${COMPOSE_PROJETO}_minio" >/dev/null
dc up -d --wait --wait-timeout 400
log "baixando do bucket externo com a credencial de leitura"
como_leitura s3 cp --only-show-errors "s3://$BUCKET_EXTERNO/diario/$NOME" "/dados/$NOME"
RESTAURAR_SEM_PERGUNTAR=1 sh "$RAIZ/deploy/restaurar.sh" "$TRAB/$NOME" "$TRAB/chave.txt"
log "5/5 conferência"
playwright conferencia
log "teste de restauração: OK ($(du -h "$TRAB/$NOME" | cut -f1) cifrados, ida e volta pelo bucket externo)"
