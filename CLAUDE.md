# FiscalHub — regras permanentes para o agente

Ferramenta para consultores tributários (advogados e contadores) em escritórios com vários clientes.
Backend Java 25 + Spring Boot (`backend/`), frontend React + TypeScript + Vite (`frontend/`), branch de trabalho `v2`.

## Produto (não negociável)
- **Dados só por importação.** Nenhum valor fiscal é digitado; o usuário só classifica e mapeia.
- **Todo número aponta para a origem** (documento, página/registro, linha).
- **Nada estimado sem rótulo**: estimativa e projeção aparecem como tal, com os parâmetros visíveis.
- **Normas como dado versionado** (vigência, fonte com URL/data/SHA-256), tiradas do texto oficial vigente — nunca da memória. Referência: `docs/normativas/`.
- Dados fiscais são sigilosos (CTN art. 198, LGPD): na dúvida, o mais seguro.

## Como trabalhar
- **graphify:** `/graphify . --update` no início e ao fim de cada tarefa; `/graphify explain` antes de mexer numa classe central.
- **ponytail** modo `full`: `/ponytail-review` antes de cada commit, `/ponytail-audit` no fim. Dependência nova só com justificativa no commit.
- **SOLID com bom senso e DRY:** interface só com mais de uma implementação real; reaproveitar antes de criar; regra de cálculo num lugar só (ex.: acréscimos só via `AcrescimosMoratorios`); frontend só exibe.
- **Commits pequenos** (`tipo(escopo): descrição`); `mvn test`, Vitest, Playwright, lint e build verdes a cada commit.
- **Push, tag, PR e merge só com autorização explícita** no prompt.
- Revisão de segurança do diff no fim de cada prompt.
- Nunca imprimir segredos; não tocar em `backend/.env`.

## Fim de cada prompt: relatório curto
Commits · contratos de API e migrações · testes · débitos `ponytail:` · decisões pendentes para mim.
