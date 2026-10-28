# FiscalHub — Normativas para a simulação de parcelamento (Federal e Paraná)

*04/10/2026 · levantamento inicial para orientar o motor de simulação. Os pontos marcados **[confirmar]** precisam de leitura do texto vigente antes de virar regra no sistema. Programas e editais mudam com frequência; todas as regras devem ser cadastradas com vigência.*

## 1. Princípios de modelagem

- **Regra como dado, não como código.** Cada órgão/modalidade é um registro versionado (`RegraParcelamento`) com base legal, vigência e parâmetros. Mudou a norma → nova versão, sem deploy.
- **Acréscimos moratórios separados do parcelamento.** `RegraAcrescimo` por órgão: fórmula da multa de mora, juros, encargos, conforme a situação do débito (não inscrito × inscrito em dívida ativa) e a data do fato gerador.
- **Índices importados automaticamente:** SELIC mensal (Banco Central, SGS) e UPF/PR (SEFA-PR). Nenhum índice digitado nem "estimado": sem índice do mês, a simulação fica bloqueada com aviso.
- **Rastreabilidade:** cada simulação grava a versão da norma e dos índices usada, e cita os artigos na memória de cálculo e no relatório.
- **Elegibilidade antes do cálculo:** checa vedações, janela de adesão, situação do débito e CAPAG (quando a modalidade exigir). Mostra por que uma modalidade não se aplica.
- **Conferência:** casos de teste comparados com os simuladores oficiais (Sicalc/e-CAC, Regularize, emissão de GR-PR da SEFA).

## 2. Federal — Receita Federal (débitos não inscritos)

**Acréscimos moratórios (Lei 9.430/96, art. 61)**
- Multa de mora: 0,33% por dia, a partir do dia seguinte ao vencimento, **limitada a 20%**. Atinge o teto por volta do 61º dia.
- Juros: **soma simples** das taxas SELIC mensais, do mês seguinte ao vencimento até o mês anterior ao pagamento, **+ 1% no mês do pagamento**.
- Débitos lançados de ofício: multa de ofício (Lei 9.430/96, art. 44) e reduções por pagamento ou parcelamento (Lei 8.218/91, art. 6º). **[confirmar percentuais vigentes após a Lei 14.689/2023]**

**Parcelamento ordinário e simplificado (Lei 10.522/02, arts. 10–14-F; IN RFB 2.063/2022)**
- Até 60 parcelas mensais.
- Parcela mínima: R$ 500 (PJ) e R$ 200 (PF).
- Cálculo: valor consolidado ÷ número de parcelas. **Cada parcela é acrescida de SELIC acumulada**, do mês seguinte à consolidação até o mês anterior ao pagamento, + 1% no mês do pagamento.
- Rescisão: falta de pagamento de 3 parcelas (consecutivas ou não), ou de 1 parcela estando pagas todas as demais.
- Reparcelamento: entrada de 10% do consolidado, ou 20% se já houve reparcelamento anterior.
- Vedações do art. 14 da Lei 10.522/02 (ex.: tributos retidos na fonte e não recolhidos, IOF). **[confirmar lista completa]**
- Simples Nacional (LC 123/06; Res. CGSN 140/2018): até 60 parcelas, mínimo R$ 300. MEI: mínimo R$ 50.

## 3. Federal — PGFN (débitos inscritos em dívida ativa da União)

**Acréscimos:** os mesmos da RFB **+ encargo legal de 20%** (Decreto-Lei 1.025/69).

**Parcelamento ordinário (Portaria PGFN 448/2019, com alterações)**
- Até 60 parcelas; mínimo de R$ 500 (PJ) e R$ 200 (PF).
- Parcelas com SELIC acumulada + 1%, como na RFB.
- Exige garantia acima de um teto de valor. **[confirmar teto vigente; foi alterado depois de 2019]**
- Reparcelamento com entrada de 10% ou 20%; rescisão por 3 parcelas em atraso.

