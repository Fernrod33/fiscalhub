# E-mail transacional com o Amazon SES

O FiscalHub envia três tipos de e-mail: o **convite** de usuário (link de 72 h), a **recuperação de senha** (link de
30 min) e os **avisos de segurança** (senha trocada, 2º fator ativado ou desativado, papel alterado). O envio usa a
interface **SMTP** do Amazon SES na região **São Paulo (`sa-east-1`)**, sem SDK da AWS. No compose de desenvolvimento
os e-mails ficam no Mailpit (`http://localhost:8025`) e nada sai para fora.

Configuração no servidor, no `.env` (ver `.env.example`):

| Variável | Valor |
|---|---|
| `SMTP_USUARIO`, `SMTP_SENHA` | credenciais SMTP do SES (passo 4); nunca no repositório |
| `EMAIL_REMETENTE` | `FiscalHub <nao-responda@SEU_DOMINIO>`, num domínio verificado (passo 1). Padrão: `nao-responda@DOMINIO` |
| `SMTP_HOST` | padrão `email-smtp.sa-east-1.amazonaws.com` (só mude se trocar de região) |

O perfil de produção usa a porta 587 com STARTTLS obrigatório e autenticação. Os links dos e-mails usam a
`ORIGEM_PUBLICA` (`https://DOMINIO`).

## 1. Verificar o domínio no SES

No console da AWS, região **South America (São Paulo)**: *Amazon SES → Configuration → Identities → Create identity →
Domain*. Informe o domínio do remetente e deixe marcado **Easy DKIM** com chave **RSA 2048 bits**.

O SES mostra três registros **CNAME** de DKIM (`<token>._domainkey.SEU_DOMINIO` → `<token>.dkim.amazonses.com`).
Cadastre os três no DNS do domínio. A identidade fica *Verified* quando o SES os enxergar (minutos a horas).

## 2. SPF (domínio MAIL FROM próprio)

Sem configuração, o envelope sai como `amazonses.com` e o SPF não alinha com o seu domínio; o DMARC passa pelo DKIM.
Para alinhar também o SPF, use um **MAIL FROM** próprio: na identidade, *Custom MAIL FROM domain → Edit*, por exemplo
`envio.SEU_DOMINIO`, com *Behavior on MX failure = Use default MAIL FROM domain*. Cadastre:

| Nome | Tipo | Valor |
|---|---|---|
| `envio.SEU_DOMINIO` | MX | `10 feedback-smtp.sa-east-1.amazonses.com` |
| `envio.SEU_DOMINIO` | TXT | `"v=spf1 include:amazonses.com ~all"` |

Cada nome tem **um** registro SPF só. Se o domínio principal já envia e-mail por outro serviço, não crie um segundo SPF
nele: o do MAIL FROM fica no subdomínio.

## 3. DMARC

Comece só monitorando:

| Nome | Tipo | Valor |
|---|---|---|
| `_dmarc.SEU_DOMINIO` | TXT | `"v=DMARC1; p=none; rua=mailto:dmarc@SEU_DOMINIO"` |

Acompanhe os relatórios agregados enviados ao `rua`. **Depois de 2 semanas sem falha** de DKIM ou SPF nas mensagens
legítimas, endureça para `p=quarantine`. Se o domínio já tem um DMARC, ajuste o existente em vez de criar outro.

## 4. Credenciais SMTP

*Amazon SES → SMTP settings → Create SMTP credentials*. O console cria um usuário IAM só com permissão de envio
(`ses:SendRawEmail`) e mostra o **usuário e a senha SMTP uma única vez**. Elas valem só para a região em que foram
criadas e não são a chave de acesso da conta AWS.

Grave as duas no `.env` do servidor (`SMTP_USUARIO`, `SMTP_SENHA`, com `chmod 600`) e rode
`docker compose up -d --wait`. Para trocar, crie credenciais novas, atualize o `.env` e só então apague as antigas no
IAM.

## 5. Sair do sandbox

Contas novas ficam no **sandbox**: só enviam para endereços verificados e com limite baixo de envio diário e por
segundo. Peça a saída em *Amazon SES → Account dashboard → Request production access*:

- tipo de e-mail: **Transactional**;
- site: `https://DOMINIO`;
- uso: convite de usuários de escritórios de contabilidade e advocacia, recuperação de senha e avisos de segurança da
  conta, só para usuários cadastrados pelo próprio escritório; sem lista de marketing;
- volume: estimativa mensal (poucas dezenas por escritório);
- bounces e reclamações: lista de supressão da conta ligada (passo 6) e endereços de usuários desativados deixam de
  receber e-mail.

Enquanto o pedido não sai, verifique os endereços de teste em *Identities → Create identity → Email address*.

## 6. Lista de supressão da conta

*Amazon SES → Configuration → Suppression list*: confira que a lista de supressão **da conta** está ligada para
**Bounces** e **Complaints**. Com ela, o SES não tenta de novo um endereço que voltou ou reclamou. Pela linha de comando:

```bash
aws sesv2 put-account-suppression-attributes --suppressed-reasons BOUNCE COMPLAINT --region sa-east-1
```

## 7. Conferir

1. No FiscalHub, em *Esqueci minha senha*, peça o link para um endereço seu.
2. O e-mail chega com remetente do seu domínio; nos cabeçalhos, `dkim=pass`, `spf=pass` (com MAIL FROM próprio) e
   `dmarc=pass`.
3. Na *Auditoria* do escritório aparece **E-mail enviado ao usuário** com `resultado: SUCESSO`. Com
   `resultado: FALHA` e `tentativas: 3`, o SMTP recusou as três tentativas: confira o `.env` e o log do backend
   (`docker compose logs backend | grep E-mail`).

## Débito

- **Bounces e reclamações por SNS:** hoje vale só a lista de supressão da conta. O passo seguinte é publicar os eventos
  de bounce e complaint do SES num tópico SNS e marcar o usuário com e-mail inválido no FiscalHub (avisar o ADMIN).
- **Fila persistente:** a mensagem fica na memória entre o pedido e o envio (o token do link não vai em claro para o
  banco). Se o backend reiniciar nesse intervalo, o usuário pede de novo (reenviar convite, nova recuperação).
