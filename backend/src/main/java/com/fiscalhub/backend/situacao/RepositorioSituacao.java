package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.fiscal.RepositorioApuracao;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.situacao.CodigosReceita.Receita;
import com.fiscalhub.backend.situacao.Extracao.Origem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Grava o que um documento de débitos, declarado ou pago leu: a escrituração do documento (tipo, período, versão do
 * layout) e, pendurados nela, débitos, parcelamentos, pendências, capacidade oficial, declarado, pago e saldos. Na
 * gravação confere com as outras fontes do mesmo cliente: a mesma dívida ou o mesmo pagamento com valor diferente vira
 * alerta, e a DCTF original em PDF e em .dec do mesmo período é a mesma declaração (confere uma contra a outra).
 */
@Repository
public class RepositorioSituacao {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final JdbcTemplate jdbc;
    private final Escrituracoes escrituracoes;
    private final RepositorioApuracao apuracoes;

    public RepositorioSituacao(JdbcTemplate jdbc, Escrituracoes escrituracoes, RepositorioApuracao apuracoes) {
        this.jdbc = jdbc;
        this.escrituracoes = escrituracoes;
        this.apuracoes = apuracoes;
    }

    public void gravar(DocumentoFiscal documento, Cliente cliente, String versao, Extracao e, ContextoProcessamento contexto) {
        long documentoId = documento.getId();
        if (!jdbc.queryForList("""
                SELECT s.simulacao_id FROM simulacao_debito s JOIN debito d ON d.id = s.debito_id
                WHERE d.documento_origem_id = ?""", Long.class, documentoId).isEmpty()) {
            throw new FalhaImportacao("DEBITO_EM_SIMULACAO", "Há simulação feita com débitos deste documento. Exclua a "
                    + "simulação antes de reprocessar ou excluir o documento.");
        }
        escrituracoes.apagarDoDocumento(documentoId);
        DocumentoTipo tipo = documento.getTipo();
        documento.setPeriodoInicio(e.inicio);
        documento.setPeriodoFim(e.fim);
        long clienteId = cliente.getId();
        long escrituracao = escrituracoes.inserir(new Escrituracoes.Nova(documentoId, clienteId, cliente.getCnpj(), tipo.name(),
                e.inicio, e.fim, versao, null, e.finalidade));
        if (tipo == DocumentoTipo.DCTF || tipo == DocumentoTipo.DCTFWEB) {
            declaracao(escrituracao, clienteId, tipo, e, contexto);
        }
        e.alertas.forEach(a -> registrar(contexto, a.severidade(), a.regra(), a.mensagem(), a.origem()));

        for (Extracao.Debito d : e.debitos) {
            divergenciaDeDebito(clienteId, escrituracao, d, contexto);
        }
        jdbc.batchUpdate("""
                INSERT INTO debito (cliente_id, documento_origem_id, escrituracao_id, referencia_origem, orgao_credor, tributo,
                    competencia, data_vencimento, valor_principal, valor_multa, valor_juros, valor_encargo, valor_total, situacao,
                    numero_inscricao, chave, data_referencia, codigo_receita, periodo, situacao_documento, numero_processo,
                    ajuizado, negociacao, pagina, linha, trecho)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.debitos.stream().map(d -> new Object[]{clienteId, documentoId, escrituracao, referencia(d.origem()),
                        d.orgao().name(), d.tributo().name(), data(d.competencia()), data(d.vencimento()), d.principal(),
                        d.multa(), d.juros(), d.encargo(), d.total(), d.situacao().name(), d.numero(), d.chave(), Date.valueOf(e.fim),
                        d.codigoReceita(), d.periodo(), d.situacaoDocumento(), d.processo(), d.ajuizado(), d.negociacao(),
                        d.origem().pagina(), d.origem().linha(), trecho(d.origem())}).toList());
        jdbc.batchUpdate("""
                INSERT INTO parcelamento (escrituracao_id, cliente_id, orgao, numero, modalidade, parcelas_pagas, parcelas_total,
                    valor_parcela, saldo, situacao, debitos, data_referencia, pagina, linha, trecho)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.parcelamentos.stream().map(p -> new Object[]{escrituracao, clienteId, p.orgao().name(), p.numero(),
                        p.modalidade(), p.parcelasPagas(), p.parcelasTotal(), p.valorParcela(), p.saldo(), p.situacao(),
                        String.join("; ", p.debitos()), Date.valueOf(e.fim), p.origem().pagina(), p.origem().linha(),
                        trecho(p.origem())}).toList());
        jdbc.batchUpdate("""
                INSERT INTO pendencia_fiscal (escrituracao_id, cliente_id, tipo, descricao, periodo, situacao, data_referencia,
                    pagina, linha, trecho) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.pendencias.stream().map(p -> new Object[]{escrituracao, clienteId, p.tipo(), p.descricao(), data(p.periodo()),
                        p.situacao(), Date.valueOf(e.fim), p.origem().pagina(), p.origem().linha(), trecho(p.origem())}).toList());
        jdbc.batchUpdate("""
                INSERT INTO capacidade_pagamento_oficial (escrituracao_id, cliente_id, fonte, classe, capacidade, passivo,
                    data_referencia, classe_consistente, pagina, linha, trecho) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.capacidades.stream().map(c -> new Object[]{escrituracao, clienteId, c.fonte(), c.classe(), c.capacidade(),
                        c.passivo(), Date.valueOf(c.referencia()), c.classeConsistente(), c.origem().pagina(),
                        c.origem().linha(), trecho(c.origem())}).toList());
        jdbc.batchUpdate("""
                INSERT INTO divida_detalhe (escrituracao_id, cliente_id, orgao, numero, data_inscricao, origem_divida, ajuizamento,
                    parcelamentos, garantia, suspensao_desde, classe_oficial, data_referencia, pagina, linha, trecho)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.detalhesDivida.stream().map(d -> new Object[]{escrituracao, clienteId, d.orgao().name(), d.numero(),
                        data(d.dataInscricao()), d.origemDivida(), d.ajuizamento(), d.parcelamentos(), d.garantia(),
                        data(d.suspensaoDesde()), d.classeOficial(), Date.valueOf(e.fim), d.origem().pagina(), d.origem().linha(),
                        trecho(d.origem())}).toList());
        jdbc.batchUpdate("""
                INSERT INTO situacao_cadastral (escrituracao_id, cliente_id, orgao, situacao, desde, data_referencia, pagina,
                    linha, trecho) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.situacoesCadastrais.stream().map(c -> new Object[]{escrituracao, clienteId, c.orgao().name(), c.situacao(),
                        data(c.desde()), Date.valueOf(e.fim), c.origem().pagina(), c.origem().linha(), trecho(c.origem())}).toList());
        jdbc.batchUpdate("""
                INSERT INTO debito_declarado (escrituracao_id, cliente_id, codigo_receita, tributo, periodo, vencimento, valor,
                    valor_pago, valor_compensado, valor_suspenso, valor_parcelado, pagina, linha, trecho)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.declarados.stream().map(d -> new Object[]{escrituracao, clienteId, d.codigoReceita(),
                        tributo(d.codigoReceita(), d.origem(), contexto), data(d.periodo()), data(d.vencimento()), d.valor(),
                        d.pago(), d.compensado(), d.suspenso(), d.parcelado(), d.origem().pagina(), d.origem().linha(),
                        trecho(d.origem())}).toList());
        divergenciaDePagamentos(clienteId, escrituracao, e.pagamentos, contexto);
        jdbc.batchUpdate("""
                INSERT INTO pagamento (escrituracao_id, cliente_id, documento, numero, chave, codigo_receita, tributo, periodo,
                    referencia, vencimento, data_pagamento, principal, multa, juros, total, parcelamento, pagina, linha, trecho)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                e.pagamentos.stream().map(p -> new Object[]{escrituracao, clienteId, p.documento(), p.numero(), chave(p),
                        p.codigoReceita(), p.parcelamento() != null ? null : tributo(p.codigoReceita(), p.origem(), contexto),
                        data(p.periodo()), p.referencia(), data(p.vencimento()), Date.valueOf(p.dataPagamento()), p.principal(),
                        p.multa(), p.juros(), p.total(), p.parcelamento(), p.origem().pagina(), p.origem().linha(),
                        trecho(p.origem())}).toList());
        if (!e.saldos.isEmpty()) {
            apuracoes.gravarSaldos(escrituracao, clienteId, e.saldos);
        }
    }

