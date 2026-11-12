# LGPD e sigilo fiscal

O escritório é o **controlador** dos dados dos seus clientes e usuários; o FiscalHub é o **operador**.

| Documento | Conteúdo |
|---|---|
| [inventario.md](inventario.md) | dados pessoais por tipo de documento, onde ficam, por quanto tempo e para quê |
| [Termos de Uso](../../frontend/public/legal/termos-de-uso.md) | **modelo, precisa de revisão jurídica**; aceito por cada usuário no primeiro acesso |
| [Política de Privacidade](../../frontend/public/legal/politica-de-privacidade.md) | **modelo, precisa de revisão jurídica**; idem |
| [acordo-tratamento-dados.md](acordo-tratamento-dados.md) | acordo de tratamento (operador), **modelo, precisa de revisão jurídica** |

## No sistema

| Requisito | Como |
|---|---|
| Aceite dos termos | no primeiro acesso e a cada versão nova (`lgpd/Termos.VERSAO`; mudar o texto = mudar a versão). Antes do aceite, a API responde 403 `/problemas/termos-pendentes` a tudo menos o próprio cadastro, o logout e `POST /api/usuarios/me/termos`. Auditado (`TERMOS_ACEITAR`) |
| Encarregado (DPO) | nome e e-mail do encarregado do escritório em Segurança (ADMIN edita; todos veem): `PUT /api/escritorio/seguranca` |
| Portabilidade | Cliente → **Exportar dados** (só ADMIN, auditado): ZIP com os arquivos originais, os relatórios emitidos e uma tabela JSON por tabela do banco |
| Exclusão | Clientes → excluir (só ADMIN): aviso, CNPJ digitado, prazo de arrependimento de 7 dias (`fiscalhub.lgpd.prazo-exclusao-dias`) com cancelamento; o job (`fiscalhub.lgpd.cron`, de hora em hora) remove os arquivos em todas as versões e as linhas do banco numa transação. A auditoria guarda só ids e registra a conclusão sem nome nem CNPJ |
| Minimização nos logs | CPF, CNPJ e valores mascarados na mensagem e no stack trace (texto e JSON); teste em `LgpdTest` |
| Criptografia em repouso | MinIO com SSE-S3; segredo do 2º fator com AES-GCM; cópias de segurança com `age`; o disco do servidor cifrado (LUKS ou o equivalente do provedor), ver `docs/operacao/` |
| Retenção no storage | versões antigas de arquivo (trocado ou excluído pelo usuário) expiram em 30 dias (regra do bucket) |

## Pedido de um titular

1. O titular procura o escritório (controlador). O escritório confere a identidade.
2. **Acesso ou portabilidade:** exportar o cliente e entregar o que for do titular.
3. **Eliminação:** se o dado é de um cliente que deixa de ser atendido, excluir o cliente. Dado de terceiro dentro de um
   arquivo fiscal (ex.: sócio na ECF) só some com o arquivo: excluir o documento ou o cliente; avaliar com o jurídico a
   obrigação de guarda fiscal antes.
4. Registrar o atendimento.

## Pendências (exigem ação fora do código)

- Revisão jurídica dos três textos e definição dos trechos entre colchetes (foro, suboperadores, prazos).
- Prazo de retenção da auditoria (hoje indefinido).
- Encarregado do FiscalHub (operador).
