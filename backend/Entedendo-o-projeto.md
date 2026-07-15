Pesquisado base de código para "Gere uma guia para usuários que não possuem nenhum conhecimento prévio de back-end e apis para a fim de entenderem a arquitetura, a função de cada camada da api e assim por diante", 8 resultados

# Guia Completo para Iniciantes - Fiscal Hub Backend

## 📚 Índice
1. O que é uma API?
2. Arquitetura do Projeto
3. Camadas da Aplicação
4. Como Funciona o Fluxo de Dados
5. Conceitos Importantes
6. Exemplos Práticos

---

## 🤔 O que é uma API?

**API** significa "Interface de Programação de Aplicações". Pense nela como um **garçom em um restaurante**:

- 👤 **Você (Cliente)** → Aplicativo/Site que você usa
- 📋 **Garçom (API)** → Intermediário que leva pedidos e traz respostas
- 👨‍🍳 **Cozinha (Servidor/Backend)** → Onde os dados são processados

**Exemplo Real**: Quando você faz login em um aplicativo:
1. Você digita email e senha (pedido)
2. A API leva essas informações ao servidor (garçom leva o pedido)
3. O servidor verifica se está correto (cozinha prepara)
4. A API retorna "Login bem-sucedido" ou "Senha incorreta" (garçom traz a resposta)

---

## 🏗️ Arquitetura do Projeto

O **Fiscal Hub Backend** segue uma arquitetura em camadas, como um prédio de 6 andares:

```
┌─────────────────────────────────────┐
│  6º Andar: CLIENTE (Aplicativo/Web) │  ← O usuário final
├─────────────────────────────────────┤
│  5º Andar: CONTROLLER               │  ← Recebe requisições
├─────────────────────────────────────┤
│  4º Andar: DTO (Data Transfer)      │  ← Valida os dados
├─────────────────────────────────────┤
│  3º Andar: SERVICE (Lógica)         │  ← Processa regras de negócio
├─────────────────────────────────────┤
│  2º Andar: REPOSITORY (Acesso BD)   │  ← Conversa com o banco
├─────────────────────────────────────┤
│  1º Andar: ENTITY (Modelo)          │  ← Representa as tabelas
├─────────────────────────────────────┤
│  Térreo: BANCO DE DADOS             │  ← Armazena tudo
└─────────────────────────────────────┘
```

---

## 🔍 Camadas da Aplicação

### 1️⃣ **ENTITY (Entidade)** - O que são os dados?

