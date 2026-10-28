# FiscalHub — Normativas para o cálculo de capacidade de pagamento (Federal e Paraná)

*04/10/2026 · levantamento inicial. Os pontos marcados **[confirmar]** precisam de leitura do texto vigente antes de virar regra. Complementa `claude/normativas-parcelamento.md`.*

## 1. Visão geral

Cada fazenda tem a sua metodologia, e as duas servem a mesma finalidade: **definir se o contribuinte tem acesso a transação e quanto de desconto e prazo pode obter**. Por isso o FiscalHub calcula três coisas separadas, sem misturá-las:

| Camada | Para que serve | Base |
|---|---|---|
| **CAPAG federal (PGFN)** | Classificação A–D dos débitos na dívida ativa da União; desconto e prazo na transação; fundamentar pedido de revisão da CAPAG presumida | Lei 13.988/2020; Portaria PGFN 6.757/2022 (alterada pela 1.457/2024) |
| **Capacidade de pagamento Paraná (PGE/SEFA)** | Classificação do devedor e dos créditos de ICMS na transação estadual | Lei 21.860/2023; Decreto 7.855/2024; Resolução Conjunta PGE/SEFA 01/2025 (redação vigente desde 24/08/2026) |
| **Análise gerencial** (não normativa) | Parcela mensal sustentável, para escolher entre as modalidades de parcelamento | Indicadores das demonstrações (EBITDA, caixa operacional, liquidez) |

Cada resultado mostra a norma e a versão usadas. A camada gerencial aparece sempre rotulada como "análise do consultor, não é critério oficial".

## 2. Federal — CAPAG da PGFN

**Conceito.** Valor em reais que estima quanto o contribuinte conseguiria pagar do passivo fiscal **num cenário de execução fiscal, em 5 anos**, sem descontos.

**Classificação dos débitos (Portaria PGFN 6.757/2022)**, comparando a CAPAG com o valor consolidado das dívidas em cobrança na PGFN, incluindo FGTS:

| Classe | Critério | Recuperabilidade | Desconto na transação |
|---|---|---|---|
| A | CAPAG ≥ 2 × dívida | Alta | Não |
| B | 1 × dívida ≤ CAPAG < 2 × dívida | Média | Não |
| C | 0,5 × dívida ≤ CAPAG < 1 × dívida | Difícil | Sim |
| D | CAPAG < 0,5 × dívida | Irrecuperável | Sim (maior) |

**Créditos considerados irrecuperáveis automaticamente (art. 25)** — devem ser identificados antes do cálculo:
- inscritos há mais de 15 anos sem garantia nem suspensão;
- suspensos por decisão judicial há mais de 10 anos;
- devedor em falência, recuperação judicial ou extrajudicial, liquidação ou intervenção;
- CNPJ baixado ou inapto. **[confirmar a lista completa e a redação vigente]**

**CAPAG presumida (CAPAG-P).** Calculada pela PGFN por modelo estatístico com dados que o fisco já tem (receita declarada, ECF, pagamentos, retenções, notas, bens, garantias). **A fórmula não é publicada**: o valor e a classe aparecem no Regularize. No FiscalHub, a CAPAG-P **vem importada** do documento do Regularize, nunca estimada por nós.

**CAPAG efetiva: revisão pela pessoa jurídica (arts. 30 e 33, com a Portaria 1.457/2024).** É aqui que o FiscalHub agrega valor. A PGFN recalcula a capacidade por dois métodos:
1. **Resultado operacional:** Geração de Resultados Econômicos Futuros (GRF) + Patrimônio Líquido Realizável (PLR), em 5 anos, ajustados para retirar saídas sem preferência sobre o crédito tributário.
2. **Fluxo de caixa:** projeção de 5 anos a partir da DFC pelo **método direto**, representando o mínimo recuperável respeitando a preferência do crédito tributário.

