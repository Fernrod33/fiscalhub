#!/bin/sh
# Backup do FiscalHub: pg_dump + espelho do bucket do MinIO, num tar cifrado com age (chave pública no .env), com
# retenção diária/semanal/mensal e cópia para um destino externo. Agendar no servidor (crontab do usuário do deploy):
#   0 3 * * * /opt/fiscalhub/deploy/backup.sh >> /var/log/fiscalhub-backup.log 2>&1
# Variáveis (.env): BACKUP_AGE_DESTINATARIO (obrigatória: a chave pública "age1..."; a privada fica fora do servidor),
# BACKUP_DIR (/var/backups/fiscalhub), BACKUP_RETER_DIARIO (7), BACKUP_RETER_SEMANAL (4), BACKUP_RETER_MENSAL (6),
# BACKUP_DESTINO_RSYNC (ex.: backup@outro-servidor:/backups/fiscalhub) e/ou BACKUP_DESTINO_RCLONE (ex.: b2:fiscalhub)
# e/ou o bucket S3 externo: BACKUP_S3_BUCKET, BACKUP_S3_ACCESS_KEY e BACKUP_S3_SECRET_KEY (credencial só com PutObject
# nesse bucket: versionamento e Object Lock do lado da AWS impedem apagar ou sobrescrever), BACKUP_S3_REGIAO (sa-east-1)
# e BACKUP_S3_ENDPOINT (só no teste, para um MinIO no papel do S3). Passo a passo: docs/deploy/hostinger.md.
. "$(dirname "$0")/lib.sh"

DESTINATARIO="$(env_valor BACKUP_AGE_DESTINATARIO)"
[ -n "$DESTINATARIO" ] || { echo "Defina BACKUP_AGE_DESTINATARIO no .env (age-keygen gera o par; a privada fica fora do servidor)." >&2; exit 1; }
DIR="${BACKUP_DIR:-$(env_valor BACKUP_DIR)}"
DIR="${DIR:-/var/backups/fiscalhub}"
BUCKET="$(env_valor S3_BUCKET)"
BUCKET="${BUCKET:-fiscalhub-documentos}"
CARIMBO="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$DIR/diario" "$DIR/semanal" "$DIR/mensal"
TMP="$(mktemp -d "$DIR/.trabalho-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/fiscalhub-$CARIMBO/minio"

log "pg_dump"
dc exec -T postgres pg_dump -U fiscalhub -d fiscalhub -Fc > "$TMP/fiscalhub-$CARIMBO/banco.dump"
log "espelho do bucket $BUCKET"
mc_ "$TMP/fiscalhub-$CARIMBO" mirror --quiet --overwrite "local/$BUCKET" /dados/minio
printf 'banco: pg_dump -Fc do PostgreSQL\nminio: espelho do bucket %s (versões atuais)\ndata: %s\n' "$BUCKET" "$CARIMBO" \
	> "$TMP/fiscalhub-$CARIMBO/LEIAME.txt"

ARQUIVO="$DIR/diario/fiscalhub-$CARIMBO.tar.age"
log "cifrando para $ARQUIVO"
tar -C "$TMP" -cf - "fiscalhub-$CARIMBO" | age_cifrar "$DESTINATARIO" > "$ARQUIVO.parcial"
mv "$ARQUIVO.parcial" "$ARQUIVO"

# semanal no domingo, mensal no dia 1º (cópias do diário de hoje)
[ "$(date +%u)" = 7 ] && cp "$ARQUIVO" "$DIR/semanal/"
[ "$(date +%d)" = 01 ] && cp "$ARQUIVO" "$DIR/mensal/"

reter() {
	n="$(env_valor "$2")"
	n="${n:-$3}"
	# os nomes têm a data: a ordem alfabética é a cronológica
	find "$DIR/$1" -maxdepth 1 -name "*.tar.age" | sort -r | tail -n +"$((n + 1))" | while read -r velho; do rm -f "$velho"; done
}
reter diario BACKUP_RETER_DIARIO 7
reter semanal BACKUP_RETER_SEMANAL 4
reter mensal BACKUP_RETER_MENSAL 6

RSYNC="$(env_valor BACKUP_DESTINO_RSYNC)"
RCLONE="$(env_valor BACKUP_DESTINO_RCLONE)"
if [ -n "$RSYNC" ]; then log "rsync para $RSYNC"; rsync -a --delete "$DIR/" "$RSYNC/"; fi
if [ -n "$RCLONE" ]; then log "rclone para $RCLONE"; rclone sync "$DIR" "$RCLONE"; fi
S3="$(env_valor BACKUP_S3_BUCKET)"
if [ -n "$S3" ]; then
	log "enviando para s3://$S3/diario/"
	# só PutObject (aws s3 cp sobe em partes acima de 8 MB, tudo coberto por s3:PutObject); nada é listado nem apagado:
	# a retenção lá fora é a do Object Lock e a regra de ciclo de vida do bucket
	AWS_ACCESS_KEY_ID="$(env_valor BACKUP_S3_ACCESS_KEY)" AWS_SECRET_ACCESS_KEY="$(env_valor BACKUP_S3_SECRET_KEY)" 		AWS_DEFAULT_REGION="$(env_valor BACKUP_S3_REGIAO)" AWS_ENDPOINT_URL="$(env_valor BACKUP_S3_ENDPOINT)" 		aws_ "$(dirname "$ARQUIVO")" s3 cp --only-show-errors "/dados/$(basename "$ARQUIVO")" "s3://$S3/diario/$(basename "$ARQUIVO")"
fi
[ -n "$RSYNC$RCLONE$S3" ] || log "AVISO: sem destino externo (BACKUP_S3_BUCKET, BACKUP_DESTINO_RSYNC ou BACKUP_DESTINO_RCLONE): o backup está só neste servidor"

log "ok: $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"
echo "$ARQUIVO"
