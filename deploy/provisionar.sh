#!/bin/sh
# Provisionamento do servidor (Ubuntu 24.04, Hostinger VPS) para o FiscalHub. Idempotente: rodar de novo não muda o que
# já está certo. Como root, no primeiro acesso:
#   CHAVE_SSH="ssh-ed25519 AAAA... voce@maquina" sh provisionar.sh
# Sem CHAVE_SSH, copia as chaves de /root/.ssh/authorized_keys (a que a Hostinger instalou na criação da VPS).
# Faz: usuário sem root com sudo (USUARIO, padrão "deploy") e só chave no SSH; root e senha desligados no SSH; ufw só com
# 22, 80 e 443; fail2ban no SSH; atualizações de segurança automáticas; Docker e o plugin compose do repositório oficial.
# Passo a passo completo: docs/deploy/hostinger.md.
set -eu

USUARIO="${USUARIO:-deploy}"
export DEBIAN_FRONTEND=noninteractive

log() { printf '[provisionar] %s\n' "$*"; }
# com systemd (VPS real) liga o serviço; num contêiner de teste só registra o que faria
servico() {
	if [ -d /run/systemd/system ]; then systemctl enable --now "$1" >/dev/null 2>&1 && systemctl reload-or-restart "$1"; else log "sem systemd: $1 não iniciado"; fi
}

[ "$(id -u)" = 0 ] || { echo "rode como root (sudo sh provisionar.sh)" >&2; exit 1; }
grep -q 'VERSION_ID="24.04"' /etc/os-release || log "AVISO: feito para Ubuntu 24.04; seguindo mesmo assim"

log "pacotes"
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg ufw fail2ban unattended-upgrades openssh-server sudo python3-systemd >/dev/null

log "usuário $USUARIO"
id "$USUARIO" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash "$USUARIO"
# sudo com senha: a conta não tem senha até você definir uma (passwd $USUARIO), e sem ela o sudo não funciona
usermod -aG sudo "$USUARIO"
CASA="$(getent passwd "$USUARIO" | cut -d: -f6)"
install -d -m 700 -o "$USUARIO" -g "$USUARIO" "$CASA/.ssh"
CHAVES="$CASA/.ssh/authorized_keys"
touch "$CHAVES"
if [ -n "${CHAVE_SSH:-}" ]; then
	grep -qxF "$CHAVE_SSH" "$CHAVES" || printf '%s\n' "$CHAVE_SSH" >> "$CHAVES"
elif [ -s /root/.ssh/authorized_keys ]; then
	while IFS= read -r linha; do
		if [ -n "$linha" ] && ! grep -qxF "$linha" "$CHAVES"; then printf '%s\n' "$linha" >> "$CHAVES"; fi
	done < /root/.ssh/authorized_keys
fi
chown "$USUARIO:$USUARIO" "$CHAVES"
chmod 600 "$CHAVES"

log "SSH só por chave, sem root"
# sem chave do usuário novo, desligar root e senha trancaria você fora: para antes
grep -q '^ssh-\|^ecdsa-\|^sk-' "$CHAVES" || { echo "nenhuma chave SSH para $USUARIO: passe CHAVE_SSH=\"ssh-ed25519 ...\"" >&2; exit 1; }
# o primeiro valor vale no sshd: o drop-in 00- vem antes do 50-cloud-init.conf que a imagem pode trazer
cat > /etc/ssh/sshd_config.d/00-fiscalhub.conf <<EOF
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AllowUsers $USUARIO
EOF
ssh-keygen -A >/dev/null
mkdir -p /run/sshd
sshd -t
servico ssh

log "firewall: 22, 80 e 443"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw allow 443/udp >/dev/null # HTTP/3 do Caddy
if [ -d /run/systemd/system ]; then ufw --force enable >/dev/null; else log "sem systemd: ufw com as regras gravadas, não ativado"; fi

log "fail2ban no SSH"
cat > /etc/fail2ban/jail.d/fiscalhub.local <<'EOF'
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
servico fail2ban

log "atualizações de segurança automáticas"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF
servico unattended-upgrades

log "Docker (repositório oficial)"
install -d -m 0755 /etc/apt/keyrings
[ -s /etc/apt/keyrings/docker.asc ] || curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# shellcheck disable=SC1091 # existe no servidor, não na máquina que roda o shellcheck
CODINOME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
	"$(dpkg --print-architecture)" "$CODINOME" > /etc/apt/sources.list.d/docker.list
apt-get update -qq
apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null
# grupo docker = acesso de root ao Docker: só o usuário do deploy
usermod -aG docker "$USUARIO"
servico docker

log "pasta da aplicação /opt/fiscalhub"
install -d -m 750 -o "$USUARIO" -g "$USUARIO" /opt/fiscalhub

log "pronto. Antes de fechar esta sessão, teste noutra: ssh $USUARIO@<ip> (root e senha já não entram)"
