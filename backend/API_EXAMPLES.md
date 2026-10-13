> **OBSOLETO**: descreve a API anterior à v2 (endpoints removidos, `usuarioId` por parâmetro). Use o [README](README.md).

# Fiscal Hub API - Usage Examples

This document provides practical examples for using the Fiscal Hub REST API.

## Table of Contents
- [Authentication](#authentication)
- [User Management](#user-management)
- [Company Management](#company-management)
- [Declaration Management](#declaration-management)
- [Installment Simulation](#installment-simulation)

---

## Authentication

### 1. Register a New User

**Endpoint:** `POST /api/auth/signup`

**Request:**
```json
{
  "nome": "João Silva",
  "email": "joao.silva@example.com",
  "senha": "senha123",
  "cpf": "12345678901",
  "telefone": "11999999999"
}
```

**Response (200 OK):**
```json
{
  "message": "User registered successfully!"
}
```

---

### 2. Login

**Endpoint:** `POST /api/auth/login`

**Request:**
```json
{
  "email": "joao.silva@example.com",
  "senha": "senha123"
}
```

**Response (200 OK):**
```json
{
  "token": "your-jwt-token-will-be-here",
  "type": "Bearer",
  "id": 1,
  "email": "joao.silva@example.com",
  "nome": "João Silva"
}
```

**Important:** Save the token! Use it in the `Authorization` header for all subsequent requests:
```
Authorization: Bearer <your-jwt-token>
```

---

## User Management

### 3. Get All Users

**Endpoint:** `GET /api/usuarios`

**Headers:**
```
Authorization: Bearer <your-token>
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "nome": "João Silva",
    "email": "joao.silva@example.com",
    "cpf": "12345678901",
    "telefone": "11999999999",
    "ativo": true,
    "dataCriacao": "2025-10-09T10:00:00",
    "dataAtualizacao": "2025-10-09T10:00:00"
  }
]
```

---

### 4. Get User by ID

**Endpoint:** `GET /api/usuarios/{id}`

**Headers:**
```
Authorization: Bearer <your-token>
```

**Example:** `GET /api/usuarios/1`

**Response (200 OK):**
```json
{
  "id": 1,
  "nome": "João Silva",
  "email": "joao.silva@example.com",
  "cpf": "12345678901",
  "telefone": "11999999999",
  "ativo": true,
  "dataCriacao": "2025-10-09T10:00:00",
  "dataAtualizacao": "2025-10-09T10:00:00"
}
```

---

### 5. Update User

**Endpoint:** `PUT /api/usuarios/{id}`

**Headers:**
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

**Request:**
```json
{
  "nome": "João Silva Santos",
  "email": "joao.silva@example.com",
  "cpf": "12345678901",
  "telefone": "11988888888",
  "ativo": true
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "nome": "João Silva Santos",
  "email": "joao.silva@example.com",
  "cpf": "12345678901",
  "telefone": "11988888888",
  "ativo": true,
  "dataCriacao": "2025-10-09T10:00:00",
  "dataAtualizacao": "2025-10-09T11:00:00"
}
```

---

## Company Management

### 6. Create a Company

**Endpoint:** `POST /api/empresas?usuarioId={userId}`

**Headers:**
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

**Request:**
```json
{
  "razaoSocial": "Empresa ABC Ltda",
  "nomeFantasia": "ABC Comércio",
  "cnpj": "12345678000190",
  "inscricaoEstadual": "123456789",
  "inscricaoMunicipal": "987654321",
  "endereco": "Rua Principal, 123",
  "cidade": "São Paulo",
  "estado": "SP",
  "cep": "01234567",
  "telefone": "1133334444",
  "ativa": true
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "razaoSocial": "Empresa ABC Ltda",
  "nomeFantasia": "ABC Comércio",
  "cnpj": "12345678000190",
  "inscricaoEstadual": "123456789",
  "inscricaoMunicipal": "987654321",
  "endereco": "Rua Principal, 123",
  "cidade": "São Paulo",
  "estado": "SP",
  "cep": "01234567",
  "telefone": "1133334444",
  "ativa": true,
  "dataCriacao": "2025-10-09T12:00:00",
  "dataAtualizacao": "2025-10-09T12:00:00"
}
```

---

### 7. Get Companies by User

**Endpoint:** `GET /api/empresas/usuario/{usuarioId}`

**Headers:**
```
Authorization: Bearer <your-token>
```

**Example:** `GET /api/empresas/usuario/1`

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "razaoSocial": "Empresa ABC Ltda",
    "nomeFantasia": "ABC Comércio",
    "cnpj": "12345678000190",
    "inscricaoEstadual": "123456789",
    "inscricaoMunicipal": "987654321",
    "endereco": "Rua Principal, 123",
    "cidade": "São Paulo",
    "estado": "SP",
    "cep": "01234567",
    "telefone": "1133334444",
    "ativa": true,
    "dataCriacao": "2025-10-09T12:00:00",
    "dataAtualizacao": "2025-10-09T12:00:00"
  }
]
```

---

## Declaration Management

### 8. Create a Declaration

**Endpoint:** `POST /api/declaracoes?usuarioId={userId}&empresaId={companyId}`

**Headers:**
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

**Request:**
```json
{
  "tipo": "ICMS",
  "periodoInicio": "2025-01-01",
  "periodoFim": "2025-01-31",
  "valorTotal": 50000.00,
  "valorImposto": 8500.00,
  "status": "PENDENTE",
  "dataVencimento": "2025-02-10",
  "observacoes": "Declaração de ICMS referente ao mês de janeiro/2025"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "tipo": "ICMS",
  "periodoInicio": "2025-01-01",
  "periodoFim": "2025-01-31",
  "valorTotal": 50000.00,
  "valorImposto": 8500.00,
  "status": "PENDENTE",
  "dataVencimento": "2025-02-10",
  "dataPagamento": null,
  "observacoes": "Declaração de ICMS referente ao mês de janeiro/2025",
  "dataCriacao": "2025-10-09T13:00:00",
  "dataAtualizacao": "2025-10-09T13:00:00"
}
```

---

### 9. Get Declarations by Company

**Endpoint:** `GET /api/declaracoes/empresa/{empresaId}`

**Headers:**
```
Authorization: Bearer <your-token>
```

**Example:** `GET /api/declaracoes/empresa/1`

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "tipo": "ICMS",
    "periodoInicio": "2025-01-01",
    "periodoFim": "2025-01-31",
    "valorTotal": 50000.00,
    "valorImposto": 8500.00,
    "status": "PENDENTE",
    "dataVencimento": "2025-02-10",
    "dataPagamento": null,
    "observacoes": "Declaração de ICMS referente ao mês de janeiro/2025",
    "dataCriacao": "2025-10-09T13:00:00",
    "dataAtualizacao": "2025-10-09T13:00:00"
  }
]
```

---

### 10. Update Declaration Status

**Endpoint:** `PUT /api/declaracoes/{id}`

**Headers:**
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

**Request:**
```json
{
  "tipo": "ICMS",
  "periodoInicio": "2025-01-01",
  "periodoFim": "2025-01-31",
  "valorTotal": 50000.00,
  "valorImposto": 8500.00,
  "status": "PAGO",
  "dataVencimento": "2025-02-10",
  "dataPagamento": "2025-02-08",
  "observacoes": "Declaração de ICMS paga em 08/02/2025"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "tipo": "ICMS",
  "periodoInicio": "2025-01-01",
  "periodoFim": "2025-01-31",
  "valorTotal": 50000.00,
  "valorImposto": 8500.00,
  "status": "PAGO",
  "dataVencimento": "2025-02-10",
  "dataPagamento": "2025-02-08",
  "observacoes": "Declaração de ICMS paga em 08/02/2025",
  "dataCriacao": "2025-10-09T13:00:00",
  "dataAtualizacao": "2025-10-09T14:00:00"
}
```

---

## Installment Simulation

### 11. Create an Installment Simulation

**Endpoint:** `POST /api/simulacoes?declaracaoId={declarationId}`

**Headers:**
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

**Request:**
```json
{
  "numeroParcelas": 12,
  "valorParcela": 750.00,
  "taxaJuros": 1.5,
  "valorTotal": 9000.00,
  "valorJuros": 500.00,
  "aprovada": false,
  "observacoes": "Simulação de parcelamento em 12 vezes"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "numeroParcelas": 12,
  "valorParcela": 750.00,
  "taxaJuros": 1.5,
  "valorTotal": 9000.00,
  "valorJuros": 500.00,
  "aprovada": false,
  "observacoes": "Simulação de parcelamento em 12 vezes",
  "dataCriacao": "2025-10-09T14:00:00",
  "dataAtualizacao": "2025-10-09T14:00:00"
}
```

---

### 12. Get Simulations by Declaration

**Endpoint:** `GET /api/simulacoes/declaracao/{declaracaoId}`

**Headers:**
```
Authorization: Bearer <your-token>
```

**Example:** `GET /api/simulacoes/declaracao/1`

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "numeroParcelas": 12,
    "valorParcela": 750.00,
    "taxaJuros": 1.5,
    "valorTotal": 9000.00,
    "valorJuros": 500.00,
    "aprovada": false,
    "observacoes": "Simulação de parcelamento em 12 vezes",
    "dataCriacao": "2025-10-09T14:00:00",
    "dataAtualizacao": "2025-10-09T14:00:00"
  }
]
```

---

### 13. Approve Simulation

**Endpoint:** `PUT /api/simulacoes/{id}`

**Headers:**
```
Authorization: Bearer <your-token>
Content-Type: application/json
```

**Request:**
```json
{
  "numeroParcelas": 12,
  "valorParcela": 750.00,
  "taxaJuros": 1.5,
  "valorTotal": 9000.00,
  "valorJuros": 500.00,
  "aprovada": true,
  "observacoes": "Simulação aprovada pelo cliente"
}
```

**Response (200 OK):**
```json
{
  "id": 1,
  "numeroParcelas": 12,
  "valorParcela": 750.00,
  "taxaJuros": 1.5,
  "valorTotal": 9000.00,
  "valorJuros": 500.00,
  "aprovada": true,
  "observacoes": "Simulação aprovada pelo cliente",
  "dataCriacao": "2025-10-09T14:00:00",
  "dataAtualizacao": "2025-10-09T15:00:00"
}
```

---

## Error Responses

### 400 Bad Request
```json
{
  "timestamp": "2025-10-09T15:00:00",
  "message": "Validation failed",
  "errors": {
    "email": "must be a well-formed email address",
    "senha": "size must be between 6 and 40"
  },
  "status": 400
}
```

### 401 Unauthorized
```json
{
  "path": "/api/usuarios",
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "status": 401
}
```

### 404 Not Found
```json
{
  "timestamp": "2025-10-09T15:00:00",
  "message": "Usuario not found with id: 999",
  "status": 404
}
```

---

## Testing with cURL

### Example: Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "joao.silva@example.com",
    "senha": "senha123"
  }'
```

### Example: Get All Companies (with authentication)
```bash
curl -X GET http://localhost:8080/api/empresas \
  -H "Authorization: Bearer <your-jwt-token-here>"
```

---

## Testing with Postman

1. Import the base URL: `http://localhost:8080`
2. For authentication endpoints, set Content-Type to `application/json`
3. For protected endpoints:
   - Go to Authorization tab
   - Select "Bearer Token"
   - Paste your JWT token

---

## Common Status Codes

- **200 OK** - Successful GET, PUT requests
- **201 Created** - Successful POST request
- **204 No Content** - Successful DELETE request
- **400 Bad Request** - Invalid request data
- **401 Unauthorized** - Missing or invalid authentication
- **404 Not Found** - Resource not found
- **500 Internal Server Error** - Server error
