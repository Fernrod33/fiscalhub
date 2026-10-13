# FiscalHub — Backend

API REST do FiscalHub: ferramenta para advogados e contadores consultores tributários. Importa escriturações e demonstrações, lê a parte tributária e simula parcelamentos conforme as normas da Receita Federal e da SEFA-PR.

Repositório: [Fernrod33/fiscalhub](https://github.com/Fernrod33/fiscalhub) (backend em `backend/`, frontend React em `frontend/`).

## Stack

- Java 25, Spring Boot 3.5.6 (Web, Data JPA, Security, Validation)
- PostgreSQL + Flyway (migrações versionadas)
- JWT (JJWT 0.11) para autenticação stateless
- Testes: JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL 18)

## Pré-requisitos

- JDK 25 e Maven 3.9+
- PostgreSQL 15+ (desenvolvimento local)
- Docker (só para `mvn test`: os testes de integração sobem um PostgreSQL via Testcontainers)

## Configuração (variáveis de ambiente)

Copie `backend/.env.example` para `backend/.env` e preencha. O `.env` é lido na inicialização (`spring.config.import`) e não é versionado; variáveis de ambiente reais têm precedência.

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_PASSWORD` | sim | — | senha do PostgreSQL |
| `JWT_SECRET` | sim | — | segredo HMAC do JWT, mínimo 32 bytes (`openssl rand -base64 48`). O app não sobe sem ele ou com menos de 256 bits |
| `DB_URL` | não | `jdbc:postgresql://localhost:5432/fiscalhub` | URL JDBC |
| `DB_USERNAME` | não | `postgres` | usuário do banco |
| `CORS_ALLOWED_ORIGINS` | não | `http://localhost:5173` | origens permitidas, separadas por vírgula |
| `SPRING_PROFILES_ACTIVE` | não | — | `dev` para desenvolvimento (ver Perfis) |

## Perfis

| Perfil | Arquivo | Efeito |
|---|---|---|
| padrão | `application.properties` | log `INFO`, sem SQL no log, sem DEBUG do Spring Security |
| `dev` | `application-dev.properties` | `show-sql`, `format_sql`, log `DEBUG`; carrega índices SELIC/UFPR **aproximados** de exemplo (`DataInitializer`) |
| `test` | `src/test/resources/application-test.properties` | usado pelos testes de integração; o banco vem do Testcontainers |

Nenhum perfil cria usuário: cadastre-se por `POST /api/auth/signup`.

## Banco de dados (Flyway)

O schema é versionado em `src/main/resources/db/migration` e aplicado pelo Flyway na inicialização. O Hibernate só valida (`ddl-auto=validate`); qualquer mudança de schema entra como nova migração `V<n>__descricao.sql`.

| Migração | Conteúdo |
|---|---|
| `V1__baseline.sql` | usuário, empresa, declaração, simulação, parcela, índice monetário |
| `V2__create_documentos_fiscais.sql` | documentos fiscais importados e seus tributos |

**Banco do zero:**

```sql
CREATE DATABASE fiscalhub;
```

Depois, suba o app. O Flyway cria tudo a partir da V1.

**Banco de desenvolvimento criado antes do Flyway** (pelo antigo `ddl-auto=update`): `spring.flyway.baseline-on-migrate=true` marca a V1 como aplicada e executa só da V2 em diante.

## Executando

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

- **Integração** (`integracao/`): isolamento entre usuários (IDOR), ausência de senha nas respostas, 401 sem token e banco do zero só com as migrações.
- **Motor de parcelamento** (`service/calculo/`): testes de caracterização do comportamento atual e testes normativos `@Disabled("Fase 5 — ...")`, que documentam cada divergência em relação à lei e serão ativados quando o motor for corrigido.

## Autenticação

Todos os endpoints, exceto `/api/auth/**`, exigem `Authorization: Bearer <token>`. Sem token, ou com token inválido, a resposta é **401**.

O usuário é sempre o do token; nenhum endpoint aceita `usuarioId` como parâmetro. Recurso inexistente ou de outro usuário responde **404**, para não revelar que existe.

## Endpoints

### Autenticação (público)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/signup` | cadastra usuário (`nome`, `email`, `senha`, `cpf`, `telefone`) |
| POST | `/api/auth/login` | devolve `{ token, type, id, email, nome }` |

### Usuário autenticado

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/usuarios/me` | dados do próprio usuário (sem senha) |
| PUT | `/api/usuarios/me` | altera `nome`, `cpf` e `telefone` |
| DELETE | `/api/usuarios/me` | exclui a própria conta |

### Empresas

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/empresas/minhas` | empresas do usuário |
| GET | `/api/empresas/{id}` | uma empresa |
| POST | `/api/empresas` | cadastra (valida CNPJ e CEP) |
| PUT | `/api/empresas/{id}` | altera |
| DELETE | `/api/empresas/{id}` | exclui |

### Documentos fiscais da empresa

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/empresas/{empresaId}/documentos` | lista os documentos importados |
| GET | `/api/empresas/{empresaId}/documentos/{id}` | metadados de um documento |
| GET | `/api/empresas/{empresaId}/documentos/{id}/arquivo` | baixa o arquivo original |
| POST | `/api/empresas/{empresaId}/documentos` | importa (multipart: `arquivo` + `documento` JSON; PDF, XML, CSV ou XLSX até 5 MB) |

### Declarações

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/declaracoes/{id}` | uma declaração |
| GET | `/api/declaracoes/empresa/{empresaId}` | declarações da empresa |
| POST | `/api/declaracoes?empresaId={id}` | cria |
| PUT | `/api/declaracoes/{id}` | altera |
| DELETE | `/api/declaracoes/{id}` | exclui |

### Simulações de parcelamento

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/simulacoes/calcular` | calcula sem salvar |
| POST | `/api/simulacoes/calcular/salvar` | calcula e salva com a tabela de parcelas |
| GET | `/api/simulacoes/{id}` | uma simulação salva |
| GET | `/api/simulacoes/declaracao/{declaracaoId}` | simulações da declaração |
| PATCH | `/api/simulacoes/{id}/aprovar` | marca como aprovada |
| DELETE | `/api/simulacoes/{id}` | exclui |

> Declarações e simulações serão substituídas pelo novo domínio (débitos importados) nas próximas fases.

## Estrutura

```
src/main/java/com/fiscalhub/backend/
├── config/       # DataInitializer (só perfil dev)
├── controller/   # recebem a requisição e devolvem a resposta
├── dto/          # contratos da API; fromEntity() centraliza o mapeamento por agregado
├── entity/       # entidades JPA e enums
├── exception/    # exceções de negócio e GlobalExceptionHandler
├── repository/   # Spring Data
├── security/     # JWT, filtro e SecurityConfig (CORS global)
├── service/      # regras de negócio; AcessoRecursoService = autorização por dono
│   └── calculo/  # motor de parcelamento (RFB e SEFA-PR)
└── util/
src/main/resources/
├── application.properties
├── application-dev.properties
└── db/migration/ # Flyway
```

## Segurança

- Senhas com BCrypt; `senha` nunca sai em resposta (DTO + `@JsonIgnore`).
- Segredos só por variável de ambiente, sem valor padrão no código.
- CORS só para as origens em `CORS_ALLOWED_ORIGINS`.
- O `app.jwt.secret` antigo ficou no histórico do git: **gere um segredo novo** para qualquer ambiente real.