**Localização**: [`src/main/java/com/fiscalhub/backend/entity/`](cci:1://d:/Repos/fiscalhubbackend/README.md:209:0-228:0)

**O que faz**: Define como os dados são organizados, como uma planta baixa.

**Analogia**: Pense em um formulário de cadastro. A Entity diz quais campos existem:

```java
// Usuario.java - Define um usuário
Usuario {
  - id (número único)
  - nome (texto)
  - email (texto)
  - senha (texto criptografado)
  - cpf (texto)
  - telefone (texto)
  - ativo (sim/não)
}
```

**Entidades do projeto**:
- [`Usuario`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:33:0-39:0) - Pessoas que usam o sistema
- [`Empresa`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:41:0-48:0) - Empresas cadastradas
- [`Declaracao`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:50:0-57:0) - Declarações de impostos
- [`SimulacaoParcelamento`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:59:0-66:0) - Simulações de parcelamento

---

### 2️⃣ **REPOSITORY (Repositório)** - Como buscar/salvar dados?

**Localização**: [`src/main/java/com/fiscalhub/backend/repository/`](cci:1://d:/Repos/fiscalhubbackend/README.md:209:0-228:0)

**O que faz**: Responsável por conversar diretamente com o banco de dados.

**Analogia**: É como um **bibliotecário** que sabe exatamente onde encontrar cada livro e como guardá-lo.

**Operações comuns**:
- `findAll()` → Buscar todos os registros
- `findById(id)` → Buscar por ID específico
- `save(entity)` → Salvar ou atualizar
- `delete(entity)` → Deletar registro

**Exemplo prático**:
```java
// Buscar usuário por email
Usuario usuario = usuarioRepository.findByEmail("joao@email.com");
```

---

### 3️⃣ **SERVICE (Serviço)** - Qual a regra de negócio?

**Localização**: [`src/main/java/com/fiscalhub/backend/service/`](cci:1://d:/Repos/fiscalhubbackend/README.md:209:0-228:0)

**O que faz**: Contém toda a **lógica de negócio** - as regras do que pode ou não fazer.

**Analogia**: É o **gerente** que toma decisões:
- "Esse CPF já existe? Não pode cadastrar!"
- "A senha precisa ter pelo menos 6 caracteres"
- "Só usuários ativos podem fazer login"

**Responsabilidades**:
- ✅ Validar dados
- ✅ Aplicar regras de negócio
- ✅ Criptografar senhas
- ✅ Verificar permissões
- ✅ Orquestrar chamadas ao Repository

**Exemplo prático**:
```java
// UsuarioService - Criar novo usuário
public Usuario create(Usuario usuario) {
    // Regra: Email já existe?
    if (existsByEmail(usuario.getEmail())) {
        throw new Exception("Email já cadastrado!");
    }

    // Regra: Criptografar senha
    String senhaCriptografada = passwordEncoder.encode(usuario.getSenha());
    usuario.setSenha(senhaCriptografada);

    // Salvar no banco
    return usuarioRepository.save(usuario);
}
```

---

### 4️⃣ **DTO (Data Transfer Object)** - Como transportar dados?

**Localização**: [`src/main/java/com/fiscalhub/backend/dto/`](cci:1://d:/Repos/fiscalhubbackend/README.md:209:0-228:0)

**O que faz**: Define o **formato dos dados** que entram e saem da API.

**Analogia**: São os **pacotes padronizados** para envio - você não envia uma geladeira sem embalar!

**Tipos de DTO no projeto**:

1. **Requests** (Dados que chegam):
   - [`LoginRequest`](cci:1://d:/Repos/fiscalhubbackend/src/main/java/com/fiscalhub/backend/controller/AuthController.java:36:0-47:0) - Email e senha para login
   - [`SignupRequest`](cci:1://d:/Repos/fiscalhubbackend/src/main/java/com/fiscalhub/backend/controller/AuthController.java:50:0-67:0) - Dados para cadastro

2. **Responses** (Dados que saem):
   - [`JwtResponse`](cci:1://d:/Repos/fiscalhubbackend/src/main/java/com/fiscalhub/backend/controller/AuthController.java:45:0-45:0) - Token de autenticação
   - [`MessageResponse`](cci:1://d:/Repos/fiscalhubbackend/src/main/java/com/fiscalhub/backend/controller/AuthController.java:52:0-52:0) - Mensagens de sucesso/erro

**Exemplo**:
```json
// LoginRequest - O que você envia
{
  "email": "joao@email.com",
  "senha": "123456"
}

// JwtResponse - O que você recebe
{
  "token": "eyJhbGciOiJIUzI1NiIsInR...",
  "type": "Bearer",
  "id": 1,
  "email": "joao@email.com",
  "nome": "João Silva"
}
```

---

### 5️⃣ **CONTROLLER (Controlador)** - Quem recebe as requisições?

**Localização**: [`src/main/java/com/fiscalhub/backend/controller/`](cci:1://d:/Repos/fiscalhubbackend/README.md:209:0-228:0)

**O que faz**: É a **porta de entrada** da API - recebe todas as requisições HTTP.

**Analogia**: É a **recepcionista** que atende ligações e direciona para o departamento certo.

**Exemplo real** - `AuthController`:

```java
@PostMapping("/login")  // ← Rota: POST /api/auth/login
public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
    // 1. Recebe email e senha
    // 2. Chama o Service para validar
    // 3. Gera token JWT
    // 4. Retorna resposta
}
```

**Controllers do projeto**:
- `AuthController` - Login e cadastro
- `UsuarioController` - Gerenciar usuários
- `EmpresaController` - Gerenciar empresas
- [`DeclaracaoController`](cci:1://d:/Repos/fiscalhubbackend/src/main/java/com/fiscalhub/backend/controller/DeclaracaoController.java:23:0-46:0) - Gerenciar declarações
- `SimulacaoParcelamentoController` - Gerenciar simulações

---

### 6️⃣ **SECURITY (Segurança)** - Como proteger os dados?

**Localização**: [`src/main/java/com/fiscalhub/backend/security/`](cci:1://d:/Repos/fiscalhubbackend/README.md:209:0-228:0)

**O que faz**: Garante que apenas usuários autenticados acessem dados protegidos.

**Componentes principais**:

1. **JwtUtils** - Gera e valida tokens
2. **JwtAuthenticationFilter** - Verifica token em cada requisição
3. **SecurityConfig** - Define quais rotas são públicas/protegidas

**Como funciona**:

```
┌──────────────┐
│ 1. Login     │  Email + Senha
└──────┬───────┘
       ↓
┌──────────────┐
│ 2. Validação │  Confere no banco
└──────┬───────┘
       ↓
┌──────────────┐
│ 3. Gera JWT  │  Token único e seguro
└──────┬───────┘
       ↓
┌──────────────┐
│ 4. Uso Token │  Usa em todas requisições
└──────────────┘
```

**Rotas públicas** (não precisa login):
- `/api/auth/login` - Fazer login
- `/api/auth/signup` - Cadastrar-se

**Rotas protegidas** (precisa token):
- [`/api/usuarios`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:139:0-146:0) - Gerenciar usuários
- [`/api/empresas`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:148:0-154:0) - Gerenciar empresas
- [`/api/declaracoes`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:156:0-164:0) - Gerenciar declarações
- [`/api/simulacoes`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:166:0-172:0) - Gerenciar simulações

---

## 🔄 Como Funciona o Fluxo de Dados

### Exemplo Completo: Criar uma Nova Empresa

```
👤 USUÁRIO
   ↓ Envia requisição POST com dados da empresa

📡 CONTROLLER (EmpresaController)
   ↓ Recebe dados e valida JWT token

📋 DTO (EmpresaDTO)
   ↓ Valida campos obrigatórios

⚙️ SERVICE (EmpresaService)
   ↓ Aplica regras de negócio
   ↓ - CNPJ já existe?
   ↓ - Usuário existe?

💾 REPOSITORY (EmpresaRepository)
   ↓ Salva no banco de dados

🗄️ BANCO DE DADOS (PostgreSQL)
   ↓ Dados armazenados

✅ RESPOSTA
   ↓ Retorna empresa criada com ID

👤 USUÁRIO
   Recebe confirmação
```

### Código Visual do Fluxo:

```java
// 1. CONTROLLER recebe
@PostMapping("/api/empresas")
public ResponseEntity<Empresa> create(@RequestBody EmpresaDTO dto) {

    // 2. SERVICE processa
    Empresa empresa = empresaService.create(dto);

    // 3. REPOSITORY salva
    // (feito internamente pelo service)

    // 4. Retorna resposta
    return ResponseEntity.ok(empresa);
}
```

---

## 💡 Conceitos Importantes

### 🔐 **Autenticação vs Autorização**

**Autenticação** = "Quem você é?"
- Login com email e senha
- Gera um **token JWT**

**Autorização** = "O que você pode fazer?"
- Verifica permissões
- Valida token em cada requisição

### 🎫 **O que é JWT (JSON Web Token)?**

É como um **ingresso de cinema**:
- ✅ Tem seu nome e informações
- ✅ Tem data de validade
- ✅ É único e seguro
- ✅ Precisa dele para entrar (acessar rotas protegidas)

**Formato**:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

### 📡 **Métodos HTTP (Verbos)**

- **GET** → Buscar dados (não modifica nada)
- **POST** → Criar novo registro
- **PUT** → Atualizar registro completo
- **DELETE** → Deletar registro

### ✅ **Códigos de Status HTTP**

- [`200 OK`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:561:0-561:0) → Sucesso
- [`201 Created`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:562:0-562:0) → Criado com sucesso
- [`204 No Content`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:563:0-563:0) → Deletado com sucesso
- [`400 Bad Request`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:490:0-500:0) → Dados inválidos
- [`401 Unauthorized`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:502:0-509:0) → Não autenticado
- [`404 Not Found`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:511:0-518:0) → Recurso não existe
- [`500 Internal Server Error`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:567:0-567:0) → Erro no servidor

---

## 🎯 Exemplos Práticos

### Exemplo 1: Cadastrar Usuário

**Passo a passo**:

1. **Você envia** (POST [`/api/auth/signup`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:20:0-43:0)):
```json
{
  "nome": "Maria Santos",
  "email": "maria@email.com",
  "senha": "senha123",
  "cpf": "12345678901",
  "telefone": "11999999999"
}
```

2. **O sistema faz**:
   - `AuthController` recebe
   - Valida se email já existe
   - Criptografa senha
   - Salva no banco

3. **Você recebe**:
```json
{
  "message": "User registered successfully!"
}
```

---

### Exemplo 2: Fazer Login

1. **Você envia** (POST [`/api/auth/login`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:47:0-65:0)):
```json
{
  "email": "maria@email.com",
  "senha": "senha123"
}
```

2. **O sistema faz**:
   - Verifica email e senha
   - Gera token JWT
   - Retorna token

3. **Você recebe**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR...",
  "type": "Bearer",
  "id": 1,
  "email": "maria@email.com",
  "nome": "Maria Santos"
}
```

4. **Guarde esse token!** Você precisará dele para todas as próximas requisições.

---

### Exemplo 3: Criar Empresa (com autenticação)

1. **Você envia** (POST [`/api/empresas?usuarioId=1`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:171:0-213:0)):

**Headers**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR...
Content-Type: application/json
```

**Body**:
```json
{
  "razaoSocial": "Empresa ABC Ltda",
  "nomeFantasia": "ABC Comércio",
  "cnpj": "12345678000190",
  "inscricaoEstadual": "123456789",
  "endereco": "Rua Principal, 123",
  "cidade": "São Paulo",
  "estado": "SP",
  "cep": "01234567"
}
```

2. **O sistema faz**:
   - Valida token JWT
   - Verifica se usuário existe
   - Verifica se CNPJ já existe
   - Salva empresa

3. **Você recebe**:
```json
{
  "id": 1,
  "razaoSocial": "Empresa ABC Ltda",
  "nomeFantasia": "ABC Comércio",
  "cnpj": "12345678000190",
  "ativa": true
}
```

---

## 🛠️ Como Testar a API

### Opção 1: Usando Postman (Recomendado para iniciantes)

1. Baixe o [Postman](https://www.postman.com/downloads/)
2. Crie uma nova requisição
3. Configure:
   - **Método**: POST, GET, etc.
   - **URL**: `http://localhost:8080/api/auth/login`
   - **Body**: Cole o JSON
   - **Headers**: Adicione o token (se necessário)

Veja mais em: [`API_EXAMPLES.md - Testing with Postman`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:544:0-552:0)

---

### Opção 2: Usando cURL (Terminal)

```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@email.com","senha":"senha123"}'

# Buscar empresas (com token)
curl -X GET http://localhost:8080/api/empresas \
  -H "Authorization: Bearer SEU_TOKEN_AQUI"
```

Veja mais em: [`API_EXAMPLES.md - Testing with cURL`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:526:0-541:0)

---

## 📚 Documentação Adicional

- [`README.md`](cci:1://d:/Repos/fiscalhubbackend/README.md:0:0-0:0) - Instruções gerais e setup
- [`API_EXAMPLES.md`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:0:0-0:0) - Exemplos detalhados de cada endpoint
- [`IMPLEMENTATION_SUMMARY.md`](cci:1://d:/Repos/fiscalhubbackend/IMPLEMENTATION_SUMMARY.md:0:0-0:0) - Detalhes técnicos da implementação

---

## 🎓 Resumo para Memorizar

1. **ENTITY** = Define os dados (como formulário)
2. **REPOSITORY** = Busca/salva no banco (bibliotecário)
3. **SERVICE** = Aplica regras de negócio (gerente)
4. **DTO** = Formato dos dados (pacote)
5. **CONTROLLER** = Recebe requisições (recepcionista)
6. **SECURITY** = Protege o sistema (segurança)

**Fluxo básico**: Cliente → Controller → Service → Repository → Banco de Dados → Resposta

---

Espero que este guia tenha ajudado! Para dúvidas específicas sobre endpoints, consulte o [`API_EXAMPLES.md`](cci:1://d:/Repos/fiscalhubbackend/API_EXAMPLES.md:0:0-0:0). 🚀