Documentos exigidos no pedido de revisão (prazo de 30 dias após conhecer a CAPAG-P):
- BP e DRE **mapeados para o plano de contas referencial da RFB**;
- **DFC pelo método direto** dos 2 últimos exercícios completos + exercício em curso até o último trimestre fechado, assinada por contador;
- declarações obrigatórias dos representantes e do contador (dados cadastrais e patrimoniais, operações com partes relacionadas, créditos contra a União, alienação ou oneração de bens);
- opcionais: relação de bens e dívidas, credores, extratos bancários.

**[confirmar no texto da Portaria: fórmulas exatas de GRF e PLR, percentuais de realização dos ativos, ajustes de despesas e tratamento das dívidas com preferência]**

**Como o FiscalHub implementa:**
- Calcula a **CAPAG estimada pelos dois métodos** a partir da ECD importada: plano referencial já mapeado na Fase 1; DFC direta derivada dos lançamentos I200/I250, classificando os movimentos das contas de caixa e bancos pela contrapartida.
- Compara com a **CAPAG-P importada** e com a dívida consolidada (extrato PGFN importado). Mostra a classe resultante em cada cenário.
- **Ponto de atenção:** se a CAPAG estimada indicar classe C ou D e a CAPAG-P indicar A ou B, a revisão é potencialmente vantajosa, com estimativa do desconto que se abriria no edital vigente.
- Gera o **dossiê de revisão**: BP/DRE referenciais, DFC direta e memória de cálculo, para o contador revisar e assinar.

## 3. Paraná — capacidade de pagamento e classificação de créditos (PGE/SEFA)

A metodologia estadual **não usa demonstrações contábeis**. É um modelo de pontuação com dados cadastrais, de faturamento declarado e da dívida ativa.

**3.1 Capacidade de pagamento do devedor com CAD/ICMS (arts. 7º e 8º)**

| Critério | Pontos |
|---|---|
| Situação cadastral | ativa 4 · ativa CEC 3 · baixada/paralisada 2 · cancelada 1 |
| Tempo de inatividade | ativa 4 · até 1 ano 3 · 1–3 anos 2 · mais de 3 anos 1 |
| Índice de endividamento = dívida ativa com exigibilidade imediata ÷ faturamento declarado em 12 meses | até 10% 4 · 10–30% 3 · 30–50% 2 · acima de 50% 1 |
| Indicativo de pagamento | até 30 dias 3 · 30–90 dias 2 · mais de 90 dias 1 |
| Classificação das CDAs do devedor | sem débitos 5 … até 20% em classe A/B 1 **[confirmar faixas intermediárias]** |

**Classe do devedor (art. 8º, redação desde 24/08/2026), pela soma simples:** A > 17 · B 15–17 · C 12–14 · D < 12.

**3.2 Classificação dos créditos de ICMS (arts. 2º a 5º)**

Seis variáveis, cada uma com um índice:

| Variável | Índice |
|---|---|
| Parcelamentos anteriores | nenhum 1,000 · 1–2 1,646 · 3 ou mais 1,519 |
| Tempo de inscrição | até 1 ano 9,337 · 1–2 anos 2,748 · 2–4 anos 1,608 · mais de 4 anos 1,000 |
| Origem | imposto declarado 2,075 · lançamento de ofício 1,000 |
| Valor | 6 faixas, de 3,850 (até R$ 499,99) a 1,000 (acima de R$ 249.999,99) **[confirmar faixas intermediárias]** |
| Ajuizamento | não ajuizada 3,766 · ajuizada 1,000 · desistência 0,500 |
| Situação cadastral | ativa 7,861 … cancelada 1,000 **[confirmar faixas intermediárias]** |

**Índice final = média geométrica dos seis.** Classes:
- A: índice > 2,865548 (probabilidade de pagamento de 75% por exercício)
- B: 2,212273 < índice ≤ 2,865548 (50%)
- C: 1,558998 < índice ≤ 2,212273 (25%)
- D: índice ≤ 1,558998 (5%)

**Ajuste com parcelamento ativo (art. 5º):** ≥ 70% das parcelas pagas → classe A; de 30% a 69% → classe B; abaixo de 30% → mantém a classe calculada.

