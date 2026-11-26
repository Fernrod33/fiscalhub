# Primeiro deploy na Hostinger

Alvo: **Hostinger VPS KVM 2** (2 vCPU, 8 GB, 100 GB NVMe), datacenter São Paulo, **Ubuntu 24.04**. Backup externo e
e-mail na AWS, região **`sa-east-1`**. O domínio ainda não existe: onde aparece `DOMINIO`, use o domínio que for
registrado (sem `https://`). Ele entra **só** no `.env` do servidor e no DNS.

A rotina depois da subida (atualizar, restaurar, trocar segredos, incidentes) está no
[runbook de operação](../operacao/runbook.md). O e-mail está em [email-ses.md](email-ses.md).

## 0. Antes de começar

- [ ] Domínio registrado e acesso ao DNS dele.
- [ ] Uma chave SSH sua (`ssh-keygen -t ed25519`), com a parte pública à mão.
- [ ] Conta AWS com MFA no root e um usuário administrativo para os passos 3 e 4 (não é a credencial que vai para o servidor).
- [ ] `age` na sua máquina (`winget install FiloSottile.age`, `brew install age` ou `apt install age`).
- [ ] **Aviso aos ADMIN sem segundo fator** (passo 7) enviado com antecedência.

## 1. Criar a VPS

No hPanel: VPS KVM 2, São Paulo, **Ubuntu 24.04** (sem painel). Na criação, cadastre a sua chave SSH pública. Ative a
criptografia de disco se o plano oferecer (LGPD, sigilo fiscal: o PostgreSQL não cifra os dados; o MinIO sim).
Anote o IPv4 (e o IPv6, se houver).

## 2. Provisionar (como root, uma vez; pode repetir)

```sh
scp deploy/provisionar.sh root@IP:/root/
ssh root@IP 'CHAVE_SSH="ssh-ed25519 AAAA... voce@maquina" sh /root/provisionar.sh'
```

O [`provisionar.sh`](../../deploy/provisionar.sh) é idempotente e faz:

| O quê | Como |
|---|---|
| Usuário `deploy` (sem root) com `sudo` | grupo `sudo`; `sudo` pede senha: defina uma com `passwd deploy` ainda como root |
| SSH só por chave, só o `deploy` | `/etc/ssh/sshd_config.d/00-fiscalhub.conf`: `PermitRootLogin no`, `PasswordAuthentication no`, `AllowUsers deploy` |
| Firewall | `ufw`: entrada negada, só 22/tcp, 80/tcp, 443/tcp e 443/udp (HTTP/3) |
| Força bruta no SSH | `fail2ban`, 5 tentativas em 10 min = 1 h de bloqueio |
| Atualizações de segurança | `unattended-upgrades` diário |
| Docker e Compose | repositório oficial `download.docker.com` (`docker-ce`, `docker-compose-plugin`); `deploy` no grupo `docker` |

O script para **antes** de mexer no SSH se não houver chave para o `deploy` (não tranca você fora). Sem `CHAVE_SSH`,
ele copia as chaves de `/root/.ssh/authorized_keys`.

**Não feche a sessão de root** antes de testar, noutro terminal: `ssh deploy@IP` entra, `sudo -v` pede a senha e
`ssh root@IP` é recusado.

> **Docker e ufw:** porta publicada por contêiner passa por fora do `ufw`. O `compose.yaml` só publica 80 e 443 (proxy).
> Nunca publique outra porta (banco, MinIO, actuator) sem `127.0.0.1:` na frente.

Testado num contêiner `ubuntu:24.04` a cada commit (job `deploy` do CI, [`teste-provisionamento.sh`](../../deploy/teste-provisionamento.sh)).
Sem systemd no contêiner, ficam **sem teste automático**: a ativação do `ufw` e a regra no kernel, o `sshd` recarregado
de fato (a configuração é validada com `sshd -T`), o `fail2ban` banindo um IP, a execução do `unattended-upgrades` e o
daemon do Docker rodando. Confira na VPS: `sudo ufw status verbose`, `sudo fail2ban-client status sshd`,
`systemctl status unattended-upgrades docker`.

## 3. Bucket S3 de backup (na sua máquina, com a credencial administrativa)

