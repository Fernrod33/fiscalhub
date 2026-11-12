# Runbook de operação

Alvo: **um servidor Linux com Docker Compose**, o `compose.yaml` da raiz e o Caddy com TLS automático. Os comandos
partem da pasta do repositório no servidor (ex.: `/opt/fiscalhub`).

## 1. Preparar o servidor (uma vez)

1. **Disco cifrado** (LGPD e sigilo fiscal): o volume do Docker (`/var/lib/docker`) e a pasta de backups num disco com
   LUKS (ou a criptografia de disco do provedor). O MinIO já cifra os arquivos (SSE), mas o PostgreSQL não.
2. Docker Engine com o plugin Compose; `age`, `rsync` (ou `rclone`) e `openssl` (`apt install age rsync openssl`).
3. Firewall: só 22 (de onde administra), 80 e 443 abertos. Nenhuma outra porta é publicada pelo compose.
4. DNS: o domínio (`DOMINIO`) apontando para o servidor, antes da primeira subida (o Caddy pede o certificado).
5. Usuário de deploy no grupo `docker`, dono de `/opt/fiscalhub` e de `/var/backups/fiscalhub`.

## 2. Primeira subida

```sh
git clone https://github.com/Fernrod33/fiscalhub.git /opt/fiscalhub && cd /opt/fiscalhub
git checkout v2.0.0-rc.1                 # sempre uma tag
cp .env.example .env && chmod 600 .env   # preencha (abaixo)
age-keygen -o chave-backup.txt           # a PRIVADA vai para fora do servidor (cofre); a pública vai para o .env
docker compose up -d --build --wait
docker compose ps                        # todos healthy; minio-init "exited (0)"
```

`.env`: gere cada segredo com `openssl rand -base64 48` (32 para `CHAVE_DADOS` e a do `MINIO_KMS_SECRET_KEY`, no
formato `nome:base64`). Preencha também `BACKUP_AGE_DESTINATARIO` (a pública, `age1...`) e o destino externo do backup
(`BACKUP_DESTINO_RSYNC` ou `BACKUP_DESTINO_RCLONE`). Apague a `chave-backup.txt` do servidor depois de guardá-la.

Conferência: `https://DOMINIO` abre o login; `curl -sI https://DOMINIO | grep -i strict-transport` mostra o HSTS.

## 3. Atualizar

```sh
cd /opt/fiscalhub
deploy/backup.sh                          # sempre antes
git fetch --tags && git checkout vX.Y.Z
docker compose up -d --build --wait       # o Flyway aplica as migrações novas na subida do backend
docker compose logs backend | grep -i -E "migrat|error" | tail
```

- O Flyway roda na subida, sem baseline automático (`application-prod.properties`). Migração com erro: o backend não
  sobe e o anterior já parou. Volte à tag anterior **e** restaure o backup feito no passo 1 (as migrações não têm volta).
- Versões são tags (`CHANGELOG.md`); nunca suba a branch diretamente.

## 4. Backup

Agendado no crontab do usuário de deploy:

```
0 3 * * * /opt/fiscalhub/deploy/backup.sh >> /var/log/fiscalhub-backup.log 2>&1
```

- O que entra: `pg_dump -Fc` do banco + espelho do bucket (versões atuais), num `.tar` cifrado com `age` para a chave
  pública do `.env`. Sem a chave privada, o arquivo não abre.
- Retenção: 7 diários, 4 semanais (domingo) e 6 mensais (dia 1º) — `BACKUP_RETER_*`. Depois disso o dado excluído de um
  cliente não existe em mais nenhum lugar.
- Cópia externa: `rsync` ou `rclone` ao fim de cada execução; sem destino, o log avisa.
- Conferir: `tail /var/log/fiscalhub-backup.log` (a última linha diz o arquivo e o tamanho) e, mensalmente, o teste de
  restauração (seção 5).

## 5. Restaurar

```sh
deploy/restaurar.sh /var/backups/fiscalhub/diario/fiscalhub-AAAAMMDD-HHMMSS.tar.age /caminho/chave-backup.txt
```

