# FiscalHub — frontend

React 19 + TypeScript + Vite. Dados fiscais só entram por importação de arquivo; nenhuma tela pede valor fiscal digitado.

## Rodar

```bash
npm install
npm run dev          # http://localhost:5173 (backend em http://localhost:8080)
npm run build        # tsc + build de produção em dist/
npm run lint
```

Variáveis (opcionais, em `.env.local`):

| Variável | Padrão | Uso |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Base da API |
| `VITE_UPLOAD_MAX_BYTES` | 500 MB | Limite de upload checado antes do envio (o backend valida de novo) |

## Pastas

```
src/
  app/          rotas (data router, código dividido por rota), guardas, providers, tema, telas de erro
  features/     uma pasta por domínio: auth, clientes, documentos (central de importação),
                planilha (assistente do balancete), demonstracoes, tributos (apurado: matriz,
                demonstração do lucro, saldos fiscais), debitos, simulacoes,
                usuarios, auditoria, painel — cada uma com api.ts (TanStack Query),
                components/ e pages/
  components/   compartilhados: DataTable, Cartao, EstadoVazio, TextoTruncado, Campo, Confirmar, Alternador,
                ValorOrigem (número que abre a origem: arquivo, registro e linha)
    layout/     AppShell, menu lateral, cabeçalho, trilha, paleta de comandos (Ctrl+K)
    motion/     componentes de movimento (abaixo)
    ui/         shadcn/ui ajustado aos tokens — não editar sem necessidade
  lib/          http (cliente único, ProblemDetail -> ErroApi), formatadores, cnpj, rótulos,
                preferências, config; api/ com os tipos gerados da OpenAPI
  styles/       globals.css (tokens) e motion.ts (tokens de movimento)
  laboratorio/  referência viva do design system — só em dev, fora do build
```

Regras que valem para todo o código:

- Toda chamada à API passa por `lib/http.ts`. A sessão é o cookie `httpOnly` do backend (`credentials: 'include'`): o JS nunca vê o token, e nada de sessão fica no `localStorage`. Alterações levam o cabeçalho CSRF (`X-XSRF-TOKEN`, do cookie `XSRF-TOKEN`). O upload usa `http.enviarComProgresso` (XHR só ali, para a barra de progresso). Erros chegam como `ErroApi` (status, tipo, correlação, campos); use `mensagemErro(err, padrao)` para exibir.
- A sessão é lida de `GET /api/usuarios/me` antes do primeiro render (`features/auth/sessao.ts`).
- Valores contábeis seguem a convenção do backend (devedor +, credor −) e aparecem por `fmt.saldo`: valor absoluto com D/C. Nenhuma tela pede valor fiscal digitado.
- Números, datas, CNPJ e competência sempre por `lib/formatadores.ts`.
- Permissão na tela: `usePermissoes()` (`alterar`, `admin`). O backend é a autoridade; a tela só esconde o que daria 403 (ex.: mapear planilha e de-para, só ADMIN e CONSULTOR).
- Todo número de origem fiscal ou contábil abre a origem pelo `ValorOrigem` (arquivo, registro e linha).

## Aba Tributos

- **Matriz** (`features/tributos/components/matriz-tributos.tsx`): tributos por esfera (estaduais por UF) × competências (mensal, trimestral ou anual, pela data final do período); a célula soma o valor a recolher das apurações dela e abre a gaveta de detalhe (base, apurado, deduções e créditos, a recolher, origem, linhas do cálculo, obrigações). As colunas de cada competência são `Metrica`s (`features/tributos/matriz.ts`): para o declarado, o pago e a diferença da Fase 2b, basta passar mais métricas.
- **IRPJ e CSLL**: por período de apuração (ajuste anual ou trimestre), regime e demonstração em cascata (lucro, adições, exclusões, compensação, base presumida, cálculo).
- **Saldos fiscais**: cards do saldo mais recente de cada tipo e a evolução por período.
- **Alertas de conferência**: as inconsistências das escriturações fiscais e a conferência ECD × ECF.

## Design system

Referência visual: `npm run dev` e abra `/laboratorio-design` (a rota não existe no build de produção).

**Tokens** (`src/styles/globals.css`): cores semânticas em `:root` (claro) e `.dark` — `background`, `surface`, `foreground`, `muted`, `border`, `primary` (azul-petróleo), `success`/`warning`/`danger`/`info` com `-bg`, e `sidebar-*` (menu escuro nos dois temas). Use as classes do Tailwind geradas por eles (`bg-surface`, `text-muted-foreground`…), nunca cor literal.

- Tipografia: Inter em tudo; Source Serif 4 (`font-serif`) só no h1 da página e no h2 dos cartões.
- Números em Inter com `tabular-nums` (já aplicado a `td`, `.num` e `[data-numerico]`).
- Cor só indica estado (ex.: KPI "Com erro" > 0 em perigo). Nada de cor decorativa.
- Tabelas: datas, valores, competência e status sem quebra; nomes longos com `TextoTruncado` (reticências + tooltip).

**Movimento** (`src/styles/motion.ts` é a fonte única; vira variáveis CSS `--dur-*`/`--ease-*` no boot):

| Componente | Uso |
|---|---|
| `Reveal` | entrada suave de um bloco |
| `Stagger` + `StaggerItem` | entrada em sequência de listas e grades de KPI |
| `AnimatedNumber` | número que conta até o valor (leitor de tela recebe só o final) |
| `Collapse` | abrir/fechar com altura animada |

Tudo respeita `prefers-reduced-motion` (hook `useMovimentoReduzido` e bloco no CSS). Troca de página usa View Transitions do React Router (`viewTransition` em `Link`/`navigate`).

## Tipos da API

Gerados da OpenAPI do backend (o endpoint `/v3/api-docs` só existe no perfil `dev`):

```bash
# backend rodando com SPRING_PROFILES_ACTIVE=dev
npm run gerar:api    # escreve src/lib/api/schema.d.ts
```

Não edite `schema.d.ts`; os aliases usados no código ficam em `src/lib/api/tipos.ts`.

## Testes

```bash
npm test                  # Vitest (jsdom): formatadores, http (ProblemDetail, CSRF, upload, 202),
                          # DataTable, useMovimentoReduzido, ClienteForm, paleta de comandos,
                          # tabela de demonstração (AH/AV, recolher, origem), assistente da planilha,
                          # aba Tributos (matriz e periodicidade, detalhe da célula, lucro real, estados vazios)
npx playwright install chromium   # uma vez
npx playwright test       # e2e: fumaça, ECD → Demonstrações → origem do valor,
                          # ECD + ECF + EFD → Tributos → célula → origem,
                          # acessibilidade (axe, WCAG A/AA, dois temas) nas telas principais
```

O e2e usa o Vite em `:5173` (sobe sozinho se não estiver rodando) e precisa do backend em `:8080` com banco; cada execução cria um escritório novo. Os arquivos de teste (ECD, ECF, EFD, planilhas) são as amostras sintéticas do backend (`backend/src/test/resources/amostras`).
