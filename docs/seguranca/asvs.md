# OWASP ASVS 4.0.3 — nível 2

Checklist do FiscalHub contra o ASVS nível 2, feito na Fase 7 (novembro/2026). Cada linha diz se o requisito está
**atendido**, **parcial** ou **pendente**, onde está e o que falta. Requisitos que não se aplicam (sem o recurso no
sistema) estão marcados como **n/a**.

Contexto: dados fiscais e sigilosos (CTN, art. 198, e LGPD); um servidor com Docker Compose atrás do Caddy com TLS.

## V1 — Arquitetura

| Item | Situação | Onde / o que falta |
|---|---|---|
| 1.1.x Ciclo de desenvolvimento seguro | parcial | CI com CodeQL, gitleaks, Dependabot, npm audit e Dependency-Check (`.github/workflows`); revisão de segurança a cada fase. Falta modelagem de ameaças formal |
| 1.2.x Autenticação entre componentes | atendido | Banco e MinIO só na rede do compose; o backend usa um usuário do MinIO restrito ao bucket (`deploy/minio-init.sh`), não o administrador |
| 1.4.x Controle de acesso centralizado | atendido | `SecurityConfig` (rotas) + `AcessoRecursoService` (escopo por escritório; recurso de outro escritório = 404) + `@PreAuthorize` por papel |
| 1.5.x Entrada e saída | atendido | Bean Validation nos DTOs, ProblemDetail único (`GlobalExceptionHandler`), Thymeleaf com escape nos relatórios |
| 1.6.x Criptografia | atendido | Chaves só em variáveis de ambiente (`JWT_SECRET`, `CHAVE_DADOS`, `MINIO_KMS_SECRET_KEY`); nada no código, nas imagens ou no compose |
| 1.14.x Configuração | atendido | Perfil `prod` + `.env.example`; imagens sem segredos, usuário sem root |

## V2 — Autenticação

| Item | Situação | Onde / o que falta |
|---|---|---|
| 2.1.1 Mínimo de 12 caracteres | atendido | `security/SenhaForte` (cadastro, usuário novo, troca de senha) |
| 2.1.2 Até 64+ caracteres | parcial | Até 64 caracteres e 72 bytes (limite do BCrypt). Frase-senha longa em UTF-8 pode bater nos 72 bytes antes dos 64 caracteres |
| 2.1.3–2.1.6 Sem truncar, Unicode, troca com a senha atual | atendido | `TrocaSenhaDTO` exige a senha atual |
| 2.1.7 Senha contra lista de vazadas | atendido | Lista local de ~30 mil senhas vazadas comuns com 12+ caracteres (`resources/seguranca/senhas-vazadas.txt`, SecLists, MIT), sem chamar serviço externo |
| 2.1.8 Medidor de força | pendente | A tela só orienta ("frase com palavras soltas"); sem medidor |
| 2.1.9 Sem regra de composição | atendido | Nenhuma exigência de maiúscula/símbolo (NIST 800-63B) |
| 2.1.10 Sem rotação periódica forçada | atendido | |
| 2.1.11 Colar e gerenciador de senhas | atendido | Campos `type=password` com `autocomplete` correto |
| 2.2.1 Anti-automação | atendido | Bloqueio progressivo por conta (5 falhas) e por IP (20), espera dobrando até 1 h (`security/BloqueioLogin`); limite global de 30 requisições/min por IP em `/api/auth/**` (`security/LimiteAutenticacao`); 429 com `Retry-After`; tentativas e bloqueios auditados (`LOGIN`, motivo `BLOQUEADO`) |
| 2.2.2 Sem SMS/e-mail como 2º fator | atendido | Só TOTP |
| 2.2.3 Aviso de mudança de credencial | pendente | Sem serviço de e-mail; a troca de senha e o 2º fator são auditados |
| 2.3.1 Senha inicial aleatória e de troca obrigatória | parcial | A senha provisória do ADMIN passa pela mesma política e exige troca no 1º acesso (`trocarSenhaNoProximoLogin`); falta convite por e-mail com expiração |
| 2.4.1 Armazenamento de senha | atendido | BCrypt (custo 10, `SecurityConfig`) |
| 2.5.x Recuperação | parcial | Recuperação de 2º fator por códigos de uso único; recuperação de senha esquecida é feita pelo ADMIN (senha provisória); sem fluxo por e-mail |
| 2.7.x / 2.8.x OTP | atendido | TOTP RFC 6238 (`security/Totp`), segredo de 160 bits cifrado com AES-256-GCM (`security/CifraDados`, `CHAVE_DADOS`), folga de ±1 passo, código não reutilizável (`totp_ultimo_passo`), falhas no bloqueio progressivo |
| 2.8.x Códigos de recuperação | atendido | 10 códigos de 50 bits, SHA-256 no banco (`codigo_recuperacao`), uso único, regenerar pede a senha |
| 2.9.x / 2.10.x Credenciais de serviço | atendido | Segredos por variável de ambiente; usuário do MinIO dedicado |