    /** O mesmo pagamento em dois documentos: DARF pelo número; GR-PR (a individual não traz número) por receita, referência e data. */
    static String chave(Extracao.Pagamento p) {
        return p.numero() != null && !"GR_PR".equals(p.documento()) ? p.documento() + "|" + p.numero().replaceAll("\\D", "")
                : p.documento() + "|" + p.codigoReceita() + "|" + p.referencia() + "|" + p.dataPagamento();
    }

    /**
     * Retificadora substitui a original do mesmo período (histórico mantido). Duas originais do mesmo período são a mesma
     * declaração em formatos diferentes (impressão em PDF e .dec): ficam as duas, conferidas débito a débito.
     */
    private void declaracao(long escrituracao, long clienteId, DocumentoTipo tipo, Extracao e, ContextoProcessamento contexto) {
        List<Map<String, Object>> outraOriginal = jdbc.queryForList("""
                SELECT id, documento_id FROM escrituracao_contabil
                WHERE cliente_id = ? AND origem = ? AND data_inicio = ? AND situacao = 'VIGENTE' AND finalidade = 'ORIGINAL'
                  AND id <> ? ORDER BY id DESC LIMIT 1""", clienteId, tipo.name(), Date.valueOf(e.inicio), escrituracao);
        if ("ORIGINAL".equals(e.finalidade) && !outraOriginal.isEmpty()) {
            long outra = (Long) outraOriginal.getFirst().get("id");
            Map<String, BigDecimal> daOutra = new HashMap<>();
            jdbc.query("SELECT codigo_receita, valor FROM debito_declarado WHERE escrituracao_id = ?",
                    rs -> { daOutra.put(rs.getString(1), rs.getBigDecimal(2)); }, outra);
            List<String> diferencas = new ArrayList<>();
            for (Extracao.Declarado d : e.declarados) {
                BigDecimal outroValor = daOutra.remove(d.codigoReceita());
                if (outroValor == null || outroValor.compareTo(d.valor()) != 0) {
                    diferencas.add(d.codigoReceita() + ": " + d.valor().toPlainString() + " aqui, "
                            + (outroValor == null ? "ausente" : outroValor.toPlainString()) + " no outro");
                }
            }
            daOutra.forEach((codigo, valor) -> diferencas.add(codigo + ": ausente aqui, " + valor.toPlainString() + " no outro"));
            Object outroDocumento = outraOriginal.getFirst().get("documento_id");
            if (diferencas.isEmpty()) {
                contexto.registrar(Severidade.INFO, null, null, "DECLARACAO_EM_DOIS_FORMATOS", "A mesma declaração já foi "
                        + "importada em outro formato (documento " + outroDocumento + ") e os débitos conferem.");
            } else {
                contexto.registrar(Severidade.ALERTA, null, null, "DECLARACAO_FORMATOS_DIVERGENTES", "A mesma declaração "
                        + "importada em outro formato (documento " + outroDocumento + ") tem débitos diferentes: "
                        + String.join("; ", diferencas) + ".");
            }
            return;
        }
        escrituracoes.retificar(contexto, escrituracao, e.finalidade, tipo == DocumentoTipo.DCTF ? "DCTF" : "DCTFWeb", null, null);
    }

