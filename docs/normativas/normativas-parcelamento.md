# FiscalHub — Normativas para a simulação de parcelamento (Federal e Paraná)

*04/10/2026 · levantamento inicial (conferido com os textos vigentes em 29/10/2026: seção 7) para orientar o motor de simulação. Os pontos marcados **[confirmar]** precisam de leitura do texto vigente antes de virar regra no sistema. Programas e editais mudam com frequência; todas as regras devem ser cadastradas com vigência.*

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

## 7. Conferência com os textos vigentes (29/10/2026, Fase 5)

Regras implantadas em `backend/src/main/resources/parcelamento/regras.json` (URL, data e SHA-256 de cada texto). Na
lista abaixo, **confirmado** quer dizer que o valor do levantamento acima bate com o texto; **corrigido**, que o texto
diz outra coisa; **fonte secundária**, que o oficial não pôde ser baixado e foi usada a cópia indicada.

**Federal — RFB (IN RFB 2.063/2022, cópia do DOU de 31/01/2022, fonte secundária: o portal de normas da RFB não devolveu o ato)**
- Confirmado: até 60 prestações (art. 2º); mínimo de R$ 200 (PF) e R$ 500 (PJ) (art. 10); prestação + SELIC acumulada
  do mês seguinte à consolidação até o anterior ao pagamento + 1% (art. 11); reparcelamento com 10% (histórico de
  parcelamento) ou 20% (histórico de reparcelamento) (art. 17); rescisão por 3 prestações ou até 2 com as demais pagas
  ou a última vencida (art. 18).
- Corrigido: a lista de vedações está no art. 20 da IN (e no art. 14 da lei), com 10 incisos (retidos na fonte, IOF
  retido, valores recebidos por agentes arrecadadores, tributos na importação, FINOR/FINAM/FUNRES, estimativas de
  IRPJ/CSLL, carnê-leão obrigatório, débito com parcelamento anterior não quitado salvo reparcelamento, falência ou
  insolvência, RET); o simplificado (art. 21) dispensa essas vedações e a IN não fixa limite de valor. O FiscalHub
  detecta hoje IRRF (retenção) e débito já negociado; as demais vedações dependem de dado que os documentos não trazem.
- Não conferido: alterações da IN RFB 2.284/2025.
- **Decisão pendente:** a IN (arts. 4º e 13) condiciona o pedido ao pagamento da 1ª prestação no mês do pedido; o teste
  normativo do motor (valores mantidos) coloca a 1ª no mês seguinte à consolidação. A regra `RFB_*` está com
  `primeiraParcela: MES_SEGUINTE` até a decisão (mudar é só trocar o campo).

**Federal — PGFN ordinário (Portaria PGFN 448/2019 compilada, fonte secundária: normaslegais.com.br)**
- Confirmado: até 60 (art. 2º); 1ª até o último dia útil do mês do requerimento (art. 4º); consolidação com principal,
  multas, juros e encargo (art. 6º); mínimo de R$ 200 PF e R$ 500 PJ (art. 8º); SELIC + 1% (art. 9º); reparcelamento
  com 10% ou 20% (art. 17); rescisão (art. 18).
- Corrigido o **[confirmar]** do teto de garantia: sem garantia até R$ 1.000.000,00 consolidados; acima, garantia real
  ou fidejussória (arts. 20 e 22).
- Encargo legal de 20% (DL 1.025/1969): na massa, bate com o encargo do extrato do Regularize.

**Federal — transação por adesão (Edital PGDAU 6/2026, impressão do DOU, fonte secundária; Edital PGDAU 10/2026, reprodução em contadores.cnt.br, fonte secundária; Lei 13.988/2020 lida no Planalto, sem hash: download recusado)**
- Confirmado: prorrogação pelo Edital 10/2026 — adesão de 01/10/2026 a 29/01/2027, inscrições até 03/07/2026 (pequeno
  valor: 01/10/2025); os Editais 8/2026 (até 20/12/2026) e 9/2026 (Desenrola MEI, até 29/01/2027) também foram
  prorrogados. Nenhum edital novo encontrado para empresas em 29/10/2026.