## V3 — Sessão

| Item | Situação | Onde / o que falta |
|---|---|---|
| 3.1.1 Token fora da URL | atendido | JWT só no cookie |
| 3.2.1–3.2.3 Token novo no login, aleatório, guardado com segurança | atendido | Sessão nova (UUID) por login e por troca de senha; cookie `httpOnly` |
| 3.3.1 Logout invalida no servidor | atendido | `POST /api/auth/logout` revoga a sessão do dispositivo (`sessao.revogada_em`) |
| 3.3.2 Expiração absoluta | atendido | 24 h (`app.jwt.expiration-ms` e `sessao.expira_em`) |
| 3.3.3 Encerrar as outras sessões | atendido | Tela Segurança: lista de sessões por dispositivo, encerrar uma ou "encerrar as outras" (`/api/usuarios/me/sessoes`); troca de senha, desativação e mudança de papel derrubam todas (versão do token) |
| 3.3.4 Lista de sessões ativas | atendido | Idem, com IP, navegador, criação e último uso |
| 3.4.1–3.4.5 Atributos do cookie | atendido | `HttpOnly`, `Secure` (perfil prod), `SameSite=Lax`, `Path=/api` (`CookieSessao`) |
| 3.5.x Tokens sem estado | atendido | JWT HS256 assinado; conferido a cada requisição contra a sessão no banco |
| 3.7.1 Reautenticação em operação sensível | parcial | Desativar o 2º fator e gerar códigos pedem a senha; trocar senha pede a atual. Exclusão de cliente pede confirmação dupla (Fase 7, LGPD), não a senha |
| Expiração por inatividade | pendente | Só a absoluta (24 h); o `ultimo_uso` já é gravado e permite implementar |

## V4 — Controle de acesso

| Item | Situação | Onde |
|---|---|---|
| 4.1.1–4.1.5 Negar por padrão, no servidor | atendido | `anyRequest().authenticated()`; escopo por escritório em todo serviço (`AcessoRecursoService`); testes `AcessoEntreEscritoriosTest` |
| 4.2.1 IDOR | atendido | Recurso de outro escritório responde 404 |
| 4.2.2 CSRF | atendido | Cookie `XSRF-TOKEN` + cabeçalho `X-XSRF-TOKEN` em toda alteração (`SessaoCookieCsrfTest`) |
| 4.3.1 Administração com 2º fator | parcial | O ADMIN pode exigir o 2º fator de todo o escritório; não é obrigatório por padrão |

## V5 — Validação, sanitização e codificação

| Item | Situação | Onde |
|---|---|---|
| 5.1.x Validação | atendido | Bean Validation; arquivos importados lidos com limites (128 KB por linha, PDF de até 20 MB e 300 páginas, logo de até 1 MB e 4000×4000) |
| 5.2.x Sanitização | atendido | HTML do relatório por Thymeleaf com escape; SVG do logo recusado; prévia em `iframe` `sandbox` vazio |
| 5.3.x Codificação de saída | atendido | React escapa; ProblemDetail em JSON |
| 5.3.4 SQL | atendido | JPA e `JdbcTemplate` com parâmetros; nenhuma concatenação de entrada |
| 5.5.x Desserialização | atendido | Só JSON (Jackson) em DTOs; nenhuma desserialização de objeto Java |

## V6 — Criptografia armazenada

| Item | Situação | Onde |
|---|---|---|
| 6.1.1–6.1.3 Dados sensíveis cifrados em repouso | parcial | MinIO com SSE-S3 (`MINIO_KMS_SECRET_KEY`) e backups cifrados com `age`; segredo do TOTP com AES-GCM. O PostgreSQL depende do disco cifrado do servidor (orientação no runbook) |
| 6.2.x Algoritmos | atendido | AES-256-GCM com nonce aleatório, HMAC-SHA256 no JWT, SHA-256, BCrypt |
| 6.3.x Aleatoriedade | atendido | `SecureRandom` |
| 6.4.x Gestão de chaves | parcial | Chaves em variáveis de ambiente do servidor (`.env` com `chmod 600`); troca descrita no runbook. Sem cofre (KMS/Vault) |

## V7 — Erros e logs

| Item | Situação | Onde |
|---|---|---|
| 7.1.1–7.1.2 Sem credenciais e dados pessoais no log | atendido | Máscara de CPF, CNPJ e valores (Fase 7, LGPD) com teste; senha e token nunca são logados |
| 7.1.3 Eventos de segurança | atendido | Auditoria imutável (`auditoria_evento`, trigger contra `UPDATE`/`DELETE`): login, falha, bloqueio, 2º fator, sessões, papéis, documentos, relatórios |
| 7.1.4 Contexto do evento | atendido | Usuário, escritório, IP real (atrás do proxy), recurso e o id de correlação no log |
| 7.3.x Proteção dos logs | parcial | Auditoria no banco só por inserção; logs do contêiner em JSON com rotação (`json-file`, 5 × 20 MB). Sem envio a um coletor externo |
| 7.4.1 Mensagem de erro genérica | atendido | Erro 500 devolve só o id de correlação |

