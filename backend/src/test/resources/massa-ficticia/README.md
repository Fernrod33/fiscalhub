# Massa de teste fictícia — FiscalHub

Todos os dados são **fictícios** (empresas, CNPJs, CPFs, IEs, chaves de NF-e, recibos, inscrições, processos e
valores). Os PDFs trazem a marca "DOCUMENTO FICTÍCIO" e não reproduzem brasões nem logotipos oficiais.
CNPJ/CPF/IE/chaves têm dígitos verificadores válidos, para passar em validações de formato.

Fica versionada em `backend/src/test/resources/massa-ficticia/` e é a base do `MassaFicticiaAceiteTest`. A pasta
`amostras-locais/` (fora do git) fica só para documentos reais. Os totais esperados ficam em
`_gerador/valores_esperados.json`.

## Como regenerar

Requer Python 3 e o reportlab (só para os PDFs), instalado no `.venv` da raiz do repositório. Na pasta `backend/`:

```
../.venv/Scripts/python -m pip install reportlab      # uma vez (Linux/macOS: ../.venv/bin/python)
../.venv/Scripts/python -I src/test/resources/massa-ficticia/_gerador/gerar_tudo.py
```

O gerador reescreve os arquivos desta pasta e o `valores_esperados.json`, e lê o plano referencial oficial de
`backend/src/main/resources/plano-referencial`. Os PDFs saem com outra data de criação a cada geração (o conteúdo é o
mesmo). Depois de regenerar, rode `mvn test -Dtest=MassaFicticiaAceiteTest`: se um valor mudou, o teste aponta qual.

## Empresas

| Empresa | CNPJ | Regime | Usada em |
|---|---|---|---|
| METALURGICA PINHEIRAL INDUSTRIA E COMERCIO LTDA (Cascavel/PR, IE 9012345663) | 11.222.333/0001-81 | Lucro Real anual (balancete de suspensão) | tudo, exceto a ECF de Presumido |
| VALE DO IGUACU SERVICOS TECNICOS LTDA (Cascavel/PR) | 44.555.666/0001-81 | Lucro Presumido trimestral | ECF de Lucro Presumido |

## Arquivos

