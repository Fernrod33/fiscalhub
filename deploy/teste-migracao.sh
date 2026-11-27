#!/bin/sh
# Teste do serviço "migracao" no compose de produção (compose.yaml, perfil prod), num projeto separado e com segredos
# descartáveis: (1) o backend não recebe a senha do dono; (2) uma migração nova é aplicada pela migracao, como dono, e
# o papel da aplicação ganha acesso a ela; (3) se a migração falha, o backend não sobe; (4) corrigida, ele sobe;
# (5) atualização pelo procedimento do runbook (seção 3) para uma versão com migração quebrada: a migração falha e o
# backend da versão anterior continua no ar e respondendo, sem nada da migração no banco; (6) corrigida, a versão nova
# sobe. As migrações extras vêm de uma pasta montada; a "versão nova" é a imagem com uma migração a mais dentro, com a
# mesma tag, como num deploy. Requer Docker. Uso: deploy/teste-migracao.sh
. "$(dirname "$0")/lib.sh"

export COMPOSE_PROJETO=fiscalhub-migracao
# tag própria: a imagem é refeita no passo 5 sem tocar na fiscalhub-backend:local do desenvolvimento
export VERSAO=teste-migracao
IMAGEM="fiscalhub-backend:$VERSAO"
TRAB="$(mktemp -d)"
export ENV_ARQUIVO="$TRAB/.env"
COMPOSE_ARQUIVOS="-f compose.yaml -f $(caminho_docker "$TRAB")/migracoes-extras.yaml"
export COMPOSE_ARQUIVOS

limpar() {
	dc down -v --remove-orphans >/dev/null 2>&1 || true
	docker image rm -f "$IMAGEM" >/dev/null 2>&1 || true
	rm -rf "$TRAB"
}
trap limpar EXIT

aleatorio() { openssl rand -base64 "$1" | tr -d '\n/+='; }
cat > "$ENV_ARQUIVO" <<EOF
DOMINIO=localhost
ACME_EMAIL=teste@localhost
DB_PASSWORD=$(aleatorio 24)
DB_APP_PASSWORD=$(aleatorio 24)
JWT_SECRET=$(aleatorio 48)
CHAVE_DADOS=$(openssl rand -base64 32)
MINIO_ROOT_USER=fiscalhub-admin
MINIO_ROOT_PASSWORD=$(aleatorio 24)
S3_ACCESS_KEY=fiscalhub-app
S3_SECRET_KEY=$(aleatorio 24)
MINIO_KMS_SECRET_KEY=fiscalhub-sse:$(openssl rand -base64 32)
EOF
mkdir "$TRAB/extras"
cat > "$TRAB/migracoes-extras.yaml" <<EOF
services:
  migracao:
    environment:
      FLYWAY_LOCATIONS: classpath:db/migration,filesystem:/extras
    volumes:
      - $(caminho_docker "$TRAB")/extras:/extras:ro
EOF

falha() { echo "FALHA: $*" >&2; exit 1; }
sql() { dc exec -T postgres psql -U fiscalhub -d fiscalhub -tAc "$1" | tr -d '\r'; }
rodando() { [ -n "$(dc ps -q --status running backend)" ]; }
no_ar() { dc exec -T backend curl -fsS http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1; }
# versão nova: a imagem atual com uma migração a mais em db/migration, com a mesma tag (como um build de outra versão)
versao_nova() { # $1 = conteúdo da V1002
	mkdir -p "$TRAB/b"
	printf '%s\n' "$1" > "$TRAB/b/V1002__versao_nova.sql"
	printf 'FROM %s\nCOPY V1002__versao_nova.sql /app/BOOT-INF/classes/db/migration/\n' "$IMAGEM" \
		| docker build -q -t "$IMAGEM" -f - "$(caminho_docker "$TRAB/b")" >/dev/null
}

log "1/6 migração nova e subida do backend (perfil prod)"
printf 'CREATE TABLE teste_migracao_nova (id INTEGER);\n' > "$TRAB/extras/V1000__teste_migracao_nova.sql"
dc up -d --build --wait --wait-timeout 400 backend

dc exec -T backend env | grep -q '^DB_PASSWORD=' && falha "o backend recebeu a DB_PASSWORD"
dc exec -T backend env | grep -q '^FLYWAY_' && falha "o backend recebeu variáveis do Flyway"
dc exec -T backend env | grep -q '^DB_APP_PASSWORD=' || falha "o backend sem a DB_APP_PASSWORD"
log "ok: o backend só tem a senha do papel da aplicação"

