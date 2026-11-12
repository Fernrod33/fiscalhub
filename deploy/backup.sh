#!/bin/sh
# Backup do FiscalHub: pg_dump + espelho do bucket do MinIO, num tar cifrado com age (chave pública no .env), com
# retenção diária/semanal/mensal e cópia para um destino externo. Agendar no servidor (crontab do usuário do deploy):
#   0 3 * * * /opt/fiscalhub/deploy/backup.sh >> /var/log/fiscalhub-backup.log 2>&1
# Variáveis (.env): BACKUP_AGE_DESTINATARIO (obrigatória: a chave pública "age1..."; a privada fica fora do servidor),
# BACKUP_DIR (/var/backups/fiscalhub), BACKUP_RETER_DIARIO (7), BACKUP_RETER_SEMANAL (4), BACKUP_RETER_MENSAL (6),
# BACKUP_DESTINO_RSYNC (ex.: backup@outro-servidor:/backups/fiscalhub) e/ou BACKUP_DESTINO_RCLONE (ex.: b2:fiscalhub).
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
	ls -1 "$DIR/$1"/*.tar.age 2>/dev/null | sort -r | tail -n +"$((n + 1))" | while read -r velho; do rm -f "$velho"; done
}
reter diario BACKUP_RETER_DIARIO 7
reter semanal BACKUP_RETER_SEMANAL 4
reter mensal BACKUP_RETER_MENSAL 6

RSYNC="$(env_valor BACKUP_DESTINO_RSYNC)"
RCLONE="$(env_valor BACKUP_DESTINO_RCLONE)"
if [ -n "$RSYNC" ]; then log "rsync para $RSYNC"; rsync -a --delete "$DIR/" "$RSYNC/"; fi
if [ -n "$RCLONE" ]; then log "rclone para $RCLONE"; rclone sync "$DIR" "$RCLONE"; fi
[ -n "$RSYNC$RCLONE" ] || log "AVISO: sem destino externo (BACKUP_DESTINO_RSYNC ou BACKUP_DESTINO_RCLONE): o backup está só neste servidor"

log "ok: $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1))"
echo "$ARQUIVO"
