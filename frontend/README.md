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
                demonstração do lucro, saldos fiscais), situacao-fiscal (débitos, parcelamentos,
                pendências, CAPAG, conciliação, origem por documento), simulacoes,
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

- **Matriz** (`features/tributos/components/matriz-tributos.tsx`), montada da conciliação (`GET /conciliacao`): tributos por esfera (estaduais por UF) × competências (mensal, trimestral ou anual, somando os meses), com **Apurado, Declarado, Pago e Diferença** (`Metrica`s em `features/tributos/matriz.ts`) e o status em selo (texto sempre; cor só de estado; na célula agregada, o pior). Filtro por status. A célula abre a gaveta: os três valores com a origem (arquivo, página ou registro, linha e trecho), a diferença, os acréscimos devidos pelo atraso, o débito correspondente na situação fiscal e o cálculo das apurações do período (base, apurado, deduções, a recolher, linhas do cálculo, obrigações).
- **Alertas da conciliação**: diferença sem débito na situação fiscal e débito em aberto sem diferença.
- **IRPJ e CSLL**: por período de apuração (ajuste anual ou trimestre), regime e demonstração em cascata (lucro, adições, exclusões, compensação, base presumida, cálculo).
- **Saldos fiscais**: cards do saldo mais recente de cada tipo e a evolução por período.
- **Alertas de conferência**: as inconsistências das escriturações fiscais e a conferência ECD × ECF.

## Aba Situação fiscal

- **Certidão**: o que a impede (débito em aberto, inscrição sem negociação), em destaque.
- **Débitos** por órgão e situação (filtros), com o total por órgão, selos de impeditivo e de fontes divergentes e a origem de cada fonte.
- **Parcelamentos e transações** com a barra de parcelas (`BarraProgresso`, scaleX, respeita movimento reduzido), **pendências** e a **CAPAG oficial** (classe, razão capacidade ÷ passivo e alerta de inconsistência com a Portaria).
- **Visão geral** do cliente: total por órgão, divergências da conciliação e certidão (`AnimatedNumber`, `Stagger`).

## Aba Relatórios e Configurações → Escritório

- **Relatórios** (`features/relatorios`): tipo, período (competências; na carga, exercícios) e opções; **prévia** do HTML gerado pelo backend (o mesmo template do PDF) num `iframe` com `sandbox` vazio (sem script nem origem), atualizada 400 ms depois da última mudança; **Emitir** (ADMIN e CONSULTOR); histórico com número, tipo, período, quem, quando, hash abreviado do PDF e download do PDF e do Excel gravados na emissão.
- **Escritório** (`features/escritorio`, só ADMIN): nome de exibição, logotipo (PNG/JPEG até 1 MB), cor principal com o contraste WCAG sobre o branco do relatório (obrigatório AA) e sobre o escuro do app (aviso) e um botão com o ajuste sugerido (`lib/contraste.ts`, a mesma conta do backend), rodapé e responsável técnico; prévia ao vivo do cabeçalho e do rodapé.
- **Gráficos** (`components/grafico.tsx`, Recharts): apurado × pago por competência acima da matriz de Tributos e carga tributária efetiva por exercício na visão geral. Paleta `--chart-1..6` dos tokens (a mesma do `GraficoSvg` do PDF, validada para daltonismo e ≥ 3:1 nos dois temas), hachura por série, legenda, tooltip formatado, tabela para leitor de tela e animação só sem `prefers-reduced-motion`. Os gráficos dos relatórios são SVG gerados no backend: usos diferentes, código separado.

**Por que Recharts** (e não ECharts): componentes React declarativos em SVG, cores por variável CSS (os tokens dos dois temas sem código extra), animação desligável por gráfico; o ECharts é canvas, imperativo e maior. Fica num chunk só das telas com gráfico.

## Aba Capacidade

- **PGFN**: CAPAG oficial e estimada lado a lado, com o selo do rótulo (oficial, estimativa, gerencial) e o texto do backend; alerta de revisão vantajosa com um anel animado (só sem `prefers-reduced-motion`); memória de cálculo e irrecuperáveis (art. 25).
- **Paraná**: classe recalculada × oficial (RTI), critérios recolhíveis (`TabelaCriterios`: valor, pontos, faixa, fundamento e origem no popover), CDAs e o **simulador** (quitar/parcelar → antes × depois com `AnimatedNumber`).
- **Gerencial**: parcela sustentável, gráficos EBITDA × caixa operacional e composição da DFC direta.
- **Parâmetros** das estimativas editáveis (ADMIN e CONSULTOR). Visão geral: cartão com CAPAG oficial, classe do PR e parcela sustentável. Demonstrações: opção "DFC — método direto", mês a mês, com os lançamentos de cada linha.
- Nada é calculado na tela: tudo vem pronto do backend (`ResultadoCapacidade`).

## Aba Simulações

- **1. Débitos e data-base:** os débitos importados por órgão, a data-base (padrão hoje), o prazo (vazio = o máximo de
  cada modalidade) e o reparcelamento; nenhum valor é digitado.
- **2. Comparar:** as modalidades de cada órgão lado a lado (custo projetado, desconto, entrada, prestações, parcela,
  × à vista), a recomendada em destaque, o cenário "se a revisão da CAPAG for deferida" rotulado, o selo da parcela
  sustentável e os programas encerrados com o motivo. Entrada com `Stagger` (sem animação com movimento reduzido).
- **3. Detalhar:** débitos com a origem (popover), gráfico saldo nominal × projetado, cronograma com as parcelas
  projetadas marcadas, memória, elegibilidade e regra/fontes (fonte secundária em destaque), tudo em `Collapse`.
- **Histórico:** versões, "motor antigo", recalcular e excluir (ADMIN e CONSULTOR). Visão geral: cartão "Melhor
  proposta salva". Relatórios: "Proposta de regularização" escolhendo a simulação salva.

## Design system

Referência visual: `npm run dev` e abra `/laboratorio-design` (a rota não existe no build de produção).

**Tokens** (`src/styles/globals.css`): cores semânticas em `:root` (claro) e `.dark` — `background`, `surface`, `foreground`, `muted`, `border`, `primary` (azul-petróleo), `success`/`warning`/`danger`/`info` com `-bg`, e `sidebar-*` (menu escuro nos dois temas). Use as classes do Tailwind geradas por eles (`bg-surface`, `text-muted-foreground`…), nunca cor literal.

- Gráficos: paleta categórica `--chart-1..6` em ordem fixa (nunca cor gerada; mais de 6 séries viram "Outros").
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
                          # aba Tributos (matriz e periodicidade, detalhe da célula, lucro real, estados vazios),
                          # gráfico (tabela acessível, movimento reduzido), contraste e formulário de identidade,
                          # formatação igual à do backend (formatacao.casos.json)
npx playwright install chromium   # uma vez
npx playwright test       # e2e: fumaça, ECD → Demonstrações → origem do valor,
                          # ECD + ECF + EFD → Tributos → célula → origem,
                          # acessibilidade (axe, WCAG A/AA, dois temas) nas telas principais,
                          # massa → Relatórios → prévia → emitir → PDF e Excel (hash igual ao emitido),
                          # Configurações → Escritório (contraste e prévia),
                          # massa completa → Capacidade (CAPAG C × estimada, simulação no PR) → relatório e dossiê
```

O e2e usa o Vite em `:5173` (sobe sozinho se não estiver rodando) e precisa do backend em `:8080` com banco; cada execução cria um escritório novo. Os arquivos de teste (ECD, ECF, EFD, planilhas) são as amostras sintéticas do backend (`backend/src/test/resources/amostras`).
