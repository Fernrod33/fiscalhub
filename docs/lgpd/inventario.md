# Inventário de dados pessoais

Levantamento da Fase 7 (novembro/2026). Papéis: o **escritório** é o **controlador** dos dados dos seus clientes e dos
seus usuários; o **FiscalHub** é o **operador** (trata em nome do escritório, só para prestar o serviço).

Os dados dos clientes são, na maior parte, de **pessoas jurídicas** (fora da LGPD). Os dados pessoais aparecem nos
arquivos fiscais como dados de **terceiros**: sócios, administradores, contador, responsáveis pelo preenchimento e
signatários, e pessoas físicas que compram ou vendem para o cliente. Além disso, tudo é protegido pelo **sigilo fiscal**
(CTN, art. 198) e profissional.

## Onde ficam

| Local | O que guarda | Proteção |
|---|---|---|
| MinIO (bucket `fiscalhub-documentos`) | o arquivo original de cada importação (inteiro, com todos os registros) e o PDF e o Excel de cada relatório emitido | SSE-S3 (`MINIO_KMS_SECRET_KEY`), sem acesso público, só pela API com controle de acesso; versões antigas expiram em 30 dias |
| PostgreSQL | o que o sistema leu dos arquivos (só os registros usados), o cadastro de clientes e usuários, sessões, auditoria | disco do servidor (cifrado: ver runbook); acesso só pela rede do compose |
| Cópias de segurança | `pg_dump` + espelho do bucket | cifradas com `age`; retenção de até 6 meses |
| Logs do contêiner | eventos técnicos, ids, IP | CPF, CNPJ e valores mascarados (`MascaraLog`); rotação de 5 × 20 MB |

## Por tipo de documento importado

"No banco" = o que o processador grava; o resto fica só no arquivo original do storage.

| Documento | Dados pessoais no arquivo | No banco |
|---|---|---|
| ECD (SPED Contábil) | `0000`: nome e CNPJ da empresa; `J930`: signatários (nome, CPF, qualificação, CRC do contador, e-mail, telefone); `J935`: auditor independente; nomes de contas podem citar sócios ("Empréstimo de sócio – Fulano") | nome e CNPJ da empresa, plano de contas (com os nomes das contas), saldos e demonstrações. `J930` não é lido |
| ECF | `0000`/`0030`: endereço, telefone e e-mail da empresa; `0930`: signatários; **`Y600`: sócios (CPF/CNPJ, nome, qualificação, participação, remuneração, lucros e dividendos)**; `Y611`/`Y612`: rendimentos de dirigentes | IRPJ/CSLL, e-Lalur/e-Lacs, DRE declarada. `0930` e `Y` não são lidos |
| EFD-Contribuições | `0100`: contabilista (nome, CPF, CRC, endereço, telefone, e-mail); `0150`: participantes, que podem ser pessoas físicas (nome, CPF) | só os totais do bloco M e os saldos de crédito |
| EFD ICMS/IPI | `0005`: contato; `0100`: contabilista; `0150`: participantes (pessoas físicas com CPF); documentos com o participante | só a apuração (blocos E), obrigações e o faturamento (C190, sem participante) |
| Balancete (`.xlsx`/`.csv`) | nomes de contas podem citar pessoas | contas e saldos |
| DCTF / DCTFWeb | responsável pelo preenchimento (nome, CPF, telefone, e-mail) | débitos declarados e vinculações |
| Relatório de Situação Fiscal (e-CAC) | pode trazer responsável e corresponsáveis (nome, CPF) | débitos, pendências e processos (sem os responsáveis) |
| Extrato do Regularize / CAPAG | inscrições podem listar corresponsáveis (nome, CPF) | inscrições, valores, situação |
| DARF, relatório de pagamentos, GR-PR | contribuinte (CNPJ; CPF se pessoa física) | pagamentos |
| Conta corrente do ICMS (PR), RTI | inscrição estadual, CNPJ; CDAs podem citar corresponsáveis | débitos, CDAs, saldos credores |

## Dos usuários e do escritório

| Dado | Onde | Para quê | Retenção |
|---|---|---|---|
| Nome, e-mail (login), CPF e telefone (opcionais), papel | `usuario` | identificar e autorizar | enquanto o usuário existir; saída = desativação |
| Senha | `usuario.senha` (BCrypt) | autenticar | idem |
| Segredo do 2º fator e códigos de recuperação | `usuario.totp_segredo` (AES-GCM), `codigo_recuperacao` (SHA-256) | 2º fator | até desativar |
| IP e navegador (User-Agent) de cada sessão | `sessao` | listar e encerrar sessões | 30 dias depois de vencida ou encerrada |
| Ações (quem, quando, IP, ids dos recursos); e-mail tentado num login que falhou | `auditoria_evento` (só inserção) | segurança e prestação de contas | **[A DEFINIR com o jurídico]**: hoje indefinida |
| Aceite dos termos (versão e data) | `usuario.termos_versao`, `termos_aceitos_em` | prova do aceite | enquanto o usuário existir |
| Encarregado do escritório (nome, e-mail) | `escritorio` | contato do titular | enquanto o escritório existir |

## Finalidade e minimização

- O sistema só lê os registros de que precisa (tabelas acima); signatários, sócios (`Y600`) e participantes **não vão
  para o banco**. O arquivo original fica guardado inteiro porque é a prova da origem de cada número e permite
  reprocessar com uma versão nova do leitor.
- Logs não levam CPF, CNPJ completo nem valores (`operacao/MascaraLog`, testado em `LgpdTest`).
- A exportação e a exclusão do cliente cobrem as duas camadas (arquivos e banco) e as tabelas novas entram sozinhas
  (`lgpd/GrafoCliente`, pelas chaves estrangeiras).

## Ciclo de vida

1. **Entrada:** upload pelo escritório (só usuários do escritório; tipo pelo conteúdo).
2. **Uso:** leitura, conciliação, relatórios — sempre dentro do escritório.
3. **Exportação (portabilidade):** ZIP com tudo do cliente (`GET /api/clientes/{id}/exportacao`, só ADMIN, auditado).
4. **Exclusão:** pedido com o CNPJ digitado, prazo de arrependimento de 7 dias (`fiscalhub.lgpd.prazo-exclusao-dias`),
   depois o job remove os arquivos (todas as versões) e as linhas do banco; a auditoria fica só com ids.
5. **Cópias de segurança:** o dado excluído some delas quando a última cópia que o contém expira (até 6 meses).