    /** A mesma dívida em outro documento vigente do cliente com valor diferente (só os valores que os dois trazem). */
    private void divergenciaDeDebito(long clienteId, long escrituracao, Extracao.Debito d, ContextoProcessamento contexto) {
        jdbc.query("""
                SELECT doc.nome_original_arquivo, x.valor_principal, x.valor_multa, x.valor_juros, x.valor_encargo, x.pagina, x.linha
                FROM debito x JOIN escrituracao_contabil e ON e.id = x.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal doc ON doc.id = x.documento_origem_id
                WHERE x.cliente_id = ? AND x.chave = ? AND x.escrituracao_id <> ?""", rs -> {
            List<String> diferencas = new ArrayList<>();
            comparar("principal", d.principal(), rs.getBigDecimal(2), diferencas);
            comparar("multa", d.multa(), rs.getBigDecimal(3), diferencas);
            comparar("juros", d.juros(), rs.getBigDecimal(4), diferencas);
            comparar("encargo", d.encargo(), rs.getBigDecimal(5), diferencas);
            if (!diferencas.isEmpty()) {
                registrar(contexto, Severidade.ALERTA, "DEBITO_DIVERGENTE_ENTRE_FONTES", "O débito " + rotulo(d)
                        + " aparece com outros valores em " + rs.getString(1) + " (página " + rs.getObject(6) + ", linha "
                        + rs.getInt(7) + "): " + String.join("; ", diferencas) + ". Vale o documento mais recente.", d.origem());
            }
        }, clienteId, d.chave(), escrituracao);
    }

