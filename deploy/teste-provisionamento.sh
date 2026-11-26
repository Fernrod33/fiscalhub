#!/bin/sh
# shellcheck disable=SC2015 # "cond && ok || falha": ok só faz echo (nunca falha)
# Teste do provisionar.sh num Ubuntu 24.04 limpo (sem systemd: serviços não sobem, o resto é conferido). Provisiona duas
# vezes (a 2ª não pode mudar nada em /etc) e confere usuário, SSH, ufw, fail2ban, atualizações e Docker. No CI e à mão:
#   docker run --rm --cap-add NET_ADMIN -v "$PWD/deploy:/deploy:ro" ubuntu:24.04 sh /deploy/teste-provisionamento.sh
set -eu
mkdir -p /root/.ssh
echo "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIFakeChaveDeTesteSomente000000000000000000 teste@ci" > /root/.ssh/authorized_keys
sh /deploy/provisionar.sh
cp -a /etc /tmp/etc-1
sh /deploy/provisionar.sh
echo "== diferenças em /etc na 2ª execução (idempotência; apt/ld caches à parte):"
diff -rq /tmp/etc-1 /etc 2>/dev/null | grep -vE 'ld.so.cache|apt/|/mtab' || echo "nenhuma"
echo "== conferências"
ok() { echo "OK  $*"; }
falha() { echo "FALHA $*"; exit 1; }
id deploy | grep -q '(sudo)' && ok "deploy no grupo sudo" || falha "sudo"
id deploy | grep -q '(docker)' && ok "deploy no grupo docker" || falha "docker group"
[ "$(wc -l < /home/deploy/.ssh/authorized_keys)" = 1 ] && ok "chave copiada uma vez só" || falha "authorized_keys duplicada"
[ "$(stat -c %a /home/deploy/.ssh/authorized_keys)" = 600 ] && ok "authorized_keys 600" || falha "perm"
sshd -T | grep -qx 'permitrootlogin no' && ok "sshd: root desligado" || falha "root"
sshd -T | grep -qx 'passwordauthentication no' && ok "sshd: senha desligada" || falha "senha"
sshd -T | grep -qx 'kbdinteractiveauthentication no' && ok "sshd: teclado interativo desligado" || falha "kbd"
sshd -T | grep -qx 'allowusers deploy' && ok "sshd: só o deploy" || falha "allowusers"
for r in 'tcp 22' 'tcp 80' 'tcp 443' 'udp 443'; do grep -q "^### tuple ### allow $r 0.0.0.0/0 any 0.0.0.0/0 in" /etc/ufw/user.rules && ok "ufw libera $r" || falha "ufw $r"; done
[ "$(grep -c '^### tuple ###' /etc/ufw/user.rules)" = 4 ] && ok "ufw: só 4 regras (sem duplicar)" || falha "ufw regras: $(grep -c '^### tuple ###' /etc/ufw/user.rules)"
grep -q 'DEFAULT_INPUT_POLICY="DROP"' /etc/default/ufw && ok "ufw: entrada negada por padrão" || falha "ufw default"
grep -q 'enabled = true' /etc/fail2ban/jail.d/fiscalhub.local && fail2ban-client -t >/dev/null 2>&1 && ok "fail2ban: configuração válida" || falha "fail2ban"
grep -q 'Unattended-Upgrade "1"' /etc/apt/apt.conf.d/20auto-upgrades && ok "unattended-upgrades ligado" || falha "uu"
docker --version >/dev/null && docker compose version >/dev/null && ok "$(docker --version), $(docker compose version)" || falha "docker"
grep -q download.docker.com /etc/apt/sources.list.d/docker.list && ok "Docker do repositório oficial" || falha "repo"
[ "$(stat -c %U /opt/fiscalhub)" = deploy ] && ok "/opt/fiscalhub do deploy" || falha "opt"
echo "== sem chave: deve parar antes de mexer no SSH"
rm /home/deploy/.ssh/authorized_keys /root/.ssh/authorized_keys
if USUARIO=outro sh /deploy/provisionar.sh >/dev/null 2>&1; then falha "rodou sem chave"; else ok "recusou sem chave SSH"; fi
echo TUDO-OK
