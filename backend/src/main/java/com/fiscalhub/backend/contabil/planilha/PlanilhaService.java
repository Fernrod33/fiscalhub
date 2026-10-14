package com.fiscalhub.backend.contabil.planilha;

import com.fiscalhub.backend.contabil.planilha.RepositorioPlanilha.Colunas;
import com.fiscalhub.backend.contabil.planilha.RepositorioPlanilha.Mapeamento;
import com.fiscalhub.backend.contabil.referencial.MontagemReferencial;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.importacao.ProcessamentoDocumentoService;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import com.fiscalhub.backend.service.AcessoRecursoService;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assistente do balancete em planilha: prévia das primeiras linhas, mapeamento de colunas + período (salvo por cliente)
 * e de-para das contas para o plano referencial. O usuário aponta colunas e escolhe contas; nenhum valor é digitado.
 */
@Service
@Transactional
public class PlanilhaService {

    static final int LINHAS_PREVIA = 30;

    public record Previa(List<String> abas, String aba, List<List<String>> linhas, Sugestao sugestao,
                         LocalDate periodoInicio, LocalDate periodoFim) {
    }

    /** Mapeamento proposto: o salvo para o mesmo formato (reconhecido = true) ou um palpite pelos nomes do cabeçalho. */
    public record Sugestao(boolean reconhecido, int linhaCabecalho, Colunas colunas, String planoReferencial) {
    }

    public record PedidoMapeamento(String aba, @NotNull @Min(1) Integer linhaCabecalho, @NotNull @Valid Colunas colunas,
                                   @NotNull LocalDate periodoInicio, @NotNull LocalDate periodoFim,
                                   @NotNull @Pattern(regexp = "[12]", message = "Plano referencial: 1 (Lucro Real) ou 2 (Lucro Presumido).")
                                   String planoReferencial) {
    }

    public record ContaDepara(String codigo, String nome, BigDecimal saldoFinal, String codigoReferencial,
                              String descricaoReferencial, String sugestaoEcd) {
    }

    public record Depara(String planoReferencial, String tabela, List<ContaDepara> contas, long pendentes) {
    }

    public record Associacao(@NotNull String codigoConta, String codigoReferencial) {
    }

    public record ContaReferencial(String codigo, String descricao) {
    }

    private final AcessoRecursoService acesso;
    private final ArmazenamentoArquivos armazenamento;
    private final RepositorioPlanilha planilhas;
    private final ProcessamentoDocumentoService processamento;
    private final JdbcTemplate jdbc;
    private final MontagemReferencial montagem;

    public PlanilhaService(AcessoRecursoService acesso, ArmazenamentoArquivos armazenamento, RepositorioPlanilha planilhas,
                           ProcessamentoDocumentoService processamento, JdbcTemplate jdbc, MontagemReferencial montagem) {
        this.acesso = acesso;
        this.armazenamento = armazenamento;
        this.planilhas = planilhas;
        this.processamento = processamento;
        this.jdbc = jdbc;
        this.montagem = montagem;
    }