**Transação tributária por adesão (Lei 13.988/2020; Portaria PGFN 6.757/2022; editais)**
- Editais vigentes: **Edital 6/2026 e Edital 9/2026, prorrogados pelo Edital PGDAU 10/2026 até 29/01/2027**. O Edital 8/2026 (Desenrola Rural) vai até 20/12/2026.
- Débitos elegíveis: inscritos até 03/07/2026 (pequeno valor: até 01/10/2025).
- Descontos de até 100% sobre juros, multas e encargo, **limitados a 65% do total** (70% para PF, ME, EPP e outros grupos).
- Até 114 parcelas (133 para PF, ME e EPP).
- Entrada variável por modalidade (ex.: 5% em até 12 vezes para créditos de difícil recuperação).
- **A CAPAG (classes A a D) define elegibilidade e desconto.** A CAPAG presumida aparece no Regularize; contestá-la segue a Portaria PGFN 1.457/2024, que exige laudo técnico, demonstrações e fluxo de caixa. A Fase 4 do FiscalHub pode ajudar a preparar essa contestação.
- No sistema, cada edital é um registro com janela de adesão, data de corte de inscrição, faixas de desconto por CAPAG, prazos e entrada. **[confirmar a tabela de descontos por CAPAG e modalidade no texto dos editais]**

## 4. Paraná — SEFA-PR (ICMS)

**Acréscimos moratórios (Lei 11.580/96)**
- Juros (art. 38): **soma da SELIC mensal + 1% no mês do pagamento**.
- **Não há atualização monetária** (FCA/UPF) cumulada com a SELIC desde a Lei 15.610/2007.
- Multa (art. 55, §1º, I): **20%** do imposto declarado e não pago. Com a redução do art. 40, fica em 0,33% por dia nos primeiros 30 dias e, **a partir do 31º dia, 50% da multa (10%)** até a inscrição em dívida ativa. **[confirmar regime após a inscrição e eventuais encargos da PGE-PR]**

**Parcelamento ordinário (Lei 11.580/96, art. 41; RICMS/PR – Decreto 7.871/2017)**
- Até 60 parcelas, conforme critérios da SEFA.
- Juros de mora sobre as parcelas, da data do acordo até o mês do pagamento de cada parcela.
- Exige garantia quando o débito passa de 5.000 UPF/PR **e** o prazo passa de 36 parcelas (fiança ou bens se ajuizado; fiança bancária ou seguro-garantia se em dívida ativa não ajuizada).
- O pedido implica confissão irretratável do débito.
- **[confirmar no RICMS/PR: parcela mínima, entrada, vedações (ex.: ICMS-ST retido), reparcelamento e rescisão]**

**Programas especiais:** nenhum com adesão aberta hoje (Portal Refis SEFA-PR). Os mais recentes servem de modelo de parâmetros para os próximos:

| Programa | Base | Encerrado em | Condições principais |
|---|---|---|---|
| Regulariza Paraná | Lei 22.764/2025; Decreto 12.099/2025 | 31/03/2026 | ICMS até 24 parcelas; multa reduzida em 95%, 80% ou 70%; juros até -60%; parcela mínima de 5 UPF/PR; SELIC a partir da 2ª parcela; rescisão com 3 parcelas em atraso |
| Renegocia PR (transação) | Lei 21.860/2023 | 29/05/2026 | ICMS de difícil recuperação, recuperação judicial ou falência; até 120 parcelas |
| REFIS | Lei 20.946/2021 | 26/09/2024 | ICMS, ITCMD; até 180 parcelas |
| Retoma Paraná | Lei 20.634/2021 | 27/03/2024 | Recuperação judicial, falência, baixadas; até 180 parcelas; até -95% |

## 5. Divergências do motor atual em relação às normas