```sh
BUCKET=fiscalhub-backups-$(openssl rand -hex 4)     # nome único no mundo; anote
aws s3api create-bucket --bucket $BUCKET --region sa-east-1 \
  --create-bucket-configuration LocationConstraint=sa-east-1 --object-lock-enabled-for-bucket   # liga o versionamento
aws s3api put-object-lock-configuration --bucket $BUCKET --object-lock-configuration \
  '{"ObjectLockEnabled":"Enabled","Rule":{"DefaultRetention":{"Mode":"GOVERNANCE","Days":35}}}'
aws s3api put-public-access-block --bucket $BUCKET --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-lifecycle-configuration --bucket $BUCKET --lifecycle-configuration file://deploy/aws/ciclo-de-vida.json
```

- **Object Lock em governance, 35 dias:** nenhuma versão é apagada nem sobrescrita antes de 35 dias, nem com a credencial
  do servidor, nem com a de leitura. Só um usuário com `s3:BypassGovernanceRetention` passa por cima (não dê essa
  permissão a ninguém no dia a dia).
- **Ciclo de vida** ([`ciclo-de-vida.json`](../../deploy/aws/ciclo-de-vida.json)): os diários saem depois de 35 dias.
  Os semanais e mensais ficam só no servidor (`BACKUP_RETER_*`), como já era: depois disso o dado excluído de um cliente
  não existe em mais nenhum lugar (LGPD).
- Criptografia SSE-S3 é o padrão dos buckets novos; o arquivo já chega cifrado com `age`.

Duas credenciais IAM, com as políticas de [`deploy/aws/`](../../deploy/aws/) (troque `NOME_DO_BUCKET`):

| Usuário IAM | Política | Onde fica |
|---|---|---|
| `fiscalhub-backup-envio` | [`politica-envio.json`](../../deploy/aws/politica-envio.json): só `s3:PutObject` no bucket | `.env` do servidor (`BACKUP_S3_ACCESS_KEY`, `BACKUP_S3_SECRET_KEY`) |
| `fiscalhub-backup-leitura` | [`politica-leitura.json`](../../deploy/aws/politica-leitura.json): listar e baixar, nada de apagar | fora do servidor (gerenciador de senhas do escritório) |

Quem invadir o servidor consegue enviar arquivos novos, mas não lê, não lista e não apaga os backups antigos.

## 4. Chaves do backup (`age`) — a privada nunca vai para o servidor

Na **sua máquina** (não na VPS):

```sh
age-keygen -o fiscalhub-backup.key      # a linha "# public key: age1..." é a pública
```

- A **pública** (`age1...`) vai para o `.env` do servidor: `BACKUP_AGE_DESTINATARIO`.
- A **privada** (`fiscalhub-backup.key`, uma linha `AGE-SECRET-KEY-1...`) fica em **duas cópias offline**, em lugares
  diferentes:
  1. pendrive cifrado (VeraCrypt ou BitLocker To Go), no cofre do escritório;
  2. papel impresso (a chave tem 74 caracteres), em envelope lacrado, com o sócio responsável ou num cofre bancário.
- Depois de gravar as duas cópias, apague o arquivo da máquina. **Sem a privada nenhum backup abre**, nem o seu.
- Para restaurar, a chave vai ao servidor só durante a restauração e é apagada no fim (runbook, seção 5).
- Teste anual: abrir um backup do S3 com cada uma das cópias.

## 5. DNS

No DNS do domínio: registro `A` (e `AAAA`, se houver IPv6) de `DOMINIO` para o IP da VPS. Espere propagar
(`dig +short DOMINIO`) **antes** da primeira subida: o Caddy pede o certificado ao Let's Encrypt na hora.
Para o e-mail, os registros do SES (DKIM, SPF, DMARC) do [email-ses.md](email-ses.md).

## 6. Primeira subida (como `deploy`)

```sh
git clone https://github.com/Fernrod33/fiscalhub.git /opt/fiscalhub && cd /opt/fiscalhub
git checkout vX.Y.Z                       # sempre uma tag
cp .env.example .env && chmod 600 .env    # preencha
```

No `.env`: `DOMINIO`, `ACME_EMAIL`, os segredos (`openssl rand -base64 48`; 32 para `CHAVE_DADOS` e
`MINIO_KMS_SECRET_KEY` no formato `nome:base64`; `openssl rand -hex 24` para `DB_APP_PASSWORD`, a senha do papel
`fiscalhub_app` com que a aplicação roda, sem DDL e sem `DELETE` na auditoria), as credenciais SMTP do SES, `BACKUP_AGE_DESTINATARIO` e os
`BACKUP_S3_*` do passo 3.

