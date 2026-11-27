# v2 → main: FiscalHub 2.0.0-rc.2

**Título sugerido:** `FiscalHub v2 (2.0.0-rc.2): importação, conciliação, capacidade, simulações, pontos de atenção e preparação para produção`

## Resumo

A v2 reescreve o FiscalHub para consultores tributários em escritórios com vários clientes. A v1 era um motor de
parcelamento com débitos digitados; a v2 **importa** as escriturações e os documentos dos órgãos e calcula tudo a partir
deles, com a origem de cada número (arquivo, registro, linha). São 136 commits desde a `main`: as 8 fases da rc.1
(0 a 7) e o que veio depois dela (rc.2), descritos no [CHANGELOG](../../CHANGELOG.md).

## O que entra

- **Escritório, papéis e usuários** (ADMIN, CONSULTOR, ASSISTENTE), clientes com escopo por escritório (recurso alheio = 404).
- **Importação** por tipo detectado no conteúdo: ECD, ECF, EFD-Contribuições, EFD ICMS/IPI (leiautes gerados dos manuais
  oficiais), balancete em planilha, PDFs e `.dec` dos órgãos (situação fiscal, Regularize, CAPAG, DCTF/DCTFWeb, DARF,
  pagamentos, conta corrente, RTI e GR-PR do PR). Stream, SHA-256, jobs em segundo plano, inconsistências com linha.
- **Demonstrações** referenciais e publicadas, DFC direta; **conciliação** apurado × declarado × pago com acréscimos;
  **situação fiscal**; **capacidade de pagamento** (CAPAG oficial, estimada, classificação do PR, gerencial).
- **Simulações** de parcelamento e transação com regras versionadas e fontes; **pontos de atenção** (16 regras, fundamentos
  conferidos); **relatórios** imutáveis (PDF e Excel com SHA-256) e a proposta de regularização.
- **Produção:** imagens, `compose.yaml` com Caddy (TLS) e PostgreSQL/MinIO, perfil `prod`, cabeçalhos de segurança,
  healthchecks, backup cifrado com teste de restauração, runbook.
- **Deploy na Hostinger (rc.2):** provisionamento do Ubuntu 24.04, limites de memória para 8 GB, backup enviado a um S3
  com Object Lock, migração num serviço de uso único (`migracao`) antes do backend, atualização em duas etapas que
  mantém a versão anterior no ar se a migração falhar, runbook do primeiro deploy (`docs/deploy/hostinger.md`).
- **Segurança:** cookie httpOnly + CSRF, senha forte, bloqueio progressivo, TOTP exigível pelo escritório e obrigatório
  para ADMIN, expiração por inatividade, reautenticação nas ações destrutivas, CSP sem `unsafe-inline`, sessões por
  dispositivo, auditoria imutável com retenção, papel do banco próprio para a aplicação (sem DDL, sem `DELETE` na
  auditoria), ASVS nível 2 documentado.
- **E-mail (rc.2):** convite, recuperação de senha e avisos pelo SMTP do Amazon SES; a senha provisória saiu.
- **LGPD:** inventário, exclusão com prazo de arrependimento, portabilidade, logs sem dado pessoal, termos (modelos).

## Como testar

1. `.\iniciar.ps1 -Docker` (ou `docker compose -f compose.yaml -f compose.dev.yaml up -d --build`) e abra http://localhost.
2. Cadastre um escritório, aceite os termos, crie o cliente "Metalúrgica Pinheiral" (CNPJ 11.222.333/0001-81) e importe
   a massa fictícia de `backend/src/test/resources/massa-ficticia` (ou rode `E2E_URL=http://localhost npm run guia`).
3. Percorra as abas do cliente conforme o [guia do consultor](../guia/README.md).
4. Automático: `mvn verify` (backend), `npm test` e `npx playwright test` (frontend), `deploy/teste-restauracao.sh`,
   `deploy/teste-migracao.sh`.

## Resultado dos testes (26/11/2026)

- Backend: `mvn verify` com 297 testes, 0 falhas, 2 ignorados (JUnit 5 + Testcontainers com PostgreSQL 18 e MinIO),
  incluindo o aceite da massa.
- Frontend: Vitest e Playwright (15 specs contra o compose, com a CSP e os cabeçalhos de produção, axe sem violações graves).
- Teste de restauração: massa completa → backup cifrado → envio ao bucket externo → volumes destruídos → download com a
  credencial de leitura → restauração → tudo igual.
- Teste da migração (no CI): backend sem a senha do dono; migração com erro impede o backend novo de subir, e numa
  atualização a versão anterior segue no ar e respondendo.
- CI do GitHub: verde no último commit da `v2` (CI e CodeQL).

## Migração e compatibilidade

- Banco: migrações Flyway V1 a V34; a V5 **apaga** as simulações e as declarações da v1 (modelo antigo, sem origem);
  a V33 transforma senhas provisórias em convites pendentes.
- Configuração nova obrigatória: `JWT_SECRET`, `CHAVE_DADOS`, `DB_PASSWORD`, `DB_APP_PASSWORD`, MinIO, `DOMINIO` e
  `ACME_EMAIL`; para e-mail e backup externo, `SMTP_*`, `EMAIL_REMETENTE` e `BACKUP_S3_*` (ver `.env.example`).
- O `JWT_SECRET` fixo do commit inicial da v2 já foi trocado e não vale; o histórico não foi reescrito.

## Antes do merge (ação manual)

- [ ] Proteção da `main` (README, "Ação manual no GitHub"): CI obrigatório e 1 revisão. Hoje a `main` não tem proteção.
- [ ] Secret `NVD_API_KEY` para o Dependency-Check (chave gratuita em https://nvd.nist.gov/developers/request-an-api-key;
      Settings → Secrets and variables → Actions). **Até a chave existir, o job "Dependências" falha** em segundos, com
      a mensagem "secret NVD_API_KEY ausente"; não é falha do código.
- [ ] CI verde neste PR (CI e CodeQL).
- [ ] Revisão jurídica dos Termos de Uso, da Política de Privacidade e do acordo de tratamento (`docs/lgpd/`).
- [ ] Servidor, domínio e destino externo do backup para o piloto (`docs/operacao/runbook.md`).

## Riscos conhecidos

- Adaptadores de PDF leem a massa fictícia (`ficticio-2026-10`): documentos reais caem em "layout não reconhecido" até
  ganharem adaptador (fluxo de anonimização pronto).
- Pendência do ASVS listada em `docs/seguranca/asvs.md`: medidor de força da senha (2.1.8).
- Sem domínio ainda: `DOMINIO` fica vazio no `.env.example` e é preenchido só no servidor.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