| Subpasta | Arquivo(s) | Observação |
|---|---|---|
| `ecd\` | ECD 2022, 2023, 2024, 2025 + **ECD 2024 SUBSTITUTA** | Leiaute 9, ISO-8859-1, CRLF. Lançamentos fecham (D = C), I155 com continuidade mês a mês, BP fecha (Ativo = Passivo + PL), QTD_LIN de I030/J900 = 9999. A substituta tem `IND_FIN_ESC=1`, `COD_HASH_SUB` preenchido e 1 lançamento de reclassificação (fretes x administrativas, R$ 12.500,00 em 11/2024) — resultado igual |
| `ecf\` | ECF 2025 Lucro Real (com **prejuízo fiscal**) e ECF 2025 Lucro Presumido | Lucro Real: K155/K355 a partir da ECD 2025, L100/L300, e-Lalur/e-Lacs (M300/M350, M410, M500), N500/N630/N650/N670. Presumido: P030/P200/P300/P400/P500 nos 4 trimestres |
| `efd-contribuicoes\` | 01 a 12/2025 e 08/2026 | Não cumulativo, C100/C170 individualizado, M100/M105/M200/M205/M210 e M500/M505/M600/M605/M610. **2025**: uma compra e uma venda por mês com exatamente as bases, o PIS e a COFINS lançados na ECD 2025 |
| `efd-icms-ipi\` | 01 a 12/2025, 01 a 08/2026 | 08/2026: **saldo credor** de ICMS próprio + **ICMS-ST** retido (E200/E210/E250) + IPI (E500/E510/E520). **2025**: os mesmos ICMS e IPI da ECD 2025, com ICMS a recolher (E116, código 1015). **01 a 07/2026**: só ICMS (débitos, créditos, saldo credor e ST do RTI) — completam os 12 meses de faturamento do PR |
| `situacao-fiscal\` | Relatório de Situação Fiscal (RFB + PGFN) | |
| `pgfn\` | Extrato de inscrições (Regularize) + tela CAPAG | O extrato traz os dados das inscrições (data, garantia, suspensão) e a situação no CNPJ: insumos do art. 25 da Portaria PGFN 6.757/2022 |
| `dctf\` | DCTF Mensal 11/2024 (.dec + PDF) | PA 11/2024 porque a DCTF Mensal foi substituída pela DCTFWeb/MIT a partir de 2025 |
| `dctfweb\` | DCTFWeb 08/2026 declaração completa | Previdenciário + IRRF (eSocial/Reinf) + PIS/COFINS/IPI (MIT) |
| `pagamentos\` | Relatório consolidado + 3 DARF | |
| `parana\` | Conta corrente/dívida ativa, RTI, relação de pagamentos GR-PR + 2 guias GR-PR | Conta corrente: ICMS de 2025 (declarado e quitado), 3 CDAs com o detalhamento (inscrição, origem, ajuizamento, parcelamentos) e a situação do CAD/ICMS. RTI: a classificação do devedor e das CDAs. Relação de GR-PR: os 12 recolhimentos de ICMS de 2025 |

## Valores-chave para conferência

- **ECD — resultado do exercício:** 2022 R$ 428.854,24 · 2023 R$ 490.459,10 · 2024 −R$ 78.771,68 · 2025 −R$ 150.156,17
- **ECF Lucro Real 2025:** lucro líquido −150.156,17 + adições 18.450,00 (multa de ofício) = **prejuízo fiscal 131.706,17**; saldo de prejuízo anterior 231.071,68 (152.300,00 de 2019 + 78.771,68 de 2024) → saldo final 362.777,85 (IRPJ e CSLL)
- **ECF Presumido 2025 (32%):** adicional de IRPJ em todos os trimestres (base > R$ 60 mil)
- **EFD ICMS 08/2026:** débitos 307.500,00 · créditos 192.750,00 · saldo credor anterior 480.000,00 → **saldo credor a transportar 365.250,00**; **ICMS-ST retido 44.850,00** (venc. 10/09/2026)
- **IPI 08/2026:** débitos 100.000,00 − créditos 72.500,00 = **27.500,00**
- **PIS 08/2026:** 27.926,25 − 20.744,63 = **7.181,62** · **COFINS:** 128.630,00 − 95.551,00 = **33.079,00** (bases já sem o ICMS)
- **DCTFWeb 08/2026:** total declarado 148.806,02
- **PGFN:** passivo total R$ 512.791,83 (4 inscrições, 3 negociadas)

- **Carga tributária 2025:** tributos (ICMS, IPI, PIS e COFINS das EFD; IRPJ/CSLL zerados pelo prejuízo) ÷ receita bruta da ECD — `carga_tributaria_2025`.
- **DFC direta 2024 e 2025** por categoria, calculada pelo gerador a partir dos lançamentos (rateio entre as contrapartidas do mesmo sentido) e conferida com a variação do caixa — `dfc_direta`.
- **Classificação do PR** (Resolução Conjunta PGE/SEFA 01/2025, data do cálculo 14/10/2026): devedor 4 + 4 + 4 + 2 + 3 = **17 → classe B**; CDAs B (parcelada 12/36), B e C (ajuizada). Quitando a CDA ajuizada: 18 → **classe A** — `classificacao_pr`.

> **2025 na conciliação:** a massa traz as EFD (apurado) e as GR-PR do ICMS, mas não a DCTFWeb nem os DARF de 2025: PIS, COFINS e IPI de 2025 aparecem como **não declarados** (esperado).

## Pontos de atenção plantados (gabarito)

1. **COFINS 08/2026 paga a menor:** declarada 33.079,00 (EFD-Contribuições e DCTFWeb), DARF com 28.000,00 → diferença 5.079,00, que aparece como débito na Situação Fiscal.
2. **IPI 11/2024 pago em atraso sem acréscimos:** vencimento 24/12/2024, pago 10/02/2025 só o principal (48 dias de atraso) → multa 6.526,08 (0,33% × 48 dias = 15,84%) e juros 828,12 (SELIC jan/2025 1,01% + 1%) em aberto no SIEF, conforme o art. 61 da Lei 9.430/96.
3. **ICMS-ST 08/2026 não recolhido** apesar do saldo credor de ICMS próprio (o saldo credor não quita ST) → "EM ABERTO" na conta corrente PR e ausente na relação de GR-PR.
4. **Saldo credor de ICMS crescente** (402 mil em 03/2026 → 480 mil em 07/2026 → 365 mil em 08/2026) — monitorar acúmulo/transferência.
5. **Omissão** da EFD-Contribuições de 06/2026 na Situação Fiscal.
6. **Inscrição em dívida ativa não negociada** (90 3 24 002201-55, IPI) — impede certidão; as demais estão na transação.
7. **CAPAG C**: capacidade estimada R$ 400.000,00 frente a passivo PGFN de R$ 512.791,83 (0,78x → classe C pelas faixas A ≥ 2x; B ≥ 1x; C ≥ 0,5x; D < 0,5x). Essas faixas **não estão no texto** da Portaria PGFN 6.757/2022 (conferido em 28/10/2026): são o padrão dos parâmetros do escritório. A classe é calculada pelo gerador a partir desses valores.
8. **ECD 2024 substituta** — o sistema deve preferir a substituta à original do mesmo ano.
9. **Multa de ofício (R$ 18.450,00) na DRE 2025** — adição no e-Lalur/e-Lacs.
10. **Dívida ativa estadual parcelada** (CDA 2024.0012345-8, 12/36 pagas, em dia).
11. **CDA ajuizada de 2023** (2023.0045678-2, R$ 315.000,00, classe C): a que mais pesa na classe do devedor; quitá-la leva o devedor de B para A.
12. **Nenhum crédito irrecuperável na PGFN** (inscrições de 2022 a 2024, sem suspensão judicial, CNPJ ativo).

## Limitações — o que validar contra arquivos reais

- **SPED (.txt):** estrutura, campos e contagens seguem os leiautes, mas o arquivo não foi passado no PVA/validador oficial.
  - **Plano referencial (I051, K156/K356, L100/L300):** usa códigos **oficiais** da tabela dinâmica L100A/L300A de 2021 (lidos de `backend/src/main/resources/plano-referencial`). Para 2022+ eles dependem da tabela oficial do ano.
  - **ECF:** linhas lidas pelo sistema com os códigos oficiais — M300/M350 (2, 93, 168, 169, 171, 173, 175), M010 (código RFB 1000/1003), N500 (1), N630 (1, 3, 4, 24, 26), N650 (1), N670 (1, 4, 19, 21), P200 (26), P300 (1, 3, 4, 15), P400 (21), P500 (1, 4, 13). **Ainda ilustrativos:** a linha de adição da multa (M300/M350 código 30), as linhas de retenção (P300 código 10, P500 código 8) e as demais linhas da base presumida (P200/P400).
  - O código de receita estadual do ICMS-ST (1023) é ilustrativo.
- **DCTF `.dec`:** o leiaute é **aproximado** (registros R01/R10/R11/R99), suficiente para testar leitura de largura fixa, mas não é o leiaute do PGD. Valide o parser com um .dec real.
- **PDFs:** imitam a organização das informações, não o visual exato dos sistemas. A extração deve ser testada também com PDFs reais (a ordem do texto extraído costuma variar).
- **RTI:** o conteúdo foi presumido (resumo mensal de débitos/créditos/saldo credor e ICMS-ST retido x recolhido).
- MVA de 50%, alíquotas de RAT/terceiros e percentuais de multa/juros são simplificações para teste.

## Histórico de correções

- **16/10/2026:**
  - **CAPAG:** a capacidade estimada (R$ 1.186.400,00) dava 2,3x o passivo, o que é classe A, contradizendo a classe C. Passou a R$ 400.000,00, e a classe é calculada pela regra da Portaria.
  - **Transação PGFN:** "Pagas 33 de 60" não batia com a contabilidade, que mostra 48 parcelas de R$ 8.000,00 de 2022 a 2025 + 9 em 2026, com saldo de R$ 52.000,00. O número passa a ser calculado (57 de 64, a última parcela de R$ 4.000,00), e o número da transação passou para 2021.009871-4, porque os pagamentos começam em jan/2022.
  - **Plano referencial e códigos da ECF:** alinhados às tabelas oficiais e às amostras do backend (ver Limitações).
  - Os valores esperados (`_gerador/valores_esperados.json`) não mudaram.

- **27/10/2026:** acréscimos do IPI 11/2024 corrigidos. A massa aplicava multa fixa de 20% e juros de 2,08%. Pela lei, com 48 dias de atraso, a multa é 15,84% (R$ 6.526,08) e os juros 2,01% (R$ 828,12), os mesmos valores que o componente `AcrescimosMoratorios` calcula.

- **28/10/2026 (Fase 4):** exercício de 2025 completo (EFD dos 12 meses coerentes com a ECD 2025), EFD ICMS/IPI de 01 a 07/2026, dados das inscrições da PGFN e da dívida ativa do PR, situação cadastral, classificação no RTI e os valores esperados de carga, DFC direta e classificação do PR. Os arquivos que já existiam não mudaram de conteúdo (só a data de criação dos PDFs).
