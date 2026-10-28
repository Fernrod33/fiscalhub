# FiscalHub — Backend

API REST do FiscalHub: ferramenta para advogados e contadores consultores tributários, organizados em **escritórios** que atendem vários **clientes** (empresas). Todos os dados fiscais entram por **importação de arquivo**; nenhum valor fiscal é digitado.

Repositório: [Fernrod33/fiscalhub](https://github.com/Fernrod33/fiscalhub) (backend em `backend/`, frontend React em `frontend/`).

## Stack

- Java 25, Spring Boot 3.5.6 (Web, Data JPA, Security, Validation, AOP)
- PostgreSQL + Flyway (migrações versionadas)
- JWT (JJWT 0.11) em cookie `httpOnly`, com proteção CSRF
- Apache POI (só `poi-ooxml`, leitura em stream) para balancete em `.xlsx`
- AWS SDK v2 (S3) para storage em produção
- Testes: JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL 18 e MinIO)

## Pré-requisitos

- JDK 25 e Maven 3.9+
- PostgreSQL 15+ (desenvolvimento local)
- Docker em execução para `mvn test` (Testcontainers sobe PostgreSQL e MinIO)

## Modelo: escritório, papéis e clientes

- **Escritório**: nome e CNPJ ou CPF (opcional, validado). O `POST /api/auth/signup` cria o escritório e o primeiro usuário como `ADMIN`, na mesma transação.
- **Usuário** pertence a um escritório e tem um papel:

| Papel | Pode |
|---|---|
| `ADMIN` | tudo, inclusive gerenciar os usuários do escritório e consultar a auditoria |
| `CONSULTOR` | tudo o que envolve clientes (cadastrar, alterar, excluir, simular), sem gerenciar usuários |
| `ASSISTENTE` | ler e importar arquivos; não altera nem exclui nada, não mapeia planilha nem salva de-para (reprocessam e mudam números) e não gerencia usuários |

- **Cliente** pertence ao escritório (qualquer membro o enxerga); quem cadastrou fica em `criado_por`. CNPJ é único **por escritório**.
- Documentos, débitos e simulações herdam o escopo pelo cliente. Recurso de outro escritório responde **404**; papel sem permissão responde **403**.
- Saída de usuário é **desativação** (login bloqueado; nada fica órfão). O último `ADMIN` ativo não sai nem é rebaixado.
- O JWT carrega `uid`, `escritorioId`, `papel` e `tv` (versão do token). A cada requisição o filtro lê o usuário pela chave primária e confere se ele segue ativo e com a mesma versão. Troca de senha, desativação e mudança de papel incrementam a versão e invalidam na hora os tokens anteriores.
- **Senha provisória**: enquanto `trocarSenhaNoProximoLogin` for verdadeiro, toda rota autenticada responde 403 com `type` `/problemas/senha-troca-obrigatoria`, exceto `GET /api/usuarios/me` e `PUT /api/usuarios/me/senha`. A troca emite um cookie de sessão novo.

## Configuração (variáveis de ambiente)

Copie `backend/.env.example` para `backend/.env` e preencha. O `.env` é lido na inicialização (`spring.config.import`) e não é versionado; variáveis de ambiente reais têm precedência.

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_PASSWORD` | sim | — | senha do PostgreSQL |
| `COOKIE_SECURE` | não | `true` | flag `Secure` do cookie da sessão; o perfil `dev` desliga (http local) |
| `JWT_SECRET` | sim | — | segredo HMAC do JWT, mínimo 32 bytes (`openssl rand -base64 48`). O app não sobe sem ele ou com menos de 256 bits |
| `DB_URL` | não | `jdbc:postgresql://localhost:5432/fiscalhub` | URL JDBC |
| `DB_USERNAME` | não | `postgres` | usuário do banco |
| `CORS_ALLOWED_ORIGINS` | não | `http://localhost:5173` | origens permitidas, separadas por vírgula |
| `UPLOAD_MAX_SIZE` | não | `500MB` | limite único do upload (arquivo e requisição); acima dele, 413 |
| `STORAGE_TIPO` | não | `local` | `local` (disco) ou `s3` (S3/MinIO) |
| `STORAGE_LOCAL_DIRETORIO` | não | `./uploads/documentos` | raiz do storage local |
| `S3_ENDPOINT` | não | — | só para MinIO/compatíveis (ex.: `http://localhost:9000`); vazio = AWS |
| `S3_REGIAO` | não | `us-east-1` | região |
| `S3_BUCKET` | não | `fiscalhub-documentos` | bucket (precisa existir) |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | não | — | vazias = cadeia padrão de credenciais da AWS |
| `SELIC_AGENDAMENTO` | não | `true` | importação automática da SELIC do Banco Central |
| `FORWARD_HEADERS_STRATEGY` | não | `none` | atrás de proxy ou load balancer use `native` ou `framework`, para que a auditoria grave o IP real do `X-Forwarded-For`. Só ative se o proxy for confiável: senão o cliente forja o IP |
| `SPRING_PROFILES_ACTIVE` | não | — | `dev` para desenvolvimento (ver Perfis) |

## Perfis

| Perfil | Arquivo | Efeito |
|---|---|---|
| padrão | `application.properties` | log `INFO`, sem SQL no log, sem DEBUG do Spring Security |
| `dev` | `application-dev.properties` | `show-sql`, `format_sql`, log `DEBUG`; aplica também `db/dev` (seed **aproximado** da UFPR) |
| `test` | `src/test/resources/application-test.properties` | testes de integração; banco do Testcontainers; sem importação da SELIC |

Nenhum perfil cria usuário: cadastre-se por `POST /api/auth/signup`.

**Um banco por ambiente.** O perfil `dev` registra no histórico do Flyway a migração repetível de `db/dev` (seed da UFPR). Nunca reutilize o banco de dev sem o perfil `dev`: o Flyway recusa a subida por encontrar uma migração aplicada que não existe nas pastas do outro perfil.

**Contrato OpenAPI:** no perfil `dev`, `GET /v3/api-docs` devolve o contrato da API, usado pelo frontend para gerar os tipos (`npm run gerar:api`). Nos outros perfis o endpoint não existe.

## Banco de dados (Flyway)

O schema é versionado em `src/main/resources/db/migration` e aplicado pelo Flyway na inicialização. O Hibernate só valida (`ddl-auto=validate`); qualquer mudança de schema entra como nova migração `V<n>__descricao.sql`.