    /** O mesmo pagamento (mesma chave) em outro documento com total diferente (DARF × relatório consolidado). */
    private void divergenciaDePagamentos(long clienteId, long escrituracao, List<Extracao.Pagamento> pagamentos,
                                         ContextoProcessamento contexto) {
        Map<String, BigDecimal> totais = new HashMap<>();
        Map<String, Origem> origens = new HashMap<>();
        for (Extracao.Pagamento p : pagamentos) {
            totais.merge(chave(p), p.total(), BigDecimal::add);
            origens.putIfAbsent(chave(p), p.origem());
        }
        totais.forEach((chave, total) -> jdbc.query("""
                SELECT doc.nome_original_arquivo, sum(x.total) FROM pagamento x
                JOIN escrituracao_contabil e ON e.id = x.escrituracao_id AND e.situacao = 'VIGENTE'
                JOIN documento_fiscal doc ON doc.id = e.documento_id
                WHERE x.cliente_id = ? AND x.chave = ? AND x.escrituracao_id <> ? GROUP BY doc.nome_original_arquivo""", rs -> {
            if (rs.getBigDecimal(2).subtract(total).abs().compareTo(TOLERANCIA) > 0) {
                registrar(contexto, Severidade.ALERTA, "PAGAMENTO_DIVERGENTE_ENTRE_FONTES", "O pagamento " + chave
                        + " soma " + total.toPlainString() + " aqui e " + rs.getBigDecimal(2).toPlainString() + " em "
                        + rs.getString(1) + ".", origens.get(chave));
            }
        }, clienteId, chave, escrituracao));
    }

    private static void comparar(String campo, BigDecimal aqui, BigDecimal la, List<String> diferencas) {
        if (aqui != null && la != null && aqui.subtract(la).abs().compareTo(TOLERANCIA) > 0) {
            diferencas.add(campo + " " + aqui.toPlainString() + " aqui, " + la.toPlainString() + " lá");
        }
    }

    /** Tributo pelo código de receita; código fora da tabela gera alerta e fica sem tributo (fora da conciliação). */
    private static String tributo(String codigo, Origem origem, ContextoProcessamento contexto) {
        Optional<Receita> receita = CodigosReceita.receita(codigo);
        if (receita.isEmpty() || receita.get().parcelamento()) {
            if (receita.isEmpty() && codigo != null) { // sem código: DARF DCTFWeb só com o total (relatório consolidado)
                registrar(contexto, Severidade.ALERTA, "CODIGO_RECEITA_DESCONHECIDO", "O código de receita " + codigo
                        + " não está na tabela de códigos de receita: o valor fica fora da conciliação até ser cadastrado.", origem);
            }
            return null;
        }
        return receita.get().tributo().name();
    }

    private static void registrar(ContextoProcessamento contexto, Severidade severidade, String regra, String mensagem, Origem origem) {
        contexto.registrar(severidade, origem == null ? null : origem.pagina() == null ? "registro" : "página " + origem.pagina(),
                origem == null ? null : (long) origem.linha(), regra, origem == null ? mensagem : mensagem + " Trecho: " + origem.trecho());
    }

    private static String rotulo(Extracao.Debito d) {
        return Objects.requireNonNullElseGet(d.numero(), () -> d.tributo() + " " + d.competencia());
    }

    private static String referencia(Origem o) {
        return (o.pagina() == null ? "" : "página " + o.pagina() + ", ") + "linha " + o.linha();
    }

    private static String trecho(Origem o) {
        return o.trecho().length() > 1000 ? o.trecho().substring(0, 1000) : o.trecho();
    }

    private static Date data(YearMonth mes) {
        return mes == null ? null : Date.valueOf(mes.atDay(1));
    }

    private static Date data(LocalDate dia) {
        return dia == null ? null : Date.valueOf(dia);
    }
}