**Outros créditos (ITCMD, IPVA, não tributários — art. 10):** pontuação = 1,0 × pontos de idade + 0,66 × pontos de valor. Classes: A 1,660–2,905 · B 2,905–4,150 · C 4,150–5,395 · D > 5,395.

**Presumidamente irrecuperáveis (art. 12):** suspensão judicial há mais de 10 anos; devedor em falência, recuperação judicial ou extrajudicial, liquidação ou intervenção.

**Atualização:** a classificação oficial é refeita a cada 6 meses (art. 16), e os índices são revistos no máximo a cada 3 anos (art. 15). O contribuinte consulta a classificação no Portal Receita/PR, com Relatório para Transação Individual (RTI) — Decreto 7.855/2024.

**Efeito na transação:** desconto só para créditos de baixa ou improvável recuperação, até 65% do valor total. Prazo de até 120 meses, ou 145 para PF, ME e EPP, inversamente proporcional à recuperabilidade. **[confirmar a tabela do Anexo I do Decreto 7.855/2024]**. O Portal Refis da SEFA lista o Renegocia PR por adesão como encerrado em 29/05/2026, enquanto o FAQ da PGE descreve a transação como ativa. **[confirmar quais modalidades seguem abertas, provavelmente a transação individual]**

**Como o FiscalHub implementa:**
- Dados de entrada, todos importados: extrato da dívida ativa estadual (CDAs com data de inscrição, origem, valor, ajuizamento, parcelamentos); situação cadastral do CAD/ICMS; faturamento de 12 meses pela EFD ICMS/IPI (ou GIA).
- **Recalcula** a classe do devedor e de cada CDA, e compara com a classificação oficial importada do Portal Receita/PR (RTI).
- Simula o efeito de ações sobre a classe: por exemplo, quitar parte das CDAs e com isso mudar o índice de endividamento, ou desistir de uma ação judicial.

## 4. Camada gerencial (não normativa)
- EBITDA, caixa operacional (FCO), liquidez, endividamento, cobertura de juros.
- **Parcela mensal sustentável** = fração configurável do caixa operacional livre. Usada para escolher entre as modalidades de parcelamento e para alertar quando uma parcela compromete o caixa.
- Sempre rotulada como análise do consultor. Nunca substitui a CAPAG ou a classificação estadual nos relatórios.

## 5. Impactos no plano
- **Fase 1:** além de I150/I155/J100/J150, a ECD precisa dos lançamentos (I200/I250) para derivar a DFC direta. Exige processamento assíncrono, porque são arquivos grandes.
- **Fase 2:** novas importações — documento de CAPAG e débitos do Regularize, extrato da dívida ativa estadual (PGE-PR/SEFA), situação cadastral CAD/ICMS e RTI do Portal Receita/PR.
- **Fase 4:** dividida em três blocos (CAPAG federal, classificação PR, gerencial), com regras versionadas (`RegraCapacidadePagamento`) como no parcelamento.
- **Fase 5:** a simulação de transação usa a classe da camada normativa (oficial importada ou estimada, sempre indicando qual) para definir desconto e prazo.
- **Fase 6:** novos pontos de atenção — revisão de CAPAG potencialmente vantajosa; CDAs perto de mudar de classe; endividamento estadual perto de uma faixa de pontuação.

## Conferência com os textos oficiais (28/10/2026)

Textos baixados e registrados (URL, data e SHA-256) na tabela `regra_capacidade_pagamento`:

- **Resolução Conjunta PGE/SEFA 01/2025** (PDF oficial da SEFA-PR, SHA-256 `92ae28f2…`, em `fontes/`), com a RC 15/2026:
  - **confirmou** todos os índices do art. 3º e os limites do art. 4º; as faixas que estavam **[confirmar]**: valor
    (3,850 · 2,820 · 1,967 · 1,459 · 1,195 · 1,000, cortes em 499,99 / 2.499,99 / 7.499,99 / 24.999,99 / 249.999,99) e
    situação cadastral da CDA (ativa 7,861 · ativa CEC 4,422 · baixada ou paralisada 2,631 · cancelada 1,000 · devedor não
    cadastrado 2,451);
  - **confirmou** o critério V do art. 7º (incluído pela RC 15/2026, efeitos de 24/08/2026): sem débitos 5; acima de 80%
    das CDAs em A/B 4; acima de 50% a 80% 3; acima de 20% a 50% 2; até 20% 1;
  - **confirmou** o art. 8º desde 24/08/2026 (A > 17 · B 15–17 · C 12–14 · D < 12) e registrou a **redação original**
    (14/04/2025 a 23/08/2026: A > 12 · B 10–12 · C 7–9 · D < 7);
  - art. 5º: o percentual de parcelas pagas **substitui** o índice (≥ 70% A; 30% a 69% B; abaixo de 30% vale o índice);
  - art. 10 (outros créditos): idade com pontos 1 (até 2 anos) a 4 (mais de 4); valor 1 (até R$ 10 mil) a 4 (acima de
    R$ 500 mil), pesos 1,0 e 0,66.
- **Portaria PGFN 6.757/2022** (compilada com 6.941/2022, 10.826/2022 e 1.241/2023) e **Portaria PGFN 1.457/2024**:
  - art. 25 **corrigiu** a lista do documento: além de falência/recuperação/liquidação/intervenção e da suspensão
    judicial há mais de 10 anos, entram PF com indicativo de óbito e execução arquivada pelo art. 40 da LEF há mais de 3
    anos; a lista de situações do CNPJ é fechada (baixado por inaptidão, inexistência de fato, omissão contumaz,
    encerramento da falência ou da liquidação; inapto por localização desconhecida, inexistência de fato, omisso e não
    localização, omissão contumaz; suspenso por inexistência de fato) — o "inapto por omissão de declarações" foi
    revogado pela 1.241/2023; a 1.457/2024 incluiu que a baixa por liquidação voluntária **não** presume a
    irrecuperabilidade e como comprovar a recuperação extrajudicial;
  - art. 30, I (1.457/2024): a DFC é **pelo método direto**, dos dois últimos exercícios e do exercício em curso;
  - **não estão no texto**: os múltiplos das classes A–D (2x, 1x, 0,5x), as fórmulas de GRF e PLR, os percentuais de
    realização e qual método prevalece (a CAPAG efetiva é calculada pela PGFN, art. 32). No FiscalHub viraram parâmetros
    do escritório, e o resultado é rotulado como estimativa.

## Fontes
- [PGFN — perguntas frequentes sobre capacidade de pagamento](https://www.gov.br/pgfn/pt-br/servicos/perguntas-frequentes/sobre-a-capacidade-de-pagamento)
- [PGFN — pedir revisão de capacidade de pagamento](https://www.gov.br/pgfn/pt-br/servicos/orientacoes-contribuintes/pedir-revisao-de-capacidade-de-pagamento-para-fins-de-negociacao-perante-a-fazenda-nacional)
- [Conjur — CAPAG e sua relação com o desconto concedido](https://www.conjur.com.br/2023-ago-29/paulo-lima-capag-relacao-desconto-concedido/)
- [Barbieri Advogados — CAPAG na transação tributária](https://www.barbieriadvogados.com/capag-pgfn-transacao-tributaria/)
- [TMM Advogados — alterações da Portaria PGFN 1.457/2024](https://www.tmmadvogados.com.br/publicacoes/atualizacoes-importantes-na-portaria-pgfn-no-6757-2022-com-a-edicao-da-portaria-pgfn-no-1457-2024/)
- [Migalhas — o que muda e o papel da CAPAG](https://www.migalhas.com.br/depeso/459123/o-que-muda-e-o-papel-da-capag)
- [Resolução Conjunta PGE/SEFA 01/2025 (SEFA-PR)](https://www.sefanet.pr.gov.br/dados/SEFADOCUMENTOS/127202500001.pdf)
- [PGE-PR — Renegocia Paraná, perguntas frequentes](https://www.pge.pr.gov.br/Pagina/Renegocia-Parana-Perguntas-Frequentes)
- [Portal Refis SEFA-PR](https://portalrefis.sefa.pr.gov.br/)