**Receita Federal (`CalculadoraReceitaFederalService`)**
1. **Multa de mora:** aplica 0,33% por dia só até o 30º dia e salta para 20% no 31º. O correto é 0,33% por dia até atingir 20% (por volta do 61º dia).
2. **Juros:** compõe a SELIC (produto) e inclui o mês do cálculo. O correto é a soma simples até o mês anterior + 1% no mês do pagamento.
3. **Sem SELIC cadastrada, `calcularJurosAcumulados` devolve 0 sem erro.** O fallback de "1% a.m." nunca roda e os juros saem zerados sem aviso.
4. **Parcelas fixas.** A norma acresce SELIC acumulada + 1% a cada parcela, então o custo total hoje sai subestimado.
5. Não distingue RFB de PGFN: falta o encargo legal de 20% e a multa de ofício.
6. PERT e REFIS, programas encerrados, aparecem como disponíveis; a entrada de 5% está fixa no código.
7. A referência à "Portaria PGFN/RFB 15/2009" está desatualizada: hoje valem a IN RFB 2.063/2022 e a Portaria PGFN 448/2019.

**Paraná (`CalculadoraReceitaEstadualPRService`)**
1. **Aplica correção pela "UFPR"**, com fallback de "IPCA 0,5% a.m.". O PR não tem atualização monetária cumulada com SELIC desde 2007, e o índice estadual é a UPF/PR, usada só para limites e parcela mínima.
2. **Juros fixos de 1% a.m.** O correto é a soma da SELIC + 1% no mês do pagamento.
3. **Multa de 20% após 30 dias.** O correto é 10% (redução de 50%) até a inscrição em dívida ativa.
4. Entrada de 10% e parcela mínima de R$ 200 sem base legal identificada.
5. Parcelas sem os juros da SELIC.
6. O "programa especial" usa percentuais de redução digitados na requisição. Deveriam vir da tabela do programa.

## 6. Itens para o plano (Fase 0 e Fase 5)
- Fase 0: os testes do motor atual devem registrar o comportamento **e** os casos esperados pela norma. As divergências acima viram os primeiros testes vermelhos.
- Fase 5: implantar `RegraParcelamento` e `RegraAcrescimo` versionadas, a importação automática de SELIC (BCB) e UPF/PR, a checagem de elegibilidade, a correção das parcelas pela SELIC e o encargo legal da PGFN.
- Processo contínuo: alguém do produto acompanha o DOU, o DOE-PR, os editais da PGFN e o Portal Refis da SEFA-PR, e cadastra novas versões de regra.

## Fontes
- [Receita Federal — parcelamento (gov.br)](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/formularios/impostos/parcelamento/parcelamento)
- [Como parcelar dívidas com a Receita Federal em 2026 (Razonet)](https://razonet.com.br/contabilidade-digital/como-parcelar-dividas-com-a-receita-federal-em-2026)
- [Portaria PGFN 448/2019 (Normas Legais)](https://www.normaslegais.com.br/legislacao/portaria-pgfn-448-2019.htm)
- [PGFN — novos editais de transação 2026](https://www.gov.br/pgfn/pt-br/assuntos/noticias/2026/novos-editais-de-transacao-da-pgfn-estao-disponiveis)
- [Edital PGDAU 10/2026 — prazos prorrogados (TX Kapital)](https://txkapital.com.br/2026/10/01/edital-10-2026-da-pgfn-prazos-prorrogados-e-novos-debitos-elegiveis-a-transacao/)
- [CAPAG e Portaria PGFN 1.457/2024 (Migalhas)](https://www.migalhas.com.br/depeso/459123/o-que-muda-e-o-papel-da-capag)
- [SEFA-PR — ICMS notas explicativas (juros e multa)](https://www.fazenda.pr.gov.br/Pagina/ICMS-Notas-explicativas)
- [Lei PR 15.610/2007 — fim da atualização monetária (COAD)](https://www.coad.com.br/home/noticias-detalhe/10822/debito-fiscal-nao-esta-mais-sujeito-a-atualizacao-monetaria)
- [Parcelamento do ICMS PR — Lei 11.580/96, art. 41 (Estratégia)](https://concursos.estrategia.com/portal/parcelamento-icms-sefaz-pr/)
- [Portal Refis SEFA-PR](https://portalrefis.sefa.pr.gov.br/)
- [Decreto PR 12.099/2025 — Regulariza Paraná](https://www.sefanet.pr.gov.br/dados/SEFADOCUMENTOS/102202512099.pdf)
