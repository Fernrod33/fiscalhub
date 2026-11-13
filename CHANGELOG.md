# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/); versões em [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Segurança de conta
- 2º fator obrigatório para todo ADMIN (inclusive promovido): a API responde 403 `/problemas/segundo-fator-obrigatorio`
  até a ativação, e o ADMIN não pode desativá-lo.
- Sessão expira após 30 min sem requisição (`SESSAO_INATIVIDADE_MINUTOS`); aviso 2 min antes, com opção de continuar,
  e volta ao login preservando a rota.
- Reautenticação (senha e código, 5 min) para excluir cliente, exportar os dados de um cliente, desativar usuário e
  mudar papel.
- CSP sem `'unsafe-inline'` em `style-src`: nonce por resposta no proxy. Ao atualizar: `docker compose restart proxy`.

## [2.0.0-rc.1] — 2026-11-12

Primeira versão candidata da v2: o MVP completo para um piloto real, reescrito a partir da v1 (motor de parcelamento
com cadastro manual de débitos). Tudo entra por importação de arquivo; nenhum valor fiscal é digitado.

### Fase 7 — produção, segurança e LGPD
- **Índices:** UPF/PR importada da página de Indicadores Econômicos da SEFA-PR (Resolução SEFA 360/2025), agendada
  com a SELIC; regras do PR desbloqueadas.
- **Normas:** fundamentos dos pontos R03, R05, R11–R14 conferidos no texto vigente; R14 segue a ADI 5.161 (STF).
  Feriados do PR (sem feriado estadual civil) e de Cascavel.
- **Empacotamento:** imagens do backend (JRE 25, sem root) e do frontend (Caddy), `compose.yaml` de produção com
  PostgreSQL, MinIO (versionado, SSE, usuário só do bucket), proxy Caddy com TLS automático e cabeçalhos de segurança;
  perfil `prod`; actuator só na rede interna; `iniciar.ps1 -Docker`.
- **CI:** GitHub Actions (backend, frontend, e2e no compose), CodeQL, Dependabot, gitleaks, npm audit e
  Dependency-Check.
- **Conta:** senha de 12+ caracteres contra lista de vazadas, bloqueio progressivo por conta e IP, limite por IP na
  autenticação, TOTP com códigos de recuperação (exigível pelo escritório), sessões por dispositivo; checklist do
  OWASP ASVS nível 2.
- **LGPD:** inventário, exclusão do cliente com prazo de arrependimento, portabilidade em ZIP, logs sem CPF/CNPJ/valores,
  aceite dos termos (modelos para revisão jurídica), encarregado do escritório.
- **Operação:** backup cifrado com `age` e retenção, restauração e teste de restauração automatizado, runbook.
- **Documentos reais:** ferramenta de anonimização (`anonimizar.ps1`), orientação para layout não reconhecido,
  métricas e alertas do ADMIN do sistema, rotina de atualização normativa.
- **Docs:** guia do consultor com capturas geradas pelo Playwright, README raiz.

### Fase 6 — pontos de atenção
- Motor com 16 regras plugáveis, evidências com link para a origem, ciclo de vida auditado; diagnóstico fiscal;
  painel do escritório com a carteira; configuração das regras por escritório.

### Fase 5 — parcelamento e transação
- Regras de acréscimos, parcelamento e transação como dado versionado com fontes; motor único; simulações versionadas
  e recalculáveis; proposta de regularização; transação do Edital 6/2026 para ME e EPP.

### Fase 4 — capacidade de pagamento
- DFC direta da ECD, CAPAG estimada (Portaria PGFN 6.757/2022), classificação do PR (Res. Conj. PGE/SEFA 01/2025),
  análise gerencial; relatório de capacidade e dossiê da revisão da CAPAG.

### Fase 3 — relatórios
- Modelo único, template Thymeleaf (prévia e PDF), gráficos SVG, Excel, emissão imutável com snapshot e SHA-256;
  identidade do escritório.

### Fase 2 — escriturações fiscais e documentos dos órgãos
- ECF, EFD-Contribuições e EFD ICMS/IPI no modelo único de apuração; PDFs e `.dec` dos órgãos (situação fiscal,
  Regularize, CAPAG, DCTF/DCTFWeb, DARF, pagamentos, PR) em duas camadas; conciliação apurado × declarado × pago.

### Fase 1 — importação e demonstrações
- Pipeline por tipo detectado no conteúdo, ECD em stream com leiaute versionado, plano referencial oficial,
  demonstrações com origem, balancete em planilha com de-para; central de importação no frontend.

### Fase 0 — base
- Escritório, papéis e usuários; cliente com escopo por escritório; Flyway; storage local/S3 com SHA-256 e jobs; trilha
  de auditoria; SELIC do Banco Central; JWT em cookie httpOnly com CSRF; design system e estrutura do frontend.

### Segurança (correções da v1)
- Segredos fora do código (o `JWT_SECRET` antigo do histórico foi trocado), IDOR e exposição de dados de usuário
  corrigidos, sobrescrita de declaração alheia impedida.
