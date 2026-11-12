# v2 → main: FiscalHub 2.0.0-rc.1

**Título sugerido:** `FiscalHub v2 (2.0.0-rc.1): importação, conciliação, capacidade, simulações, pontos de atenção e preparação para produção`

## Resumo

A v2 reescreve o FiscalHub para consultores tributários em escritórios com vários clientes. A v1 era um motor de
parcelamento com débitos digitados; a v2 **importa** as escriturações e os documentos dos órgãos e calcula tudo a partir
deles, com a origem de cada número (arquivo, registro, linha). São 100+ commits em 8 fases (0 a 7), descritos no
[CHANGELOG](../../CHANGELOG.md).

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
- **Segurança:** cookie httpOnly + CSRF, senha forte, bloqueio progressivo, TOTP exigível pelo escritório, sessões por
  dispositivo, auditoria imutável, ASVS nível 2 documentado.
- **LGPD:** inventário, exclusão com prazo de arrependimento, portabilidade, logs sem dado pessoal, termos (modelos).

## Como testar

1. `.\iniciar.ps1 -Docker` (ou `docker compose -f compose.yaml -f compose.dev.yaml up -d --build`) e abra http://localhost.
2. Cadastre um escritório, aceite os termos, crie o cliente "Metalúrgica Pinheiral" (CNPJ 11.222.333/0001-81) e importe
   a massa fictícia de `backend/src/test/resources/massa-ficticia` (ou rode `E2E_URL=http://localhost npm run guia`).
3. Percorra as abas do cliente conforme o [guia do consultor](../guia/README.md).
4. Automático: `mvn verify` (backend), `npm test` e `npx playwright test` (frontend), `deploy/teste-restauracao.sh`.

## Resultado dos testes (local, 12/11/2026)

- Backend: suíte inteira verde (JUnit 5 + Testcontainers com PostgreSQL 18 e MinIO), incluindo o aceite da massa.
- Frontend: Vitest 66/66; Playwright 14 specs contra o compose (CSP e cabeçalhos de produção), axe sem violações graves.
- Teste de restauração: massa completa → backup cifrado → volumes destruídos → restauração → tudo igual.
- CI do GitHub: roda neste PR (precisa do push da branch).

## Migração e compatibilidade

- Banco: migrações Flyway V1 a V30; a V5 **apaga** as simulações e as declarações da v1 (modelo antigo, sem origem).
- Configuração nova obrigatória: `JWT_SECRET`, `CHAVE_DADOS`, `DB_PASSWORD`, MinIO e `DOMINIO` (ver `.env.example`).
- O `JWT_SECRET` fixo do commit inicial da v2 já foi trocado e não vale; o histórico não foi reescrito.

## Antes do merge (ação manual)

- [ ] Proteção da `main` (README, "Ação manual no GitHub"): CI obrigatório e 1 revisão.
- [ ] Secret `NVD_API_KEY` para o Dependency-Check.
- [ ] CI verde neste PR.
- [ ] Revisão jurídica dos Termos de Uso, da Política de Privacidade e do acordo de tratamento (`docs/lgpd/`).
- [ ] Servidor, domínio e destino externo do backup para o piloto (`docs/operacao/runbook.md`).

## Riscos conhecidos

- Adaptadores de PDF leem a massa fictícia (`ficticio-2026-10`): documentos reais caem em "layout não reconhecido" até
  ganharem adaptador (fluxo de anonimização pronto).
- Pendências do ASVS listadas em `docs/seguranca/asvs.md` (e-mail transacional, expiração por inatividade, CSP de estilo).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