Para o backend, recria o banco a partir do dump, espelha o bucket (arquivos que não estão no backup saem) e sobe o
backend de novo. Pede para digitar "restaurar". Traga a chave privada só durante a restauração e apague depois.

**Teste de restauração automatizado** (numa máquina com Docker e Node, não em produção): `deploy/teste-restauracao.sh`
sobe um ambiente limpo num projeto separado (porta 8088), carrega a massa completa, emite um relatório, faz o backup,
destrói os volumes, sobe vazio, restaura e confere com o Playwright que débitos, parcelamentos, CAPAG, pontos de atenção,
relatórios e os bytes de cada arquivo voltaram iguais e que o aceite da massa continua valendo.

## 6. Trocar segredos

Edite o `.env` e rode `docker compose up -d --wait` (o compose recria só o que mudou). Faça backup antes.

| Segredo | Como trocar | Efeito |
|---|---|---|
| `JWT_SECRET` | novo valor + `up -d` | todas as sessões caem; cada um entra de novo |
| `CHAVE_DADOS` | **não troque sem plano**: os segredos do 2º fator ficam ilegíveis. Antes: `UPDATE usuario SET totp_ativo = false, totp_segredo = NULL, totp_ultimo_passo = NULL; DELETE FROM codigo_recuperacao;` e avise os usuários; depois o valor novo | cada usuário ativa o 2º fator de novo (o escritório que exige leva todos à ativação) |
| `DB_PASSWORD` | `docker compose exec postgres psql -U fiscalhub -c "ALTER USER fiscalhub PASSWORD '...'"`, depois o `.env` e `up -d` | nenhum |
| `S3_SECRET_KEY` | novo valor no `.env` e `up -d` (o `minio-init` recria o usuário com a senha nova) | nenhum |
| `MINIO_ROOT_PASSWORD` | `mc admin user` não troca o root: pare o MinIO, troque no `.env` e suba | nenhum |
| `MINIO_KMS_SECRET_KEY` | **não troque**: os objetos cifrados com a chave antiga deixam de abrir. Para rotacionar, restaure um backup num ambiente com a chave nova (o espelho regrava os arquivos cifrados com ela) | — |
| chave do backup (`age`) | `age-keygen` novo, a pública no `.env`; guarde a privada antiga até o último backup cifrado com ela expirar (6 meses) | — |
| segredo vazado | troque-o já e trate como incidente (seção 7) | — |

## 7. Incidente de segurança

1. **Conter:** `docker compose stop proxy` tira o sistema do ar sem perder nada. Troque os segredos possivelmente
   expostos (seção 6). Encerre sessões suspeitas (`UPDATE sessao SET revogada_em = now() WHERE ...`) ou todas
   (troca do `JWT_SECRET`).
2. **Preservar:** copie os logs (`docker compose logs --no-color > incidente-AAAAMMDD.log`) e exporte a auditoria do
   período (`\copy (SELECT * FROM auditoria_evento WHERE data_hora > '...') TO 'auditoria.csv' CSV HEADER`). A tabela
   não aceita alteração nem exclusão.
3. **Avaliar:** o que foi acessado, de quais escritórios e clientes, desde quando (o id de correlação liga log e
   requisição; a auditoria tem usuário, IP e recurso).
4. **Comunicar:** o FiscalHub é operador: avise os escritórios afetados (controladores) sem demora; eles comunicam a
   ANPD e os titulares quando houver risco ou dano relevante (LGPD, art. 48, e o prazo do regulamento da ANPD vigente).
   Modelo e prazos do aviso: acordo de tratamento (`docs/lgpd/acordo-tratamento-dados.md`).
5. **Corrigir e registrar:** causa, correção, testes, e um registro do incidente (data, impacto, medidas) guardado com
   a documentação.

## 8. Rotina

| Quando | O quê |
|---|---|
| Todo dia | o backup rodou (log); `docker compose ps` sem `unhealthy` |
| Toda semana | PRs do Dependabot e alertas do CodeQL; atualizar a tag se houver correção de segurança |
| Todo mês | teste de restauração; conferir o espaço em disco e os índices importados (SELIC, UPF/PR) |
| A cada edital ou norma | `docs/normativas/atualizacao.md` |