| Migração | Conteúdo e efeito sobre dados existentes |
|---|---|
| `V1__baseline.sql` | schema base (usuário, empresa, declaração, simulação, parcela, índice) |
| `V2__create_documentos_fiscais.sql` | documentos fiscais importados e seus tributos |
| `V3__escritorio_e_papeis.sql` | cria `escritorio`; cada usuário existente ganha um escritório próprio e vira `ADMIN` |
| `V4__cliente.sql` | `empresa` vira `cliente`, com `escritorio_id` do dono atual e `criado_por`; CNPJ único por escritório |
| `V5__debito_e_simulacao.sql` | cria `debito` e `simulacao_debito`; **apaga** as simulações antigas e a tabela `declaracao` |
| `V6__storage_e_jobs.sql` | `chave_armazenamento` (a chave dos arquivos antigos = nome interno), `sha256` único por cliente, `job_processamento` |
| `V7__auditoria.sql` | `auditoria_evento`, com trigger que impede `UPDATE` e `DELETE` |
| `V8__indice_origem.sql` | origem e data da coleta dos índices; os antigos ficam como `LEGADO` |
| `V9__versao_token_e_limpeza.sql` | `versao_token` no usuário (todos começam em 0); remove `documento_original_id` e `nome_arquivo_interno` |
| `V10__remove_cnpj_unico_global.sql` | remove o `UNIQUE (cnpj)` global que o antigo `ddl-auto` criou (vale o por escritório) |
| `V11__pipeline_de_importacao.sql` | documento com `escritorio_id` próprio e cliente/período opcionais (vêm do arquivo); duplicado por escritório; etapa e progresso no job; `inconsistencia_importacao` |
| `V12__escrituracao_contabil.sql` | escrituração, plano de contas, saldos (com registro e linha), demonstrações e composição por conta |
| `V13__plano_referencial.sql` | tabelas oficiais do plano referencial (carregadas pela `ImportacaoPlanoReferencial`) |
| `V14__balancete_planilha.sql` | mapeamento de colunas por cliente e de-para referencial |
| `V15__remove_checks_de_enum_do_ddl_auto.sql` | remove os `CHECK` de enum que o antigo `ddl-auto` criou (recusavam valores novos) |
| `V16__remove_campos_legados_do_documento.sql` | remove `esfera`, `juridicao`, `retificacao` e `documento_fiscal_tributos`; tipos com nome de tributo viram `OUTRO`, `DCTF_WEB` vira `DCTFWEB` |
| `V17__linha_demonstracao_derivada.sql` | `linha_demonstracao.derivada` (linha calculada pelo sistema, ex.: resultado do período não encerrado) |
| `V18__apuracao_tributos.sql` | modelo único de apuração: `apuracao_tributo`, `detalhe_apuracao`, `obrigacao_recolher`, `saldo_fiscal`; CNPJ do estabelecimento na escrituração (preenchido com o do cliente nas existentes) |
| `V19__hierarquia_herdada.sql` | `linha_demonstracao.hierarquia_herdada_de` (código do I051 ausente na tabela do exercício, posicionado pela de um ano anterior) |
| `V20__situacao_fiscal.sql` | `debito` ampliado (escrituração, chave, fontes, encargo, total, origem por página e linha; vencimento e principal passam a aceitar nulo), `parcelamento`, `pendencia_fiscal`, `capacidade_pagamento_oficial`; `escrituracao_contabil.origem` e `versao_leiaute` mais largos |
| `V21__declarado_e_pago.sql` | `debito_declarado` e `pagamento` |

**Banco do zero:** `CREATE DATABASE fiscalhub;` e suba o app; o Flyway cria tudo a partir da V1.

**Banco de desenvolvimento criado antes do Flyway** (pelo antigo `ddl-auto=update`): `spring.flyway.baseline-on-migrate=true` marca a V1 como aplicada e executa da V2 em diante.

## Importação: upload, pipeline e processadores

Dados fiscais e contábeis só entram por arquivo; nenhum valor é digitado. Todo número gravado aponta para a origem (arquivo, registro e linha).

**Upload**
- Pela central do escritório (`POST /api/documentos`, sem cliente: o cliente sai do CNPJ do arquivo) ou dentro de um cliente (`POST /api/clientes/{id}/documentos`: o CNPJ do arquivo tem que ser o dele, senão é erro claro).
- O arquivo vai direto para o storage por stream, com **SHA-256** no caminho. O mesmo arquivo de novo no escritório responde **409** com `recursoExistenteId`.
- Extensões aceitas: `.txt`, `.pdf`, `.dec`, `.csv`, `.xlsx`. O tipo informado (`documento.tipo`, opcional) é só sugestão: vale o detectado no conteúdo, e a divergência vira alerta.
- **Storage**: `ArmazenamentoArquivos` (`ArmazenamentoLocal` em dev, `ArmazenamentoS3` para S3/MinIO), por `fiscalhub.storage.tipo`.

**Tipos reconhecidos** (`DetectorTipoDocumento`, pelo conteúdo)

| Conteúdo | Tipo | Leitura |
|---|---|---|
| primeira linha `\|0000\|LECD\|` | `ECD` | `ProcessadorEcd` |
| `\|0000\|LECF\|` | `ECF` | `ProcessadorFiscal` (`LeituraEcf`) |
| `\|0000\|COD_VER\|` com as datas nos campos 6 e 7 (`TIPO_ESCRIT\|IND_SIT_ESP\|NUM_REC_ANTERIOR\|DT_INI\|DT_FIN`) | `EFD_CONTRIBUICOES` | `ProcessadorFiscal` (`LeituraEfdContribuicoes`) |
| `\|0000\|COD_VER\|` com as datas nos campos 4 e 5 (`COD_FIN\|DT_INI\|DT_FIN`) | `EFD_ICMS_IPI` | `ProcessadorFiscal` (`LeituraEfdIcmsIpi`) |
| zip OOXML com extensão `.xlsx`, ou `.csv` | `BALANCETE` | `ProcessadorPlanilha` |
| `%PDF-` | `PDF` até o adaptador reconhecer o layout (vira `RELATORIO_SITUACAO_FISCAL`, `DARF`...) | `ProcessadorSituacao` (ver abaixo) |
| `DCTFM` + ano + CNPJ | `DCTF` (`.dec`) | `ProcessadorSituacao` (`LeitorDctfDec`) |
| SPED não reconhecido, outro | `OUTRO` | guardado, com alerta (`ProcessadorSemLeitura`) |

**Pipeline** (`importacao/ProcessamentoDocumentoService`)
- Um registro em `job_processamento` é despachado depois do commit ao pool do Spring Boot. Na subida, jobs presos voltam para a fila.
- Etapas no job, expostas em `GET /api/documentos/{id}/status` com o percentual (bytes lidos ÷ tamanho): `RECEBIDO → LENDO → VALIDANDO → GRAVANDO → CONCLUIDO`. Etapa e progresso são gravados em transação própria (`RegistroProgresso`), para a tela acompanhar.
- Status do documento: `RECEBIDO`, `PROCESSANDO`, `PROCESSADO`, `PROCESSADO_COM_ALERTAS` (há alerta ou erro não fatal), `ERRO`, `AGUARDANDO_CLIENTE` (CNPJ sem cliente no escritório: sugestão de cadastro com CNPJ e nome do arquivo), `AGUARDANDO_MAPEAMENTO` (planilha sem mapeamento ou período), `LAYOUT_NAO_RECONHECIDO` (PDF que nenhum adaptador reconhece).
- **Uma transação por documento**: erro fatal (`FalhaImportacao`) desfaz tudo o que o processador gravou e marca `ERRO`. As inconsistências (`ERRO`, `ALERTA`, `INFO`, com registro, linha e código da regra; até 1.000 por documento) são gravadas no fim, em `inconsistencia_importacao`.
- **Reprocessar** (`POST /api/documentos/{id}/reprocessar`, ADMIN e CONSULTOR) roda de novo a partir do arquivo no storage; o processador apaga o que a execução anterior gerou.

