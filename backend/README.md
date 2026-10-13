# Fiscal Hub Backend

A comprehensive REST API built with Java Spring Boot for managing fiscal and tax operations.

## Overview

Fiscal Hub Backend is a Spring Boot application that provides RESTful endpoints for managing users, companies, tax declarations, and installment simulations. It includes JWT-based authentication and PostgreSQL database integration.

## Features

- **JWT Authentication** - Secure authentication system with JSON Web Tokens
- **User Management** - Complete CRUD operations for user accounts
- **Company Management** - Manage company information and relationships
- **Tax Declarations** - Handle tax declaration records
- **Installment Simulations** - Calculate and manage payment installment plans
- **Spring Security** - Comprehensive security configuration
- **JPA/Hibernate** - Object-relational mapping with PostgreSQL
- **Input Validation** - Request validation using Jakarta Bean Validation
- **Exception Handling** - Global exception handling with meaningful error messages

## Technology Stack

- **Java 25**
- **Spring Boot 3.5.6**
- **Spring Security** - Authentication and authorization
- **Spring Data JPA** - Data persistence
- **PostgreSQL** - Primary database
- **JWT (JJWT)** - Token-based authentication
- **Maven** - Build and dependency management
- **Lombok** - Reduce boilerplate code
- **H2 Database** - In-memory database for testing

## Prerequisites

- Java 25 or higher
- Maven 3.6+
- PostgreSQL 12+

## Configuração (variáveis de ambiente)

Copie `backend/.env.example` para `backend/.env` e preencha. O `.env` é lido na inicialização (`spring.config.import`) e não é versionado; variáveis de ambiente reais têm precedência.

| Variável | Obrigatória | Padrão | Uso |
|---|---|---|---|
| `DB_PASSWORD` | sim | — | senha do PostgreSQL |
| `JWT_SECRET` | sim | — | segredo HMAC do JWT, mínimo 32 bytes (`openssl rand -base64 48`). O app não sobe sem ele ou com menos de 256 bits |
| `DB_URL` | não | `jdbc:postgresql://localhost:5432/fiscalhub` | URL JDBC |
| `DB_USERNAME` | não | `postgres` | usuário do banco |
| `CORS_ALLOWED_ORIGINS` | não | `http://localhost:5173` | origens permitidas, separadas por vírgula |
| `SPRING_PROFILES_ACTIVE` | não | — | `dev` liga SQL no log e DEBUG do Spring Security |

## Banco de dados (Flyway)

O schema é versionado em `src/main/resources/db/migration` e aplicado pelo Flyway na inicialização. O Hibernate só valida (`ddl-auto=validate`); qualquer mudança de schema entra como nova migração `V<n>__descricao.sql`.

**Banco do zero:**

```sql
CREATE DATABASE fiscalhub;
```

Depois, suba o app (`mvn spring-boot:run`). O Flyway cria tudo a partir da V1.

**Banco de desenvolvimento criado antes do Flyway** (pelo antigo `ddl-auto=update`): `spring.flyway.baseline-on-migrate=true` marca a V1 como aplicada e executa só da V2 em diante.

## Installation & Running

1. Clone the repository:
```bash
git clone https://github.com/charlesmuller194/fiscalhubbackend.git
cd fiscalhubbackend
```

2. Build the project:
```bash
mvn clean install
```

3. Run the application:
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Endpoints

### Authentication

- **POST** `/api/auth/signup` - Register a new user
- **POST** `/api/auth/login` - Login and receive JWT token

### Users (Protected)

- **GET** `/api/usuarios` - Get all users
- **GET** `/api/usuarios/{id}` - Get user by ID
- **GET** `/api/usuarios/email/{email}` - Get user by email
- **POST** `/api/usuarios` - Create a new user
- **PUT** `/api/usuarios/{id}` - Update user
- **DELETE** `/api/usuarios/{id}` - Delete user