Passe pelo passo 7 e então:

```sh
docker compose up -d --build --wait
docker compose ps                          # todos healthy; minio-init "exited (0)"
curl -sI https://DOMINIO | grep -i strict-transport
deploy/backup.sh                           # o primeiro backup, já com envio ao S3
crontab -e                                 # 0 3 * * * /opt/fiscalhub/deploy/backup.sh >> /var/log/fiscalhub-backup.log 2>&1
```

Crie a pasta e o log do backup antes do cron: `sudo install -d -o deploy /var/backups/fiscalhub && sudo touch
/var/log/fiscalhub-backup.log && sudo chown deploy /var/log/fiscalhub-backup.log`.

**Certificado trocado** (renovação manual, outro emissor, mudança do `DOMINIO`, Caddyfile alterado):
`docker compose restart proxy`. A renovação automática do Let's Encrypt não precisa disso.

## 7. Aviso aos ADMIN sem segundo fator (antes de subir)

Todo ADMIN precisa ativar o segundo fator (TOTP) no primeiro acesso: até ativar, o sistema só mostra a tela de ativação.
Avise com antecedência quem é ADMIN e ainda não ativou, para ter o celular com um aplicativo autenticador (Google
Authenticator, Microsoft Authenticator, 1Password...) e guardar os 10 códigos de recuperação.

Num banco que já existe (migração de outro servidor), a lista sai de:

```sh
docker compose exec -T postgres psql -U fiscalhub -d fiscalhub -c \
  "SELECT nome, email FROM usuario WHERE papel = 'ADMIN' AND ativo AND NOT totp_ativo ORDER BY nome"
```

Contas que ainda estavam com senha provisória viraram **convite pendente** na migração V33: o ADMIN reenvia o convite
em Configurações › Usuários.

## 8. Memória (8 GB)

Limites no [`compose.yaml`](../../compose.yaml) (somam 4,4 GB; o resto é do sistema, do cache de disco e do backup):

| Serviço | Limite | Ajuste |
|---|---|---|
| backend | 2 GB (`BACKEND_MEMORIA`) | JVM com `-XX:MaxRAMPercentage=75` (heap até 1,5 GB) |
| postgres | 1 GB | `shared_buffers=256MB`, `effective_cache_size=1GB`, `work_mem=8MB` |
| minio | 1 GB | `GOMEMLIMIT=768MiB` |
| proxy | 256 MB | |
| frontend | 128 MB | |

**Pico medido** (26/11/2026, `deploy/teste-restauracao.sh`: importação da massa fictícia completa — ECD 2022-2025, ECF,
EFD de 2025 e 2026, PGFN, PR —, backup, envio ao S3 e restauração; `docker stats` a cada ~2 s):

| Serviço | Pico |
|---|---|
| backend | 677 MiB |
| minio | 320 MiB |
| postgres | 107 MiB |
| proxy | 54 MiB |
| frontend | 20 MiB |
| **total dos serviços de produção** | **~1,1 GB** (1,36 GB com o MinIO e o Mailpit que só existem no teste) |

Medido com o backend no perfil `dev` do compose de teste (mesmos limites). Pico bem abaixo dos 6 GB; o teto garantido
pelos limites é 4,4 GB. Com vários usuários importando ao mesmo tempo, o backend tende a usar o heap inteiro (1,5 GB)
antes de coletar: é esperado. Acompanhe com `docker stats --no-stream` nas primeiras semanas.

## 9. Conferência final

- [ ] `https://DOMINIO` abre o login, com HSTS.
- [ ] `ssh root@IP` e login por senha recusados; `sudo ufw status` só com 22, 80 e 443.
- [ ] Cadastro do escritório, ativação do segundo fator do ADMIN, convite de um usuário chegando por e-mail.
- [ ] `deploy/backup.sh` termina com o arquivo e o tamanho; o objeto aparece no bucket com a credencial de leitura
      (`aws s3 ls s3://BUCKET/diario/`).
- [ ] Teste de restauração com o backup do S3 numa máquina à parte (runbook, seção 5).