**Como adicionar um tipo SPED novo**
1. Baixe o manual oficial (Word ou PDF) no portal do SPED e gere o leiaute: acrescente o tipo, os registros e a fonte em `scripts/sped/gerar_layout_sped.py` e rode-o (`resources/sped/<tipo>-leiautes.properties`). Nenhum índice de campo no código.
2. Ensine o `DetectorTipoDocumento` a reconhecer o `0000` (assinatura em `ASSINATURAS_SPED` ou regra de estrutura, como a das EFD) e acrescente o valor em `DocumentoTipo`.
3. Escreva a leitura estendendo `sped/LeituraSped`: declare os registros de interesse (os demais são pulados sem virar campos e entram na contagem do 9900), diga onde está a versão (`versao`), leia o 0000 (`abertura` → `abrirEscrituracao(cnpj, nome, início, fim, substituta, plano)`), trate os registros (`tratar`) e, se precisar, `validar` e `gravar`. A base já faz o progresso, a contagem 9900/9999, o conteúdo após o 9999, o cliente pelo CNPJ (ou pela raiz, para filial), a escrituração e a retificação.
4. Grave no modelo existente: apurações em `RepositorioApuracao` (com a origem de cada valor), dados contábeis em `RepositorioContabil`. Ligue a leitura num `ProcessadorDocumento` (`ProcessadorFiscal` para escriturações fiscais).
5. Gere amostras sintéticas pelo leiaute (`scripts/sped/gerar_amostras_fiscais.py`) e cubra o tipo no teste de integração.

Tipo que não é SPED: implemente `ProcessadorDocumento` como `@Component` (`suporta`, `processar`) e use o `ContextoProcessamento` (`abrir()`, `etapa`, `lidos`, `registrar`, `cliente`, `clienteObrigatorio`). Erro fatal: `FalhaImportacao`; decisão pendente: `AguardandoUsuario`. Apague no começo o que um processamento anterior do mesmo documento gerou (`Escrituracoes.apagarDoDocumento`).

**Convenção de sinal (única, em banco, API e telas)**: devedor positivo, credor negativo (`sped/Saldos`). Ativo e despesas costumam ser positivos; passivo, PL e receitas, negativos. As telas mostram o valor absoluto com o indicador D/C.

## ECD (SPED Contábil)

**Leiautes cobertos** (`I010.COD_VER_LC`): `7.00` (ano-calendário 2018), `8.00` (2019) e `9.00` (2020 em diante). Outra versão vira erro claro ("leiaute não coberto").

**Fonte dos campos**: `src/main/resources/sped/ecd-leiautes.properties`, gerado por `scripts/sped/gerar_layout_sped.py ecd` a partir dos *Manuais de Orientação do Leiaute da ECD* em Word, publicados em http://sped.rfb.gov.br/pasta/show/1569 (leiaute 9, atualização de maio/2026, `arquivo/download/7989`; leiaute 8, `4217`; leiaute 7, `2855`). Nenhum índice de campo está espalhado pelo código. Diferenças relevantes entre as versões: no leiaute 7 o `0000` tem 20 campos (sem `COD_PLAN_REF`), o `I051` traz o plano no campo 2 e o `J150` tem um valor só.

**Registros lidos**

| Registro | Uso |
|---|---|
| `0000` | período, nome, CNPJ, UF, finalidade (original ou substituta), plano referencial |
| `I010` | forma de escrituração e versão do leiaute |
| `I050` / `I051` | plano de contas e mapeamento para o plano referencial |
| `I150` / `I155` | saldos periódicos (Balanço) |
| `I350` / `I355` | resultado antes do encerramento (DRE; soma as apurações do ano) |
| `J005` / `J100` / `J150` | Balanço e DRE publicados (só `ID_DEM` = 1, demonstrações individuais) |
| `9900` / `9999` | contagem de registros e de linhas |

`I200`/`I250` e os demais são pulados olhando só o código do registro. Conteúdo depois do `9999` (assinatura do PVA) é ignorado com alerta.

**Leitura**: ISO-8859-1, linha a linha, em stream (limite de 128 KB por linha). Contas e saldos são gravados em lote (JDBC batch) durante a leitura. Desempenho medido (`DesempenhoEcdTest`, opcional): ECD sintética de **314,6 MB / 6,04 milhões de linhas em 4,7 s, pico de heap 175 MB**.

**Validações** (inconsistências com registro e linha): contagem do `9900` e do `9999`; débitos = créditos por período (`I155`); saldo inicial + débitos − créditos = saldo final; balanço fecha nos saldos (ativo + passivo + PL + resultado aberto = 0) e no `J100`; conta analítica sem `I051` (alerta com a lista); conta fora do plano.

**Retificação**: uma ECD substituta (`IND_FIN_ESC` = 1) do mesmo cliente e período marca a anterior como `SUBSTITUIDA` (histórico preservado) e passa a valer. A regra é a mesma para todos os tipos (`importacao/Escrituracoes.retificar`).

**Conferência com a ECF**: havendo ECF vigente do mesmo exercício, a ECD é conferida com ela (ver *Escriturações fiscais*).

## Escriturações fiscais (ECF, EFD-Contribuições, EFD ICMS/IPI)

Leiautes gerados por `scripts/sped/gerar_layout_sped.py` a partir dos manuais oficiais baixados do portal do SPED em 2026-10-15 (fonte e data no cabeçalho de cada `.properties`):

| Tipo | Versões (`0000.COD_VER`) | Fonte |
|---|---|---|
| ECF | `0008` a `0012` (anos-calendário 2021 a 2025) | Manuais de Orientação do Leiaute 8 a 12 (Word), http://sped.rfb.gov.br/item/show/1644 (`arquivo/download/5971`, `7145`, `7306`, `7626`, `8004`) |
| EFD-Contribuições | `005` (2019) e `006` (2020 em diante) | Guia Prático 1.35 (Word, o mais recente publicado), http://sped.rfb.gov.br/item/show/1989 (`arquivo/download/5837`) |
| EFD ICMS/IPI | `016` a `020` (2022 a 2026) | Guia Prático 3.2.2, fev/2026 (PDF), http://sped.rfb.gov.br/item/show/1573 (`arquivo/download/8112`) |

Os registros lidos têm os mesmos campos em todas as versões cobertas de cada tipo. Os códigos das linhas da ECF (base, alíquota, adicional, a pagar; Parte B de prejuízo fiscal e base negativa) vêm das tabelas dinâmicas oficiais ("Tabelas Dinâmicas e Planos de Contas Referenciais", leiautes 8 a 12, iguais em todos).

**Registros lidos** (todo o resto, inclusive os blocos de documentos A, C, D, F, G, H e K, é pulado sem virar campos)

| Tipo | Registros | Grava |
|---|---|---|
| ECF | `0010`; `L030`/`L300` e `P030`/`P150` (DRE declarada); `M010`, `M030`, `M300`, `M350`, `M500` (e-Lalur/e-Lacs); `N030`, `N500`, `N620`, `N630`, `N650`, `N660`, `N670` (Lucro Real); `P200` a `P500` (Lucro Presumido) | IRPJ e CSLL por período (estimativas mensais, ajuste anual ou trimestres), com a demonstração do lucro no detalhe; prejuízo fiscal e base negativa (Parte B, códigos 1000–1004) |
| EFD-Contribuições | `0110`, `M100`, `M200`, `M210`, `M500`, `M600`, `M610`, `1100`, `1500` | PIS e COFINS mensais por parte do regime (não cumulativo, cumulativo), créditos no detalhe, saldos de crédito |
| EFD ICMS/IPI | `E100`/`E110`/`E111`/`E116`, `E200`/`E210`/`E250`, `E300`/`E310`/`E316`, `E500`/`E520`/`E530` | ICMS próprio, ICMS-ST e DIFAL/FCP por UF e IPI, com ajustes no detalhe, obrigações a recolher (código de receita, vencimento, valor) e saldos credores |

