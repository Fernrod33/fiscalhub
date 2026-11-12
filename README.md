# FiscalHub

Ferramenta para consultores tributários em escritórios com vários clientes: importa as escriturações e os documentos
fiscais, concilia apurado × declarado × pago, aponta riscos e oportunidades, simula parcelamentos e transações e emite
relatórios. Os dados são fiscais e sigilosos (CTN, art. 198, e LGPD).

- Backend: [backend/README.md](backend/README.md) · Frontend: [frontend/README.md](frontend/README.md)

## Integração contínua

| Workflow | Quando | O que faz |
|---|---|---|
| [`ci.yml`](.github/workflows/ci.yml) | PR e push em `main`/`v2` | `backend` (`mvn verify` com Testcontainers), `frontend` (lint, `tsc -b`, Vitest, build), `e2e` (Playwright contra o `compose.dev.yaml`, com relatório, capturas e logs como artefato na falha), `segredos` (gitleaks no histórico inteiro) e `npm-audit` (só alta e crítica) |
| [`codeql.yml`](.github/workflows/codeql.yml) | PR, push e semanal | CodeQL `security-extended` em Java e TypeScript |
| [`dependencias.yml`](.github/workflows/dependencias.yml) | push em `main`/`v2`, semanal e manual | OWASP Dependency-Check do backend; quebra só com CVSS ≥ 7 |
| [`dependabot.yml`](.github/dependabot.yml) | semanal | Maven, npm, Actions, Dockerfiles e compose, agrupados por ecossistema |

Achados do gitleaks aceitos ficam em [`.gitleaksignore`](.gitleaksignore), um por linha e com a justificativa (o
`JWT_SECRET` antigo do commit inicial da v2 já foi trocado e não vale em nenhum ambiente).

### Ação manual no GitHub

1. **Proteção da branch `main`** (Settings → Branches → Add branch ruleset, alvo `main`):
   - exigir pull request antes do merge, com **1 aprovação**, descartando aprovações quando houver commit novo e exigindo
     a resolução das conversas;
   - exigir os status checks, com a branch atualizada: `backend`, `frontend`, `e2e`, `segredos`, `npm-audit` (do CI) e
     `analisar (java-kotlin, manual)` e `analisar (javascript-typescript, none)` (do CodeQL);
   - bloquear force push e exclusão da branch; incluir administradores.
2. **Secret `NVD_API_KEY`** (Settings → Secrets and variables → Actions): chave gratuita do NVD para o OWASP
   Dependency-Check. Sem ela o workflow `Dependências` leva horas baixando a base.
3. **Code scanning** (Settings → Code security): habilitar o Dependabot alerts e security updates; o CodeQL publica
   sozinho em Security → Code scanning.