    @Transactional(readOnly = true)
    public Previa previa(long documentoId, String aba, UsuarioAutenticado ua) {
        DocumentoFiscal d = planilha(documentoId, ua);
        try (FontePlanilha fonte = FontePlanilha.abrir(armazenamento.abrir(d.getChaveArmazenamento()), d.getExtensao())) {
            List<String> abas = fonte.abas();
            String escolhida = ProcessadorPlanilha.aba(fonte, aba);
            List<List<String>> linhas = new ArrayList<>();
            fonte.ler(escolhida, (n, celulas) -> linhas.add(celulas) && n < LINHAS_PREVIA);
            return new Previa(abas, escolhida == null ? abas.get(0) : escolhida, linhas, sugerir(d, linhas),
                    d.getPeriodoInicio(), d.getPeriodoFim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (FontePlanilha.PlanilhaInvalida e) {
            throw new BusinessRuleException("Planilha ilegível: " + e.getMessage());
        }
    }

    /** Salva o mapeamento do formato para o cliente, guarda o período no documento e processa de novo. */
    public void mapear(long documentoId, PedidoMapeamento pedido, UsuarioAutenticado ua) {
        DocumentoFiscal d = planilha(documentoId, ua);
        if (d.getCliente() == null) {
            throw new BusinessRuleException("Confirme o cliente do documento antes de mapear a planilha.");
        }
        if (pedido.periodoFim().isBefore(pedido.periodoInicio())) {
            throw new BusinessRuleException("O fim do período deve ser igual ou posterior ao início.");
        }
        processamento.exigirParado(d.getId());
        List<String> cabecalho = new ArrayList<>();
        try (FontePlanilha fonte = FontePlanilha.abrir(armazenamento.abrir(d.getChaveArmazenamento()), d.getExtensao())) {
            fonte.ler(ProcessadorPlanilha.aba(fonte, pedido.aba()), (n, celulas) -> {
                if (n == pedido.linhaCabecalho()) {
                    cabecalho.addAll(celulas);
                }
                return n < pedido.linhaCabecalho();
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (cabecalho.isEmpty()) {
            throw new BusinessRuleException("A linha " + pedido.linhaCabecalho() + " da planilha está vazia.");
        }
        Colunas c = pedido.colunas();
        for (Integer coluna : new Integer[]{c.codigo(), c.descricao(), c.saldoInicial(), c.debitos(), c.creditos(), c.saldoFinal(), c.dcInicial(), c.dcFinal()}) {
            if (coluna != null && (coluna < 0 || coluna >= FontePlanilha.LIMITE_COLUNAS)) {
                throw new BusinessRuleException("Coluna fora da planilha: " + coluna + ".");
            }
        }
        planilhas.salvarMapeamento(d.getCliente().getId(), RepositorioPlanilha.assinatura(cabecalho), pedido.aba(),
                pedido.linhaCabecalho(), c, pedido.planoReferencial());
        d.setPeriodoInicio(pedido.periodoInicio());
        d.setPeriodoFim(pedido.periodoFim());
        processamento.enfileirar(d);
    }

    /** Contas analíticas do balancete com a associação atual e a sugestão vinda da ECD do cliente. */
    @Transactional(readOnly = true)
    public Depara depara(long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal d = planilha(documentoId, ua);
        Map<String, Object> esc = jdbc.queryForList("""
                SELECT id, plano_referencial, data_fim FROM escrituracao_contabil WHERE documento_id = ? AND origem = 'PLANILHA'""",
                d.getId()).stream().findFirst()
                .orElseThrow(() -> new BusinessRuleException("O balancete ainda não foi processado: mapeie as colunas primeiro."));
        String plano = (String) esc.get("plano_referencial");
        int ano = ((java.sql.Date) esc.get("data_fim")).toLocalDate().getYear();
        Map<String, String> ecd = planilhas.referencialDasEcds(d.getCliente().getId());
        List<String> tabelas = tabelas(plano, ano);
        List<ContaDepara> contas = jdbc.query("""
                SELECT c.codigo, c.nome, s.saldo_final, c.codigo_referencial,
                       (SELECT r.descricao FROM conta_referencial r WHERE r.codigo = c.codigo_referencial AND r.tabela IN (?, ?) LIMIT 1)
                FROM conta_contabil c JOIN saldo_conta s ON s.conta_id = c.id
                WHERE c.escrituracao_id = ? AND NOT c.sintetica ORDER BY c.linha""",
                (rs, i) -> new ContaDepara(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4),
                        rs.getString(5), ecd.get(rs.getString(1))),
                tabelas.get(0), tabelas.get(1), esc.get("id"));
        long pendentes = contas.stream().filter(c -> c.codigoReferencial() == null).count();
        return new Depara(plano, String.join(", ", tabelas), contas, pendentes);
    }

    /** Grava as associações do cliente (vazio = desassociar) e reprocessa o balancete com elas. */
    public void associar(long documentoId, List<Associacao> associacoes, UsuarioAutenticado ua) {
        DocumentoFiscal d = planilha(documentoId, ua);
        if (d.getCliente() == null) {
            throw new BusinessRuleException("Confirme o cliente do documento antes do de-para.");
        }
        processamento.exigirParado(d.getId());
        Depara atual = depara(documentoId, ua);
        List<String> tabelas = tabelas(atual.planoReferencial(), anoDoDocumento(d));
        for (Associacao a : associacoes) {
            String ref = a.codigoReferencial() == null || a.codigoReferencial().isBlank() ? null : a.codigoReferencial().trim();
            if (ref != null && !existeAnalitica(ref, tabelas)) {
                throw new BusinessRuleException("A conta referencial " + ref + " não existe (ou é sintética) nas tabelas " + tabelas + ".");
            }
            planilhas.salvarDepara(d.getCliente().getId(), a.codigoConta(), ref);
        }
        processamento.enfileirar(d);
    }

    /** Busca de contas referenciais analíticas (por código ou descrição) para o de-para. */
    @Transactional(readOnly = true)
    public List<ContaReferencial> buscarReferencial(String plano, int exercicio, String busca) {
        List<String> tabelas = tabelas(plano, exercicio);
        String termo = "%" + (busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT)) + "%";
        return jdbc.query("""
                SELECT codigo, descricao FROM conta_referencial
                WHERE tabela IN (?, ?) AND NOT sintetica AND (codigo LIKE ? OR lower(descricao) LIKE ?)
                ORDER BY tabela, ordem LIMIT 50""", (rs, i) -> new ContaReferencial(rs.getString(1), rs.getString(2)),
                tabelas.get(0), tabelas.get(1), termo, termo);
    }

    private DocumentoFiscal planilha(long documentoId, UsuarioAutenticado ua) {
        DocumentoFiscal d = acesso.documento(documentoId, ua);
        if (d.getTipo() != DocumentoTipo.BALANCETE) {
            throw new BusinessRuleException("O documento não é um balancete em planilha.");
        }
        return d;
    }

    private static int anoDoDocumento(DocumentoFiscal d) {
        return d.getPeriodoFim() == null ? LocalDate.now().getYear() : d.getPeriodoFim().getYear();
    }

    /** As duas tabelas (balanço e resultado) do plano para o exercício, na mesma escolha da montagem. */
    private List<String> tabelas(String plano, int exercicio) {
        Map<String, String> t = montagem.tabelas(plano, exercicio);
        if (t.size() != 2) {
            throw new BusinessRuleException("Plano referencial " + plano + " não carregado.");
        }
        return List.of(t.get("BP"), t.get("DRE"));
    }

    private boolean existeAnalitica(String codigo, List<String> tabelas) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM conta_referencial WHERE tabela IN (?, ?) "
                + "AND codigo = ? AND NOT sintetica)", Boolean.class, tabelas.get(0), tabelas.get(1), codigo));
    }