**Validações** (inconsistências com registro e linha): contagem `9900`/`9999` (inclusive dos registros pulados); ECF: base do IRPJ e da CSLL (`N500`/`N650`) × e-Lalur/e-Lacs pela fórmula oficial (`M300`/`M350` linhas 175 e 349); EFD-Contribuições: campos 05, 08, 12 e 13 do `M200`/`M600` e consolidação × `M210`/`M610` e créditos; EFD ICMS/IPI: campos de saldo do `E110`, `E210`, `E310` e `E520` como no guia, ajustes `E111`/`E530` × totais e soma das obrigações = a recolher + extra-apuração.

**Conferência ECD × ECF** (`fiscal/ConferenciaEcdEcf`, no processamento de quem chegar por último): receita bruta (`3.01.01.01.01`) e resultado antes do IRPJ e da CSLL (`3.01`) da DRE referencial da ECD × DRE declarada da ECF (mesmos códigos do L300A/P150A); sem DRE referencial na ECD, o resultado líquido (soma do `I355` × linha `3` da ECF). Diferença vira alerta `CONFERENCIA_ECD_ECF` com os dois valores e as duas origens.

**Limites conhecidos**: Lucro Arbitrado (bloco T da ECF) não é lido (alerta); a descrição oficial dos códigos de ajuste do E111 (tabela 5.1.1 de cada UF) não está carregada: o detalhe mostra o código e a descrição complementar do arquivo.

**Desempenho** (`DesempenhoEfdTest`, opcional, `@Tag("desempenho")`): EFD ICMS/IPI sintética de **1 GB / 6,9 milhões de linhas em 3,3 s, pico de heap 149 MB com `-Xmx256m`**.

## Documentos dos órgãos: PDF e .dec (Fase 2b)

Leitura em duas camadas (`pdf/`), sem OCR e sem IA:

1. **`LeitorPdf`** (Apache PDFBox 3.0.8, a única dependência para PDF): texto por página e por linha, cada linha
   dividida em células pela posição horizontal; texto rotacionado é descartado (some a marca d'água diagonal);
   normalizadores pt-BR de valor (`1.234,56`, `R$`, `(...)`, `D/C`), data, CNPJ, CPF e competência. O resultado é um
   `DocumentoPdf` com a navegação comum: seção pelo título, tabela (um registro é um **bloco** de linhas a até 1,45 ×
   a fonte uma da outra: célula quebrada e centralização vertical) e campo "rótulo | valor".
2. **Um adaptador por tipo de documento e versão de layout** (`AdaptadorDocumentoPdf`: `tipo`, `versao`,
   `reconhece(pdf)`, `extrair(pdf, extracao)`). O `ProcessadorSituacao` tenta todos: exatamente um reconhece → ele lê;
   nenhum ou mais de um → status `LAYOUT_NAO_RECONHECIDO` com alerta (nunca chute). O documento fica guardado e é lido
   ao reprocessar, quando houver o adaptador.

PDF não confiável: 20 MB e 300 páginas no máximo, PDF com senha recusado (`PDF_CRIPTOGRAFADO`), arquivo inválido
(`PDF_INVALIDO`), cache de streams em arquivo temporário (não em memória). PDF sem texto (scan, foto) é erro
`PDF_SEM_TEXTO` com a orientação "gere o PDF pelo próprio sistema; scan e foto não são aceitos". A extração de texto
não executa JavaScript, não abre anexos nem busca recursos externos.

**Tipos e versões de layout cobertos**

| Documento | `DocumentoTipo` | Adaptador | Versão | Grava |
|---|---|---|---|---|
| Relatório de Situação Fiscal (e-CAC, RFB + PGFN) | `RELATORIO_SITUACAO_FISCAL` | `AdaptadorRelatorioSituacaoFiscal` | `ficticio-2026-10` | débitos do SIEF, omissões, inscrições do SIDA e negociadas, processo suspenso |
| Extrato de inscrições (Regularize) | `EXTRATO_REGULARIZE` | `AdaptadorRegularize` | `ficticio-2026-10` | inscrições (principal, multa, juros, encargo, situação, ajuizamento), transação |
| CAPAG (Regularize) | `CAPAG` | `AdaptadorCapag` | `ficticio-2026-10` | capacidade oficial (classe conferida pela Portaria PGFN 6.757/2022) |
| DCTF Mensal (impressão) | `DCTF` | `AdaptadorDctfPdf` | `ficticio-2026-10` | declarado e vinculações |
| DCTF Mensal (`.dec`) | `DCTF` | `LeitorDctfDec` + `resources/dctf/dec-ficticio-2026-10.properties` | `ficticio-2026-10` (layout **aproximado**) | declarado e vinculações |
| DCTFWeb (declaração completa) | `DCTFWEB` | `AdaptadorDctfWeb` | `ficticio-2026-10` | declarado (previdenciário, terceiros, IRRF, MIT) |
| DARF pago | `DARF` | `AdaptadorDarf` | `ficticio-2026-10` | pagamento por código |
| Relatório consolidado de pagamentos (e-CAC) | `RELATORIO_PAGAMENTOS` | `AdaptadorRelatorioPagamentos` | `ficticio-2026-10` | pagamentos; parcela de transação vai para a transação |
| Conta corrente e dívida ativa do ICMS (PR) | `CONTA_CORRENTE_PR` | `AdaptadorContaCorrentePr` | `ficticio-2026-10` | declarado do ICMS por mês, débito em aberto (SEFA-PR), CDA (PGE-PR), parcelamento |
| RTI (PR) | `RTI` | `AdaptadorRti` | `ficticio-2026-10` (conteúdo **presumido**) | saldos credores mensais de ICMS; capacidade oficial só se o documento trouxer |
| GR-PR paga | `GR_PR` | `AdaptadorGrPr` | `ficticio-2026-10` | pagamento; parcela de parcelamento vai para o parcelamento |
| Relação de pagamentos GR-PR | `RELACAO_PAGAMENTOS_GR_PR` | `AdaptadorRelacaoGrPr` | `ficticio-2026-10` | pagamentos (o cliente vem do CAD/ICMS) |

> **Atenção:** os adaptadores `ficticio-2026-10` leem a massa fictícia (`src/test/resources/massa-ficticia`), que
> imita o **conteúdo**, não o visual dos sistemas, e só a reconhecem pela faixa "DOCUMENTO FICTÍCIO PARA TESTE DE
> SOFTWARE (FiscalHub)". Um PDF real cai em `LAYOUT_NAO_RECONHECIDO` até ganhar o seu adaptador. O `.dec` usa um
> layout aproximado, não o do PGD DCTF.

**Como adicionar um adaptador de PDF** (PDF real ou nova versão de layout)
1. Leia o PDF com o `LeitorPdf` e veja as linhas e células (um teste que imprime `pdf.linhas()` basta).
2. Crie `situacao/adaptadores/Adaptador<Documento><Versao>` como `@Component` implementando `AdaptadorDocumentoPdf`
   (ou estendendo uma base da versão, como `AdaptadorFicticio`). `reconhece`: trechos que identificam o documento **e**
   a versão do layout (dois adaptadores não podem reconhecer o mesmo PDF). `extrair`: use `pdf.campo(rótulo)`,
   `pdf.secao(título, títulos)` e `DocumentoPdf.tabela(linhas, chave)`; normalize com `LeitorPdf.valor/data/cnpj`;
   preencha a `Extracao` com a empresa (CNPJ ou inscrição estadual), o período e os itens, cada um com a
   `Origem` (página, linha, trecho). Não grave nada: o `RepositorioSituacao` grava.
3. Não mexa nos adaptadores existentes. Teste o novo com o PDF (anonimizado) em `AdaptadoresTest` ou num teste próprio.
4. Código de receita novo: uma linha em `resources/receitas/codigos-receita.txt`, com a fonte.

**`.dec` da DCTF**: largura fixa pelo layout versionado em `resources/dctf/` (registro → campo:início:tamanho). Para o
layout do PGD, crie outro arquivo de layout e o reconhecimento dele no `LeitorDctfDec`, sem mexer no atual.

## Débitos, declarado, pago e conciliação

Tudo pende da escrituração do documento (`escrituracao_contabil`, origem = tipo do documento, `versao_leiaute` = versão
do adaptador): reprocessar ou excluir o documento apaga o que ele gerou. Cada linha leva a origem (página, linha e
trecho; no `.dec`, registro e linha).

- **`debito`** (a entidade existente, ampliada): órgão (`RFB`, `PGFN`, `SEFA_PR`, `PGE_PR`), tributo e código de
  receita, competência ou período, vencimento, principal, multa, juros, encargo, total do documento, situação
  (`EM_ABERTO`, `INSCRITO_DIVIDA_ATIVA`, `PARCELADO`, `SUSPENSO`, `NEGOCIADO`), inscrição/CDA/processo, ajuizamento,
  negociação. Impede certidão: `EM_ABERTO` e `INSCRITO_DIVIDA_ATIVA`.
- **`parcelamento`**, **`pendencia_fiscal`**, **`capacidade_pagamento_oficial`** (insumo da Fase 4),
  **`debito_declarado`** (com as vinculações: pago, compensado, suspenso, parcelado) e **`pagamento`** (DARF, DAS,
  GR-PR; parcela de parcelamento ou transação aponta para ele).
- **Fontes juntas na consulta** (`SituacaoFiscalService`): relatórios que são retrato (situação fiscal, Regularize,
  CAPAG, conta corrente, RTI) valem só pelo mais recente de cada tipo; a mesma dívida em tipos diferentes (chave:
  inscrição, ou órgão + tributo + competência) é um débito só, com as origens de todos e os valores do documento mais
  recente que os traz; valor diferente entre fontes marca o débito como `divergente` e gera alerta na importação. O
  mesmo pagamento (DARF pelo número; GR-PR por receita, referência e data) é um só, com os itens do próprio documento de
  arrecadação. Declarações: a retificadora substitui a original do mesmo período (histórico mantido); a mesma DCTF em
  PDF e `.dec` é conferida uma contra a outra.
- **Simulação**: os débitos importados entram em `/api/simulacoes`; inscrição sem vencimento ou principal é recusada
  com mensagem clara (o cálculo da PGFN é da Fase 5).

**Tabela de códigos de receita** (`resources/receitas/codigos-receita.txt`): código (sem a variação) → esfera, órgão e
tributo, ou `PARCELAMENTO`; fonte e data da conferência por linha (cabeçalho do arquivo) e a coluna `ilustrativo`. Conferidos em 28/10/2026: 1082,
1138 e 1646 (DCTFWeb, material da RFB) e 1015 (página oficial da GR-PR, 101-5). Ilustrativos (`ilustrativo=true`, só
valem para a massa e aparecem marcados no detalhe da conciliação): os de terceiros da DCTFWeb (1170, 1176, 1181, 1184,
1191), 1023 e 2011 do PR (na tabela oficial, 102-3 é regime especial e 201-1 é IPVA). Os `RFB-SICALC` são de uso
corrente, não reconferidos online. Código fora da tabela gera `CODIGO_RECEITA_DESCONHECIDO` e fica fora da conciliação.

**Acréscimos moratórios** (`AcrescimosMoratorios`, componente único da regra federal, Lei 9.430/96, art. 61): multa de
0,33% ao dia, do dia seguinte ao vencimento ao do pagamento, limitada a 20%; juros pela **soma** da SELIC mensal do mês
seguinte ao vencimento ao anterior ao pagamento, mais 1% no mês do pagamento. Sem SELIC carregada é erro (sem
estimativa). As calculadoras de parcelamento da Fase 5 devem usar este componente.

**Conciliação** (`ConciliacaoService`, calculada na consulta): chave cliente + esfera + tributo (+ UF) + período de
apuração; apurado das escriturações fiscais vigentes, declarado da DCTF/DCTFWeb (no ICMS, a conta corrente do PR ou, sem
ela, a própria EFD), pago dos DARF, do relatório e das GR-PR. Status: `CONCILIADO`, `DIVERGENCIA_APURADO_DECLARADO`,
`NAO_DECLARADO`, `PAGO_A_MENOR`, `PAGO_A_MAIOR`, `NAO_PAGO`, `PAGO_EM_ATRASO_SEM_ACRESCIMOS`,
`COMPENSADO_OU_SUSPENSO`, `PARCELADO`; tolerância `fiscalhub.conciliacao.tolerancia` (padrão 0,01). Cada diferença
traz o débito correspondente do relatório de situação fiscal ou da conta corrente do PR; diferença sem débito
(`DIFERENCA_SEM_DEBITO`) e débito em aberto sem diferença (`DEBITO_SEM_DIFERENCA`) viram alerta.

## Modelo de apuração

Um modelo só para todos os tributos e fontes (`fiscal/RepositorioApuracao`):

- `apuracao_tributo`: cliente, documento e escrituração de origem; tributo (`TipoTributo`: IRPJ, CSLL, PIS, COFINS, IPI, ICMS, ICMS_ST, ICMS_DIFAL, ICMS_FCP), esfera e UF; período e periodicidade (MENSAL, TRIMESTRAL, ANUAL); regime; descrição; base de cálculo, valor apurado, deduções e créditos, **valor a recolher**, saldo credor a transportar; e `origem` (JSON campo → registro e linha).
- `detalhe_apuracao`: linhas que compõem o cálculo (grupo, código, descrição, valor, registro, linha).
- `obrigacao_recolher`: obrigações do E116/E250/E316 (vencimento do apurado na conciliação).
- `saldo_fiscal`: saldos que passam de período (prejuízo fiscal, base negativa, créditos de PIS/COFINS, saldos credores), por período, com origem.

Valores positivos, como no arquivo (a convenção D/C vale para os dados contábeis). Tudo pende da escrituração: reprocessar ou excluir o documento apaga o que ele gerou; só escriturações vigentes aparecem na API.

## Demonstrações referenciais

- **Plano referencial oficial** em `src/main/resources/plano-referencial`, com fonte e SHA-256 de cada tabela em `tabelas.txt`: 2018 a 2021 do portal Sped Tabelas (pacotes "Planos Referenciais"); **2022 a 2025 da planilha oficial "Tabelas Dinâmicas e Planos de Contas Referenciais" da ECF de cada ano-calendário** (leiautes 9 a 12, http://sped.rfb.gov.br/pasta/show/1644, com URL, data de atualização e SHA-256 da planilha e do `.txt` convertido por `scripts/sped/converter_tabelas_dinamicas_ecf.py`). **Só a tabela do próprio exercício é usada**: exercício sem tabela carregada gera o alerta `TABELA_REFERENCIAL_AUSENTE` e só a demonstração publicada fica disponível; plano fora dos carregados (instituições financeiras, seguradoras, imunes/isentas) gera `PLANO_REFERENCIAL_NAO_SUPORTADO`. Código do I051 que não existe na tabela do exercício gera `CODIGO_REFERENCIAL_AUSENTE_NO_ANO` e entra na posição que tinha na tabela do ano anterior mais recente em que existe, marcado como hierarquia herdada (selo na tabela e explicação no popover de origem). DRE publicada "plana" (todas as linhas no nível 1, com totalizadoras): o total conferido é a última totalizadora. Planos carregados: `1` PJ em geral, Lucro Real (L100_A balanço, L300_A resultado) e `2` PJ em geral, Lucro Presumido (P100, P150). A `ImportacaoPlanoReferencial` roda na subida e só recarrega a tabela que mudou. Para um ano novo: `scripts/sped/baixar_tabelas_referenciais.py` e uma linha em `tabelas.txt`. Nenhuma conta referencial é digitada no código.
- **Montagem** (`MontagemReferencial`, usada pela ECD e pela planilha): Balanço pelos saldos finais e iniciais do exercício; DRE pelo resultado antes do encerramento. Valores agregados pela hierarquia referencial; cada linha analítica guarda as contas da empresa que a compõem. Vale a tabela do ano mais recente até o exercício.
- **Conferência cruzada**: ativo, passivo + PL e resultado do referencial contra o publicado (`J100`/`J150`); diferença vira alerta.
- **Comparativo** (`GET /api/clientes/{id}/demonstracoes`): um exercício por coluna (a escrituração vigente de cada ano; ECD antes de planilha), AH = (atual ÷ anterior − 1) × 100 e AV (BP sobre o total do grupo; DRE sobre a receita líquida) calculadas no backend.

## Balancete em planilha

- `.xlsx` em stream (POI `XSSFReader` + SAX): fórmulas pelo valor em cache, nunca avaliadas; proteção contra zip bomb (`ZipSecureFile`) e XML sem entidades externas. `.csv` com encoding (UTF-8 ou Windows-1252) e separador (`;`, `,`, tab) detectados. Limites de 100 mil linhas, 200 colunas e 128 KB por linha.
- **Assistente**: o backend devolve as primeiras linhas e um mapeamento sugerido; o usuário aponta as colunas (código, descrição, saldo inicial, débitos, créditos, saldo final, D/C), a linha do cabeçalho, o período e o plano. O mapeamento fica salvo por cliente e é reconhecido pela assinatura do cabeçalho nos próximos uploads.
- **De-para**: as associações salvas do cliente e o `I051` da ECD do mesmo cliente (mesmo código de conta) já vêm aplicados; o usuário associa o resto com busca no plano oficial. Conta sem associação vira alerta e fica fora do referencial.
- Validações: débitos = créditos, saldo inicial + D − C = final, soma dos saldos analíticos igual a zero. Contas sintéticas são deduzidas pelo prefixo do código. A origem de cada valor é a aba e a linha.
- **Antes do encerramento**: no Balanço referencial, o PL ganha a linha derivada "Resultado do período não encerrado" (`2.03.RNE`, `derivada = true`), composta pelas contas de resultado do próprio balancete (as mapeadas na DRE); o balanço fecha e a origem lista essas contas.
- Mapear colunas e salvar o de-para são de `ADMIN` e `CONSULTOR` e ficam na auditoria (o de-para com a quantidade de contas alteradas).

## Índices: SELIC oficial

A SELIC mensal vem do Banco Central (API SGS, **série 4390**, "Taxa de juros - Selic acumulada no mês", % a.m.). A importação roda na subida e diariamente (`fiscalhub.selic.cron`, padrão 6h). Ela traz só meses fechados (a série inclui o mês corrente parcial), não duplica e corrige valores aproximados anteriores, gravando a origem (`BCB_SGS_4390`) e a data da coleta. Sem SELIC para um mês do período, a simulação responde 400 ("Índice SELIC ausente para AAAA-MM"); não há estimativa.

A UPF/UFPR do Paraná ainda não tem fonte oficial automatizada (Fase 5). Em `dev` há um seed aproximado.

## Auditoria

Toda gravação passa pela anotação `@Auditavel` e pelo `AuditoriaAspect`. São auditados:
- login (sucesso e falha);
- importação, download e exclusão de documento;
- mapeamento de colunas e de-para da planilha (documento e contas alteradas);
- criação, edição e exclusão de cliente;
- criação de simulação;
- gestão de usuários.

Cada evento guarda escritório, usuário, ação, recurso, data e hora, IP e detalhes em JSON, sem dados sensíveis. A tabela aceita só inserção. A consulta é por `GET /api/auditoria` (só `ADMIN`, paginado, filtrado pelo escritório).

## Executando

No Windows, na raiz do repositório:

```powershell
.\iniciar.ps1          # ou dois cliques em iniciar.cmd
```

O script confere Java 25+, Maven, Node 20+ e portas livres. Na primeira vez, cria o `backend/.env`: pede a senha do PostgreSQL e gera um `JWT_SECRET`. Depois cria o banco se ele não existir, roda `npm ci` quando o `package-lock.json` muda e sobe o backend e o frontend em janelas próprias.

| Opção | Efeito |
|---|---|
| `-Perfil padrao` | sobe sem o perfil `dev` |
| `-SemFrontend` | sobe só o backend |
| `-PularInstalacao` | não roda o `npm ci` |
| `-NaoAbrirNavegador` | não abre o navegador no fim |

Manualmente:

```bash
cd backend
cp .env.example .env   # preencha DB_PASSWORD e JWT_SECRET
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

A API sobe em `http://localhost:8080`.

## Testes

```bash
mvn test   # requer Docker em execução
```

- **Integração** (`integracao/`, anotação `@TesteIntegracao`, um único contexto):
  - isolamento entre escritórios e papéis;
  - escritório, signup e gestão de usuários;
  - débitos e simulação com vários débitos;
  - documentos (upload, SHA-256, download por stream, jobs) e pipeline (tipo pelo conteúdo, inconsistências, reprocessamento);
  - sessão em cookie e CSRF;
  - ECD (cada leiaute, cliente pelo arquivo, retificação, cada validação, reprocessamento), demonstrações (referencial, publicada, AH/AV, conferência, origem, exercício sem tabela, plano não suportado) e balancete em planilha (assistente, de-para, papéis, auditoria, resultado não encerrado);
  - ECF, EFD-Contribuições e EFD ICMS/IPI (`ImportacaoFiscalTest`: apurações e origem, detalhe, saldos, fórmulas, contagem de registro pulado, conferência ECD × ECF nos dois sentidos, retificação, filial, isolamento);
  - situação fiscal, declarado e pago (`SituacaoFiscalTest`: retificação da DCTF, PDF × .dec, a mesma inscrição em duas fontes e a divergência, simulação com débito importado, layout desconhecido, PDF sem texto);
  - **aceite com a massa fictícia** (`MassaFicticiaAceiteTest`): toda a massa pela central de importação, os valores de `_gerador/valores_esperados.json` e os problemas plantados do gabarito (itens 1, 2, 3, 5, 6, 7, 8 e 10; dados dos itens 4 e 9). No item 2, a multa (6.526,08) e os juros (828,12) do `AcrescimosMoratorios` batem exatamente com os do relatório de situação fiscal da massa;
  - auditoria;
  - ProblemDetail;
  - banco do zero só com as migrações.
- **Storage** (`storage/`): o mesmo contrato para o local e para o S3 contra MinIO real.
- **Leitura** (`sped/`, `contabil/planilha/`, `importacao/`, `pdf/`, `situacao/`): `LeitorSped`, leiaute por versão, detector de tipo, `.xlsx` (fórmula, zip bomb) e `.csv`; `LeitorPdf` (marca d'água, PDF sem texto, com senha, páginas demais, normalizadores), cada adaptador e o `.dec` (`AdaptadoresTest`), `AcrescimosMoratoriosTest` (30, 60 e 61 dias, virada de mês, falta de SELIC) e os status da conciliação.
- **Massa fictícia** em `src/test/resources/massa-ficticia/` (gerador, arquivos, README com as limitações e o gabarito; como regenerar no README dela).
- **Amostras sintéticas** em `src/test/resources/amostras/` (geradas por `scripts/sped/gerar_amostras_ecd.py`, `scripts/sped/gerar_amostras_fiscais.py`, que monta cada linha pelo leiaute, e `scripts/planilha/gerar_amostras_planilha.py`). ECD de 2021/2020 e de 2024; ECF Lucro Real 2021 com prejuízo compensado e Presumido 2024; EFD-Contribuições não cumulativa e cumulativa; EFD ICMS/IPI com ST, DIFAL/FCP, IPI e saldo credor; variantes retificadora, fórmula que não fecha e contagem errada.
- **Opcionais**: `mvn test -Dtest=DesempenhoEcdTest -Ddesempenho=true` (ECD de 300 MB) e `mvn test -Dtest=DesempenhoEfdTest -Ddesempenho=true -DargLine=-Xmx256m` (EFD de 1 GB, `@Tag("desempenho")`); `AmostrasReaisTest` roda sozinho quando existe `amostras-locais/` na raiz (ECD, ECF e EFD reais anonimizadas, fora do git).
- **Motor de parcelamento** (`service/calculo/`):
  - testes de caracterização do comportamento atual;
  - testes normativos `@Disabled("Fase 5 — ...")` documentando cada divergência em relação à lei.

## Erros

Todas as respostas de erro seguem o `ProblemDetail` (RFC 9457): `status`, `title`, `detail` e, quando couber, `errors` (validação), `recursoExistenteId` (duplicado) ou `correlacao` (erro interno: a mensagem interna não é exposta; o id permite achar o log).

## Endpoints

A sessão viaja no cookie `fiscalhub_sessao` (`httpOnly`, `SameSite=Lax`, `Secure` fora do dev, `Path=/api`) com o JWT; o usuário e o escritório vêm sempre dele. Toda requisição que altera estado precisa do cabeçalho `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN` (sem ele: 403). Rotas públicas: `/api/auth/**`. Paginação: `?pagina=0&tamanho=20` (máximo 100).

### Autenticação (público)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/signup` | cria escritório + `ADMIN` (`nome`, `email`, `senha`, `cpf`, `telefone`, `nomeEscritorio`, `documentoEscritorio`) |
| POST | `/api/auth/login` | emite o cookie da sessão e devolve o usuário (`id`, `nome`, `email`, `papel`, `escritorioId`, `trocarSenhaNoProximoLogin`...) |
| POST | `/api/auth/logout` | apaga o cookie e invalida o JWT no servidor (versão do token) |
| GET | `/api/auth/csrf` | emite o cookie `XSRF-TOKEN` |

### Usuários

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/api/usuarios/me` | todos | próprio cadastro (sem senha) |
| PUT | `/api/usuarios/me` | todos | altera `nome`, `cpf`, `telefone` |
| PUT | `/api/usuarios/me/senha` | todos | troca a senha (`senhaAtual`, `novaSenha`) e emite um cookie novo |
| DELETE | `/api/usuarios/me` | todos | desativa a própria conta |
| GET | `/api/usuarios` | ADMIN | usuários do escritório |
| POST | `/api/usuarios` | ADMIN | cria com senha provisória (`nome`, `email`, `papel`, `senhaProvisoria`) |
| PATCH | `/api/usuarios/{id}/papel` | ADMIN | altera o papel |
| PATCH | `/api/usuarios/{id}/desativar` | ADMIN | desativa |

### Clientes

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/api/clientes` | todos | clientes do escritório |
| GET | `/api/clientes/{id}` | todos | um cliente |
| POST | `/api/clientes` | ADMIN, CONSULTOR | cadastra (valida CNPJ e CEP) |
| PUT | `/api/clientes/{id}` | ADMIN, CONSULTOR | altera |
| DELETE | `/api/clientes/{id}` | ADMIN, CONSULTOR | exclui |

### Documentos

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| POST | `/api/documentos` | todos | importa na central (multipart: `arquivo` + `documento` JSON opcional com `tipo`/`observacao`) |
| POST | `/api/clientes/{id}/documentos` | todos | importa dentro do cliente |
| GET | `/api/documentos?clienteId=&status=&tipo=&pagina=&tamanho=` | todos | documentos do escritório, paginados |
| GET | `/api/documentos/resumo` | todos | quantidade por status |
| GET | `/api/clientes/{id}/documentos` | todos | documentos do cliente |
| GET | `/api/documentos/{doc}` | todos | metadados (inclui a empresa lida do arquivo) |
| GET | `/api/documentos/{doc}/status` | todos | status, tipo detectado, etapa e progresso |
| GET | `/api/documentos/{doc}/inconsistencias` | todos | inconsistências com severidade, registro e linha |
| GET | `/api/documentos/{doc}/arquivo` | todos | baixa o original (stream) |
| POST | `/api/documentos/{doc}/reprocessar` | ADMIN, CONSULTOR | processa de novo a partir do storage |
| POST | `/api/documentos/{doc}/confirmar-cliente` | ADMIN, CONSULTOR | sem corpo: cadastra a empresa do arquivo; `{ clienteId }`: associa a um cliente |
| DELETE | `/api/documentos/{doc}` | ADMIN, CONSULTOR | exclui registro, dados derivados e arquivo |

### Planilha (balancete)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/documentos/{doc}/planilha?aba=` | abas, primeiras linhas e mapeamento sugerido ou reconhecido |
| POST | `/api/documentos/{doc}/planilha/mapeamento` | ADMIN, CONSULTOR: colunas, linha do cabeçalho, período e plano (salvo para o cliente); reprocessa; auditado |
| GET | `/api/documentos/{doc}/planilha/depara` | contas analíticas, associação atual e sugestão da ECD |
| PUT | `/api/documentos/{doc}/planilha/depara` | ADMIN, CONSULTOR: grava associações (`[{ codigoConta, codigoReferencial }]`, null desassocia); reprocessa; devolve e audita `{ contasAlteradas }` |
| GET | `/api/referencial/contas?plano=&exercicio=&busca=` | contas referenciais analíticas (até 50) |

### Demonstrações

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/clientes/{id}/escrituracoes` | escriturações importadas (vigente ou substituída) |
| GET | `/api/clientes/{id}/demonstracoes?tipo=BP\|DRE&origem=REFERENCIAL\|PUBLICADA&exercicios=2023,2024` | comparativo com AH, AV e conferência |
| GET | `/api/clientes/{id}/demonstracoes/linhas/{linhaId}/origem` | arquivo, registro e linha (publicada) ou contas e saldos usados (referencial) |

### Apurações (somente leitura)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/clientes/{id}/apuracoes?tributo=&de=&ate=` | apurações das escriturações vigentes (tributo, período, regime, base, apurado, deduções, a recolher, saldo credor, origem de cada valor, arquivo) |
| GET | `/api/clientes/{id}/apuracoes/{apuracaoId}/detalhe` | a apuração, as linhas do cálculo e as obrigações a recolher, com registro e linha |
| GET | `/api/clientes/{id}/apuracoes/alertas` | alertas e erros das escriturações fiscais e conferências entre arquivos |
| GET | `/api/clientes/{id}/saldos-fiscais` | prejuízo fiscal, base negativa, créditos e saldos credores por período |

### Situação fiscal, declarado, pago e conciliação (somente leitura)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/clientes/{id}/debitos?orgao=&situacao=` | um débito por dívida (fontes juntas), com total, `impedeCertidao`, `divergente` e as origens de cada fonte |
| GET | `/api/clientes/{id}/parcelamentos` | parcelamentos e transações (parcelas pagas e total, valor, saldo, situação, débitos vinculados, origem) |
| GET | `/api/clientes/{id}/pendencias` | omissões de declaração e afins |
| GET | `/api/clientes/{id}/capacidade-oficial` | CAPAG da PGFN (classe, capacidade, passivo, razão, consistência com a Portaria) |
| GET | `/api/clientes/{id}/declaracoes` | débitos declarados (DCTF, DCTFWeb, conta corrente do ICMS) com vinculações e origens |
| GET | `/api/clientes/{id}/pagamentos` | pagamentos (DARF, GR-PR), cada um uma vez, com as origens de todas as fontes |
| GET | `/api/clientes/{id}/conciliacao?de=&ate=&status=` | itens apurado × declarado × pago (`de`/`ate` em aaaa-mm) e os alertas |
| GET | `/api/clientes/{id}/conciliacao/{item}` | um item (ex.: `COFINS-2026-08`, `ICMS_ST-PR-2026-08`) com origens, acréscimos e débitos |

Não há criação manual: tudo vem da importação. Cliente de outro escritório: 404.

### Simulações de parcelamento

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| POST | `/api/simulacoes/calcular` | todos | calcula sem salvar (`clienteId`, `debitoIds`, `orgao`, `modalidade`, `numeroParcelas`, ...) |
| POST | `/api/simulacoes/calcular/salvar` | ADMIN, CONSULTOR | calcula e salva com a tabela de parcelas |
| GET | `/api/simulacoes/{id}` | todos | uma simulação |
| GET | `/api/simulacoes/cliente/{clienteId}` | todos | simulações do cliente |
| PATCH | `/api/simulacoes/{id}/aprovar` | ADMIN, CONSULTOR | marca como aprovada |
| DELETE | `/api/simulacoes/{id}` | ADMIN, CONSULTOR | exclui |

A simulação consolida vários débitos do mesmo cliente: soma o principal e usa o vencimento mais antigo. O cálculo por débito e as correções normativas entram na Fase 5.

### Auditoria

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/api/auditoria?pagina=&tamanho=` | ADMIN | eventos do escritório, mais recentes primeiro, com o nome do usuário e o resultado |

## Estrutura

```
src/main/java/com/fiscalhub/backend/
├── auditoria/    # @Auditavel, AuditoriaAspect (único ponto de gravação), consulta
├── contabil/     # escrituração: RepositorioContabil (lote), DemonstracaoService
│   ├── ecd/        # ProcessadorEcd + LeituraEcd
│   ├── planilha/   # ProcessadorPlanilha, leitura .xlsx/.csv, assistente
│   └── referencial/ # plano referencial oficial e montagem das demonstrações
├── controller/   # recebem a requisição e devolvem a resposta
├── dto/          # contratos da API; fromEntity() centraliza o mapeamento por agregado
├── entity/       # entidades JPA e enums
├── exception/    # exceções de negócio e GlobalExceptionHandler (ProblemDetail)
├── repository/   # Spring Data
├── security/     # JWT, UsuarioAutenticado, filtro, SecurityConfig, @ConsultorOuAdmin
├── service/      # regras de negócio; AcessoRecursoService = autorização por escritório
│   └── calculo/  # motor de parcelamento (RFB e SEFA-PR)
├── fiscal/       # ECF, EFD-Contribuições, EFD ICMS/IPI: leituras, ProcessadorFiscal, modelo de apuração, conferência ECD × ECF
├── pdf/          # LeitorPdf (PDFBox), DocumentoPdf (seção, tabela, campo), AdaptadorDocumentoPdf
├── situacao/     # ProcessadorSituacao, RepositorioSituacao, SituacaoFiscalService, ConciliacaoService, AcrescimosMoratorios, CodigosReceita
│   └── adaptadores/ # um adaptador por documento e versão de layout; LeitorDctfDec
├── importacao/   # pipeline: detector, ProcessadorDocumento, contexto, inconsistências, jobs, Escrituracoes (retificação)
├── sped/         # LeitorSped (stream, registros de interesse), LeituraSped (base comum), LayoutSped, Saldos
├── storage/      # ArmazenamentoArquivos: local e S3
└── util/
src/main/resources/
├── application.properties
├── application-dev.properties
├── db/migration/ # Flyway
├── db/dev/       # seed só do perfil dev
├── sped/         # leiautes SPED por versão (gerados dos manuais)
├── plano-referencial/ # tabelas oficiais + tabelas.txt (fonte e SHA-256)
├── receitas/     # tabela de códigos de receita (fonte por código)
└── dctf/         # layouts do .dec da DCTF por versão
scripts/          # geração de leiaute, amostras e download das tabelas oficiais
```

## Segurança

- Senhas com BCrypt; `senha` nunca sai em resposta (DTO + `@JsonIgnore`).
- Segredos só por variável de ambiente, sem valor padrão no código.
- Isolamento por escritório num único ponto (`AcessoRecursoService`) e papéis por `@PreAuthorize`.
- CORS só para as origens em `CORS_ALLOWED_ORIGINS`, com credenciais (cookie). Frontend e API precisam estar no mesmo site.
- Sessão em cookie `httpOnly` (o JS não lê o JWT) e CSRF por cookie + cabeçalho em todo método que altera estado; logout invalida o JWT no servidor.
- Arquivos não confiáveis: limite de tamanho de linha (SPED, CSV), de linhas e colunas (planilha), de registros de apuração guardados em memória (200 mil, ECF e EFD), zip bomb e XXE no `.xlsx`, fórmulas nunca avaliadas; PDF com 20 MB e 300 páginas no máximo, sem senha, cache em disco, sem execução de scripts nem recursos externos; `.dec` com o mesmo limite de tamanho. Linhas de registros pulados não são montadas.
- O `app.jwt.secret` antigo ficou no histórico do git: **gere um segredo novo** para qualquer ambiente real.