## V8 — Proteção de dados

| Item | Situação | Onde |
|---|---|---|
| 8.1.1 Sem cache de dado sensível | atendido | `Cache-Control: no-cache, no-store` nas respostas da API (Spring Security) |
| 8.2.1 Cabeçalhos anti-cache no cliente | atendido | Idem; o `index.html` sem cache |
| 8.2.2 Nada sensível no `localStorage` | atendido | Sessão só no cookie `httpOnly` |
| 8.3.x Privacidade (LGPD) | atendido | Inventário, retenção e exclusão com prazo de arrependimento, portabilidade, minimização nos logs (`docs/lgpd/`) |
| 8.3.4 Dado sensível identificado | atendido | `docs/lgpd/inventario.md` |

## V9 — Comunicação

| Item | Situação | Onde |
|---|---|---|
| 9.1.1–9.1.3 TLS | atendido | Caddy com TLS automático (Let's Encrypt), TLS 1.2+ e HTTP/3; HSTS de 1 ano |
| 9.2.x Comunicação interna | parcial | Backend ↔ PostgreSQL e MinIO em texto na rede privada do compose (um host só). TLS interno se os serviços forem separados em máquinas |

## V10 — Código malicioso

| Item | Situação | Onde |
|---|---|---|
| 10.3.2 Integridade de dependências | atendido | `package-lock.json`, Maven Central, imagem do MinIO fixada por digest, Dependabot |
| 10.2.x Sem segredos no repositório | atendido | gitleaks no CI com o histórico inteiro |

## V11 — Lógica de negócio

| Item | Situação | Onde |
|---|---|---|
| 11.1.4 Limites de uso | atendido | Limites por usuário em relatórios, capacidade, simulações e reavaliações; por IP na autenticação |
| 11.1.5 Trilha de auditoria | atendido | Ver V7 |

## V12 — Arquivos

| Item | Situação | Onde |
|---|---|---|
| 12.1.1 Tamanho máximo | atendido | Limite único (`UPLOAD_MAX_SIZE_MB`) no proxy, no Spring e na tela |
| 12.2.1 Tipo pelo conteúdo | atendido | `DetectorTipoDocumento` (assinatura), logo por conteúdo (PNG/JPEG) |
| 12.3.x Caminho | atendido | Chave no storage gerada pelo sistema (SHA-256), nunca o nome enviado |
| 12.4.x Armazenamento fora da raiz web | atendido | MinIO, sem acesso público; download só pela API com controle de acesso |
| 12.5.x / 12.6.x | atendido | Sem execução de arquivo enviado; PDF lido sem JavaScript, anexos ou recursos externos |

## V13 — API

| Item | Situação | Onde |
|---|---|---|
| 13.1.x Configuração da API | atendido | Sem OpenAPI em produção; CORS só para a origem pública |
| 13.2.x REST | atendido | Métodos corretos, CSRF, `Content-Type` JSON |

## V14 — Configuração

| Item | Situação | Onde |
|---|---|---|
| 14.1.x Build | atendido | CI reprodutível; imagens multi-stage |
| 14.2.x Dependências | atendido | Dependabot, npm audit e Dependency-Check (alta e crítica) |
| 14.3.x Sem informação de depuração | atendido | Perfil prod sem SQL no log, sem OpenAPI, sem stack trace na resposta; `-Server` no proxy |
| 14.4.x Cabeçalhos | atendido | CSP sem `unsafe-inline` em script, `frame-ancestors 'none'`, HSTS, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options`, COOP (`deploy/Caddyfile`) |
| 14.4.3 CSP de estilo | parcial | `style-src 'unsafe-inline'`: os componentes (Recharts, Radix, motion) e a prévia do relatório usam estilo inline. Script não tem exceção |
| 14.5.x Validação de cabeçalhos | atendido | `X-Forwarded-For` só aceito de IP privado (o proxy) |

## Pendências (resumo)

1. Medidor de força de senha na tela (2.1.8).
2. E-mail transacional: aviso de troca de credencial (2.2.3), convite com senha de uso único e expiração (2.3.1),
   recuperação de senha pelo próprio usuário (2.5.x).
3. Expiração por inatividade da sessão (3.3.x), com o `ultimo_uso` já gravado.
4. Reautenticação (senha) para operações destrutivas como a exclusão de cliente (3.7.1).
5. 2º fator obrigatório para o ADMIN por padrão (4.3.1) — hoje é opção do escritório.
6. Cofre de chaves (6.4.x) e TLS interno se os serviços saírem do mesmo host (9.2.x).
7. Coletor de logs externo com retenção (7.3.x).
8. CSP de estilo sem `unsafe-inline` (14.4.3) exigiria nonce por resposta ou trocar os componentes.