V1000="$(sql "SELECT installed_by || '|' || success FROM flyway_schema_history WHERE version = '1000'")"
[ "$V1000" = "fiscalhub|true" ] || falha "a V1000 não foi aplicada pela migracao como dono (histórico: '$V1000'; $(dc logs --no-log-prefix migracao | tail -n 5))"
[ "$(sql "SELECT has_table_privilege('fiscalhub_app', 'teste_migracao_nova', 'SELECT')")" = t ] \
	|| falha "o papel da aplicação não ganhou acesso à tabela nova (afterMigrate)"
dc logs backend | grep -q 'Migrating schema' && falha "o backend rodou o Flyway"
log "ok: a V1000 foi aplicada pela migracao (como fiscalhub) e o backend não migrou"

log "2/6 migração que falha: o backend não pode subir"
dc rm -fs backend >/dev/null
printf 'SELECT 1/0;\n' > "$TRAB/extras/V1001__falha.sql"
if dc up -d --wait --wait-timeout 400 backend >"$TRAB/up.log" 2>&1; then falha "o compose subiu com a migração falhando"; fi
rodando && falha "o backend subiu com a migração falhando"
dc logs migracao | grep -q 'division by zero' || falha "o log da migracao não mostra o erro"
[ "$(sql "SELECT count(*) FROM flyway_schema_history WHERE version = '1001'")" = 0 ] || falha "a V1001 ficou no histórico"
log "ok: migração falhou, backend parado, nada da V1001 no banco"

log "3/6 migração corrigida: o backend sobe"
printf 'CREATE TABLE teste_migracao_corrigida (id INTEGER);\n' > "$TRAB/extras/V1001__falha.sql"
dc up -d --wait --wait-timeout 400 backend
rodando || falha "o backend não subiu depois da correção"

no_ar || falha "o backend corrigido não responde"

log "4/6 atualização para uma versão com migração quebrada, pelo procedimento do runbook"
A="$(dc ps -q backend)"
versao_nova "CREATE TABLE teste_versao_quebrada (id INTEGER);
SELECT 1/0;"
# vigia: o backend da versão anterior precisa responder a cada segundo enquanto a migração da nova roda
: > "$TRAB/quedas"
: > "$TRAB/respostas"
( while [ ! -f "$TRAB/fim" ]; do if no_ar; then echo ok >> "$TRAB/respostas"; else echo queda >> "$TRAB/quedas"; fi; sleep 1; done ) &
VIGIA=$!
if dc run --rm migracao >"$TRAB/migracao-b.log" 2>&1; then falha "a migração quebrada passou"; fi
sleep 3
touch "$TRAB/fim"
wait "$VIGIA"
grep -q 'division by zero' "$TRAB/migracao-b.log" || falha "o log da migração não mostra o erro"
[ "$(wc -l < "$TRAB/respostas")" -ge 3 ] || falha "a vigia não chegou a conferir o backend durante a migração"
[ -s "$TRAB/quedas" ] && falha "o backend anterior deixou de responder $(wc -l < "$TRAB/quedas") vez(es) durante a migração"
[ "$(dc ps -q backend)" = "$A" ] || falha "o contêiner do backend anterior foi trocado"
no_ar || falha "o backend anterior não responde depois da falha"
[ "$(sql "SELECT to_regclass('teste_versao_quebrada') IS NULL")" = t ] || falha "a tabela da migração quebrada ficou no banco"
[ "$(sql "SELECT count(*) FROM flyway_schema_history WHERE version = '1002'")" = 0 ] || falha "a V1002 ficou no histórico"
log "ok: migração da versão nova falhou; a anterior respondeu nas $(wc -l < "$TRAB/respostas") conferências, sem queda; nada da V1002 no banco"

log "5/6 versão nova corrigida: migração e então o backend novo"
versao_nova "CREATE TABLE teste_versao_corrigida (id INTEGER);"
dc run --rm migracao >"$TRAB/migracao-b2.log" 2>&1 || falha "a migração corrigida falhou: $(tail -n 5 "$TRAB/migracao-b2.log")"
dc up -d --wait --wait-timeout 400 backend
[ "$(dc ps -q backend)" != "$A" ] || falha "o backend não foi trocado pela versão nova"
no_ar || falha "o backend da versão nova não responde"
[ "$(sql "SELECT count(*) FROM flyway_schema_history WHERE version = '1002' AND success")" = 1 ] || falha "a V1002 corrigida não foi aplicada"
log "ok: versão nova no ar com a V1002"

log "6/6 teste da migração: OK"