    /** Mapeamento salvo para o mesmo cabeçalho; senão, palpite pelas palavras do cabeçalho (o usuário confere). */
    private Sugestao sugerir(DocumentoFiscal d, List<List<String>> linhas) {
        String plano = d.getCliente() == null ? "1" : planilhas.planoDaUltimaEcd(d.getCliente().getId()).orElse("1");
        if (d.getCliente() != null) {
            for (Mapeamento m : planilhas.mapeamentos(d.getCliente().getId())) {
                if (m.linhaCabecalho() <= linhas.size()
                        && RepositorioPlanilha.assinatura(linhas.get(m.linhaCabecalho() - 1)).equals(m.assinatura())) {
                    return new Sugestao(true, m.linhaCabecalho(), m.colunas(), m.planoReferencial());
                }
            }
        }
        for (int i = 0; i < Math.min(15, linhas.size()); i++) {
            List<String> l = linhas.get(i).stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
            Integer codigo = coluna(l, "c[oó]d|conta");
            Integer saldoFinal = ultima(l, "atual|final|saldo");
            if (codigo != null && saldoFinal != null && l.stream().filter(c -> !c.isBlank()).count() >= 3) {
                Integer descricao = coluna(l, "descri|nome|t[ií]tulo");
                Integer inicial = coluna(l, "anterior|inicial");
                Integer dcFinal = saldoFinal + 1 < l.size() && l.get(saldoFinal + 1).matches("d/?c|natureza") ? saldoFinal + 1 : null;
                Integer dcInicial = inicial != null && inicial + 1 < l.size() && l.get(inicial + 1).matches("d/?c|natureza") ? inicial + 1 : null;
                return new Sugestao(false, i + 1, new Colunas(codigo, descricao == null ? codigo + 1 : descricao, inicial,
                        coluna(l, "d[eé]bito"), coluna(l, "cr[eé]dito"), saldoFinal, dcInicial, dcFinal), plano);
            }
        }
        return new Sugestao(false, 1, null, plano);
    }

    private static Integer coluna(List<String> cabecalho, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        for (int i = 0; i < cabecalho.size(); i++) {
            if (p.matcher(cabecalho.get(i)).find()) {
                return i;
            }
        }
        return null;
    }

    private static Integer ultima(List<String> cabecalho, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        for (int i = cabecalho.size() - 1; i >= 0; i--) {
            if (p.matcher(cabecalho.get(i)).find()) {
                return i;
            }
        }
        return null;
    }
}
