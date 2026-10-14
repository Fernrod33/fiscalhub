# FiscalHub — Backend

API REST do FiscalHub: ferramenta para advogados e contadores consultores tributários, organizados em **escritórios** que atendem vários **clientes** (empresas). Todos os dados fiscais entram por **importação de arquivo**; nenhum valor fiscal é digitado.

Repositório: [Fernrod33/fiscalhub](https://github.com/Fernrod33/fiscalhub) (backend em `backend/`, frontend React em `frontend/`).

## Stack

- Java 25, Spring Boot 3.5.6 (Web, Data JPA, Security, Validation, AOP)
- PostgreSQL + Flyway (migrações versionadas)
- JWT (JJWT 0.11) para autenticação stateless
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
| `ASSISTENTE` | ler e importar arquivos; não altera nem exclui nada e não gerencia usuários |

- **Cliente** pertence ao escritório (qualquer membro o enxerga); quem cadastrou fica em `criado_por`. CNPJ é único **por escritório**.
- Documentos, débitos e simulações herdam o escopo pelo cliente. Recurso de outro escritório responde **404**; papel sem permissão responde **403**.
- Saída de usuário é **desativação** (login bloqueado; nada fica órfão). O último `ADMIN` ativo não sai nem é rebaixado.
- O JWT carrega `uid`, `escritorioId`, `papel` e `tv` (versão do token). A cada requisição o filtro lê o usuário pela chave primária e confere se ele segue ativo e com a mesma versão. Troca de senha, desativação e mudança de papel incrementam a versão e invalidam na hora os tokens anteriores.
- **Senha provisória**: enquanto `trocarSenhaNoProximoLogin` for verdadeiro, toda rota autenticada responde 403 com `type` `/problemas/senha-troca-obrigatoria`, exceto `GET /api/usuarios/me` e `PUT /api/usuarios/me/senha`. A troca devolve um token novo.

## Configuração (variáveis de ambiente)

Copie `backend/.env.example` para `backend/.env` e preencha. O `.env` é lido na inicialização (`spring.config.import`) e não é versionado; variáveis de ambiente reais têm precedência.

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_PASSWORD` | sim | — | senha do PostgreSQL |
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

**Banco do zero:** `CREATE DATABASE fiscalhub;` e suba o app; o Flyway cria tudo a partir da V1.

**Banco de desenvolvimento criado antes do Flyway** (pelo antigo `ddl-auto=update`): `spring.flyway.baseline-on-migrate=true` marca a V1 como aplicada e executa da V2 em diante.

## Importação, storage e processamento

- **Upload** (`POST /api/clientes/{id}/documentos`, multipart): grava direto no storage por stream, calculando o **SHA-256** no caminho. O mesmo arquivo enviado de novo para o mesmo cliente responde **409** com `recursoExistenteId`.
- Tipos aceitos: `.txt` (SPED), `.pdf`, `.csv`, `.xlsx`, com o tipo de conteúdo conferido por extensão.
- **Download** por stream (`StreamingResponseBody`).
- **Storage**: `ArmazenamentoArquivos`, com `ArmazenamentoLocal` (dev) e `ArmazenamentoS3` (S3/MinIO), escolhido por `fiscalhub.storage.tipo`.
- **Jobs**: o documento entra `RECEBIDO` e um registro em `job_processamento` é despachado, depois do commit, ao pool do Spring Boot (`spring.task.execution.pool.*`). Hoje o processador só valida o arquivo e marca `PROCESSADO`; os parsers entram na Fase 1. Na subida, jobs presos em `PROCESSANDO` voltam para a fila. Acompanhe por `GET /api/clientes/{id}/documentos/{doc}/status`.

## Índices: SELIC oficial

A SELIC mensal vem do Banco Central (API SGS, **série 4390**, "Taxa de juros - Selic acumulada no mês", % a.m.). A importação roda na subida e diariamente (`fiscalhub.selic.cron`, padrão 6h). Ela traz só meses fechados (a série inclui o mês corrente parcial), não duplica e corrige valores aproximados anteriores, gravando a origem (`BCB_SGS_4390`) e a data da coleta. Sem SELIC para um mês do período, a simulação responde 400 ("Índice SELIC ausente para AAAA-MM"); não há estimativa.

A UPF/UFPR do Paraná ainda não tem fonte oficial automatizada (Fase 5). Em `dev` há um seed aproximado.

## Auditoria

Toda gravação passa pela anotação `@Auditavel` e pelo `AuditoriaAspect`. São auditados:
- login (sucesso e falha);
- importação, download e exclusão de documento;
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
  - documentos (upload, SHA-256, download por stream, jobs);
  - auditoria;
  - ProblemDetail;
  - banco do zero só com as migrações.
- **Storage** (`storage/`): o mesmo contrato para o local e para o S3 contra MinIO real.
- **Motor de parcelamento** (`service/calculo/`):
  - testes de caracterização do comportamento atual;
  - testes normativos `@Disabled("Fase 5 — ...")` documentando cada divergência em relação à lei.

## Erros

Todas as respostas de erro seguem o `ProblemDetail` (RFC 9457): `status`, `title`, `detail` e, quando couber, `errors` (validação), `recursoExistenteId` (duplicado) ou `correlacao` (erro interno: a mensagem interna não é exposta; o id permite achar o log).

## Endpoints

Todos exigem `Authorization: Bearer <token>`, exceto `/api/auth/**`. O usuário e o escritório vêm sempre do token.

### Autenticação (público)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/signup` | cria escritório + `ADMIN` (`nome`, `email`, `senha`, `cpf`, `telefone`, `nomeEscritorio`, `documentoEscritorio`) |
| POST | `/api/auth/login` | devolve `{ token, type, id, email, nome, escritorioId, papel, trocarSenhaNoProximoLogin }` |

### Usuários

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/api/usuarios/me` | todos | próprio cadastro (sem senha) |
| PUT | `/api/usuarios/me` | todos | altera `nome`, `cpf`, `telefone` |
| PUT | `/api/usuarios/me/senha` | todos | troca a senha (`senhaAtual`, `novaSenha`) e devolve um token novo (mesmo formato do login) |
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

### Documentos do cliente

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/api/clientes/{id}/documentos` | todos | lista |
| GET | `/api/clientes/{id}/documentos/{doc}` | todos | metadados |
| GET | `/api/clientes/{id}/documentos/{doc}/arquivo` | todos | baixa o original (stream) |
| GET | `/api/clientes/{id}/documentos/{doc}/status` | todos | andamento do processamento |
| POST | `/api/clientes/{id}/documentos` | todos | importa (multipart: `arquivo` + `documento` JSON) |
| DELETE | `/api/clientes/{id}/documentos/{doc}` | ADMIN, CONSULTOR | exclui registro e arquivo (409 se já gerou débitos) |

### Débitos (somente leitura)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/clientes/{id}/debitos` | débitos do cliente, cada um com o documento e o trecho de origem |

Não há criação manual: os débitos são gerados pela importação (parsers da Fase 2).

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
| GET | `/api/auditoria?page=&size=` | ADMIN | eventos do escritório, mais recentes primeiro |

## Estrutura

```
src/main/java/com/fiscalhub/backend/
├── auditoria/    # @Auditavel, AuditoriaAspect (único ponto de gravação), consulta
├── controller/   # recebem a requisição e devolvem a resposta
├── dto/          # contratos da API; fromEntity() centraliza o mapeamento por agregado
├── entity/       # entidades JPA e enums
├── exception/    # exceções de negócio e GlobalExceptionHandler (ProblemDetail)
├── repository/   # Spring Data
├── security/     # JWT, UsuarioAutenticado, filtro, SecurityConfig, @ConsultorOuAdmin
├── service/      # regras de negócio; AcessoRecursoService = autorização por escritório
│   └── calculo/  # motor de parcelamento (RFB e SEFA-PR)
├── storage/      # ArmazenamentoArquivos: local e S3
└── util/
src/main/resources/
├── application.properties
├── application-dev.properties
├── db/migration/ # Flyway
└── db/dev/       # seed só do perfil dev
```

## Segurança

- Senhas com BCrypt; `senha` nunca sai em resposta (DTO + `@JsonIgnore`).
- Segredos só por variável de ambiente, sem valor padrão no código.
- Isolamento por escritório num único ponto (`AcessoRecursoService`) e papéis por `@PreAuthorize`.
- CORS só para as origens em `CORS_ALLOWED_ORIGINS`.
- O `app.jwt.secret` antigo ficou no histórico do git: **gere um segredo novo** para qualquer ambiente real.
