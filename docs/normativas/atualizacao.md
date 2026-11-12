# Rotina de atualização normativa

As regras do FiscalHub são **dado com fonte e vigência** (nada de percentual, prazo ou faixa no código). Esta rotina diz
o que acompanhar, com que frequência, como cadastrar a mudança e como validar. Responsável: quem mantém o sistema
(o "ADMIN do sistema", configurado em `fiscalhub.admin-sistema`).

## O que monitorar

| Fonte | O que muda | Onde entra no sistema | Frequência |
|---|---|---|---|
| **DOU** (in.gov.br): Leis, MPs, Portarias PGFN e RFB, INs | acréscimos federais, parcelamentos, transação, CAPAG (Portaria PGFN 6.757/2022) | `resources/parcelamento/regras.json`, `regra_capacidade_pagamento` (migração), `resources/pontos/regras.json` | semanal |
| **Editais da PGFN** (gov.br/pgfn → Regularize → Transação por adesão) | modalidades, descontos, prazos, **datas de adesão** | `parcelamento/regras.json` (modalidade com `adesaoInicio`/`adesaoFim`) | semanal; o alerta interno avisa 30 dias antes do fim da adesão |
| **Portal Refis / programas especiais** | programas com janela de adesão | idem | semanal |
| **DOE-PR** (legislacao.pr.gov.br) e RICMS/PR | parcelamento do ICMS, Regulariza Paraná, classificação PGE/SEFA | `parcelamento/regras.json`, `regra_capacidade_pagamento` | semanal |
| **Indicadores Econômicos da SEFA-PR** | UPF/PR mensal (Resolução SEFA 360/2025) | importação automática diária (`ImportacaoUpfPrService`) | conferir no início do mês |
| **SGS do Banco Central** (série 4390) | SELIC mensal | importação automática diária (`ImportacaoSelicService`) | conferir no início do mês |
| **Portal do SPED** (sped.rfb.gov.br) | leiautes da ECD/ECF/EFD, tabelas dinâmicas e planos referenciais do ano | `resources/sped/*-leiautes.properties`, `resources/plano-referencial` | a cada publicação (início do ano e no prazo de cada escrituração) |
| **Tabela de códigos de receita** (RFB/SICALC, GR-PR) | códigos novos | `resources/receitas/codigos-receita.txt` | quando um documento cair em `CODIGO_RECEITA_DESCONHECIDO` |
| **STF/STJ** (teses com efeito sobre as regras) | ex.: ADI 5.161 sobre a distribuição de lucros com débito | `pontos/regras.json` (fundamento) e a regra | mensal |

Alertas: os índices sem o mês corrente e os editais perto do fim aparecem no alerta interno (Painel, só para o
ADMIN do sistema) e no log (`WARN` diário do `AlertasSistema`).

## Como cadastrar uma regra nova com vigência

1. **Baixe o texto oficial** e guarde a URL, a data da consulta e o SHA-256 (`sha256sum arquivo.pdf`). Fonte
   secundária (notícia, resumo) só se a oficial não abrir — e marque `secundaria: true`.
2. **Nunca altere a versão vigente**: acrescente uma nova com `vigenciaInicio` e feche a anterior com `vigenciaFim` (o
   dia anterior). Simulações e relatórios antigos guardam a versão que usaram.
   - Parcelamento, transação e acréscimos: `backend/src/main/resources/parcelamento/regras.json` (e mude `versao`).
   - CAPAG e classificação do PR: nova migração `V<n>__...sql` com o `INSERT` da regra (mesma `chave`) e o `UPDATE`
     da `vigencia_fim` da anterior, com URL e SHA-256.
   - Pontos de atenção: `resources/pontos/regras.json` (`versao` nova; `conferido: true` só com o texto lido).
   - Código de receita: uma linha em `resources/receitas/codigos-receita.txt` com a fonte.
3. Edital com janela de adesão: preencha `adesaoInicio` e `adesaoFim` — é o que dispara o alerta de 30 dias e encerra a
   modalidade sozinha depois do prazo.
4. Atualize `docs/normativas/` (tabela da norma, artigo e fonte).

## Como validar

1. Teste de valores-limite da regra (dentro e fora da vigência, nos limites de prazo e de valor):
   `MotorParcelamento*Test`, `CapacidadeRegrasEAcessoTest`, `PontosAtencaoTest`.
2. A massa fictícia: `MassaFicticiaAceiteTest` (gabarito em `src/test/resources/massa-ficticia/_gerador/valores_esperados.json`).
   Mudou um número esperado? Confira à mão contra o texto e só então atualize o gabarito, no mesmo commit.
3. `mvn verify` e o e2e (CI) verdes; a simulação de um caso real conhecido conferida por um consultor.
4. Commit com a norma, o artigo e a fonte na mensagem; nova versão no `CHANGELOG.md`.