### Companies (Protected)

- **GET** `/api/empresas` - Get all companies
- **GET** `/api/empresas/{id}` - Get company by ID
- **GET** `/api/empresas/usuario/{usuarioId}` - Get companies by user
- **POST** `/api/empresas?usuarioId={id}` - Create a new company
- **PUT** `/api/empresas/{id}` - Update company
- **DELETE** `/api/empresas/{id}` - Delete company

### Declarations (Protected)

- **GET** `/api/declaracoes` - Get all declarations
- **GET** `/api/declaracoes/{id}` - Get declaration by ID
- **GET** `/api/declaracoes/usuario/{usuarioId}` - Get declarations by user
- **GET** `/api/declaracoes/empresa/{empresaId}` - Get declarations by company
- **GET** `/api/declaracoes/status/{status}` - Get declarations by status
- **POST** `/api/declaracoes?usuarioId={id}&empresaId={id}` - Create declaration
- **PUT** `/api/declaracoes/{id}` - Update declaration
- **DELETE** `/api/declaracoes/{id}` - Delete declaration

### Installment Simulations (Protected)

- **GET** `/api/simulacoes` - Get all simulations
- **GET** `/api/simulacoes/{id}` - Get simulation by ID
- **GET** `/api/simulacoes/declaracao/{declaracaoId}` - Get simulations by declaration
- **GET** `/api/simulacoes/aprovadas/{aprovada}` - Get simulations by approval status
- **POST** `/api/simulacoes?declaracaoId={id}` - Create simulation
- **PUT** `/api/simulacoes/{id}` - Update simulation
- **DELETE** `/api/simulacoes/{id}` - Delete simulation

### Test Endpoints

- **GET** `/api/test/public` - Public test endpoint (no auth required)
- **GET** `/api/test/protected` - Protected test endpoint (auth required)

## Authentication

All endpoints except `/api/auth/**` and `/api/test/public` require authentication.

1. **Register**: Send POST request to `/api/auth/signup` with user details
2. **Login**: Send POST request to `/api/auth/login` with email and password
3. **Use Token**: Include the JWT token in the Authorization header:
   ```
   Authorization: Bearer <your-jwt-token>
   ```

### Example Login Request

```json
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "senha": "password123"
}
```

### Example Response

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": 1,
  "email": "user@example.com",
  "nome": "User Name"
}
```

## Database Schema

### Tables

- **usuario** - User accounts
- **empresa** - Companies linked to users
- **declaracao** - Tax declarations linked to users and companies
- **simulacao_parcelamento** - Installment simulations for declarations

### Entity Relationships

- One User has many Companies
- One User has many Declarations
- One Company has many Declarations
- One Declaration has many Installment Simulations

## Configuration

Key configuration properties in `application.properties`:

```properties
# Server port
server.port=8080

# Database configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/fiscalhub
spring.datasource.username=postgres
spring.datasource.password=postgres

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# JWT Configuration
app.jwt.secret=<your-secret-key>
app.jwt.expiration-ms=86400000
```

## Running Tests

```bash
mvn test
```

Tests use an in-memory H2 database and don't require PostgreSQL.

## Project Structure

```
src/
├── main/
│   ├── java/com/fiscalhub/backend/
│   │   ├── controller/       # REST controllers
│   │   ├── dto/              # Data Transfer Objects
│   │   ├── entity/           # JPA entities
│   │   ├── exception/        # Exception handlers
│   │   ├── repository/       # Spring Data repositories
│   │   ├── security/         # Security configuration and JWT
│   │   └── service/          # Business logic layer
│   └── resources/
│       └── application.properties
└── test/                     # Unit and integration tests
```

## Security Notes

- Passwords are encrypted using BCrypt
- JWT tokens expire after 24 hours (configurable)
- CORS is enabled for all origins (configure for production)
- Update the JWT secret key in production

## License

This project is licensed under the MIT License.