- Confirmado: até R$ 45 milhões por sujeito passivo (art. 2º); regra geral (art. 4º): desconto de até 100% de juros,
  multas e encargos limitado a 65% do total de cada inscrição; entrada de 6% em até 6 prestações e saldo em até 114;
  sem desconto, prazo total de 60 meses; 60 parcelas para as contribuições do art. 195, I, a, e II da CF. Prestação
  mínima de R$ 100 e SELIC + 1% (art. 18); rescisão por 3 prestações (art. 20); impedimento de 2 anos (art. 21, III;
  Lei 13.988, art. 4º, §4º); adesão abrange todas as inscrições elegíveis, salvo as já negociadas (art. 13).
- Corrigido: a entrada de 5% em 12 + 108 é dos créditos **irrecuperáveis** pelo art. 25 da Portaria 6.757 (art. 7º do
  edital), não da classe D da CAPAG; PF/ME/EPP: 6% em 12 + 133 com limite de 70% (art. 5º) — não modelado.
- Corrigido o **[confirmar]** da tabela de descontos por CAPAG: o edital **não tem tabela por classe**. Ele fixa o
  máximo; a Portaria 6.757 (arts. 21 e 24) dá desconto só a quem tem capacidade insuficiente (tipos C e D) e a PGFN
  gradua o desconto efetivo. O FiscalHub mostra o máximo do edital para C e D, rotulado.

**Paraná (RICMS/PR, PDF oficial da SEFA-PR atualizado até o Decreto 15.266/2026, SHA-256 dc917a25…)**
- Confirmado: até 60 parcelas (art. 81, Lei 11.580, art. 41); garantia acima de 5.000 UPF/PR com mais de 36 parcelas
  (art. 82, §§6º e 7º); confissão irretratável (art. 81, §4º).
- Corrigido / completado os **[confirmar]**: valor mínimo de 30 UPF/PR a parcelar e 6 UPF/PR por parcela (art. 83, §1º);
  parcela inicial na concessão (art. 83, §2º); juros vincendos da 2ª parcela em diante pela soma da SELIC sobre imposto
  e multa (art. 81, §3º, I) — não é SELIC + 1%; imposto declarado na EFD só depois do prazo da multa reduzida ou com a
  multa de 10% (art. 81, §§1º, I, e 7º), **limitado a 24 parcelas e 4 meses de referência** (art. 81, §8º); vedado o ICMS
  declarado em GIA-ST ou DeSTDA (§10) — o ICMS-ST da EFD é parcelável; dívida ativa: 1 parcelamento em até 12, 1 em até
  24, 1 em até 36 e 2 em até 60 por contribuinte (§12); rescisão por 3 parcelas ou pelas 2 últimas há mais de 60 dias
  (art. 84, com a revogação do inciso I pelo Decreto 14.766/2026); reparcelamento só depois de rescisão e inscrição,
  com 6 parcelas na assinatura entre 37 e 60 (art. 84, §3º).
- Confirmado: multa de 20% reduzida a 0,33% ao dia até o 30º dia e a 50% (10%) do 31º dia até a inscrição (art. 80).
- **UPF/PR:** a SEFA-PR publica o valor mês a mês por resolução; foram baixadas as de fevereiro a abril de 2025 (R$ 141,62,
  141,85 e 143,71). Os valores de 2026 não foram encontrados em fonte oficial: as regras do PR que dependem da UPF ficam
  bloqueadas com aviso até a resolução do mês entrar em `upf-pr.csv`.
- Regulariza Paraná (Decreto 12.099/2025): encerrado em 31/03/2026, registrado como histórico.

**Portaria PGFN 6.757/2022 e Portaria Conjunta 1.751/2014:** nova tentativa no DOU em 29/10/2026 respondeu 404; mantida a
fonte secundária da Fase 4 (marcada em `regra_capacidade_pagamento.fonte_secundaria`, V25).

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
