package com.fiscalhub.backend.contabil.planilha;

import com.fiscalhub.backend.contabil.RepositorioContabil;
import com.fiscalhub.backend.contabil.RepositorioContabil.Conta;
import com.fiscalhub.backend.contabil.RepositorioContabil.Saldo;
import com.fiscalhub.backend.contabil.planilha.RepositorioPlanilha.Colunas;
import com.fiscalhub.backend.contabil.planilha.RepositorioPlanilha.Mapeamento;
import com.fiscalhub.backend.contabil.referencial.MontagemReferencial;
import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.importacao.AguardandoUsuario;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.importacao.ProcessadorDocumento;
import com.fiscalhub.backend.sped.Saldos;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Balancete em planilha (.xlsx ou .csv). Usa o mapeamento de colunas salvo para o cliente (reconhecido pela assinatura
 * do cabeçalho) e o período escolhido no assistente; sem eles, o documento aguarda o mapeamento. Grava contas e saldos
 * nas mesmas tabelas da ECD (origem PLANILHA, com aba e linha) e monta as demonstrações referenciais pelo mesmo código.
 */
@Component
public class ProcessadorPlanilha implements ProcessadorDocumento {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final RepositorioPlanilha planilhas;
    private final RepositorioContabil repositorio;
    private final MontagemReferencial montagem;

    public ProcessadorPlanilha(RepositorioPlanilha planilhas, RepositorioContabil repositorio, MontagemReferencial montagem) {
        this.planilhas = planilhas;
        this.repositorio = repositorio;
        this.montagem = montagem;
    }

    @Override
    public boolean suporta(DocumentoTipo tipo) {
        return tipo == DocumentoTipo.BALANCETE;
    }

    @Override
    public void processar(DocumentoFiscal documento, ContextoProcessamento contexto) {
        Cliente cliente = contexto.clienteObrigatorio();
        contexto.etapa(EtapaProcessamento.LENDO);
        try {
            Mapeamento mapeamento = reconhecer(cliente.getId(), documento, contexto);
            if (mapeamento == null || documento.getPeriodoInicio() == null || documento.getPeriodoFim() == null) {
                throw new AguardandoUsuario(DocumentoStatus.AGUARDANDO_MAPEAMENTO,
                        "Indique as colunas e o período do balancete no assistente.", null, null);
            }
            List<LinhaConta> linhas = ler(documento, mapeamento, contexto);
            contexto.lidos(contexto.tamanho());
            contexto.etapa(EtapaProcessamento.VALIDANDO);
            validar(linhas, mapeamento.colunas(), registro(mapeamento), contexto);
            contexto.etapa(EtapaProcessamento.GRAVANDO);
            gravar(documento, cliente, mapeamento, linhas, contexto);
        } catch (IOException e) {
            // IOException (inclusive zip bomb recusada): a mensagem da biblioteca fica fora da tela
            throw new FalhaImportacao("PLANILHA_ILEGIVEL", "Não foi possível ler a planilha: arquivo corrompido, grande demais ou fora do formato.");
        } catch (FontePlanilha.PlanilhaInvalida e) {
            throw new FalhaImportacao("PLANILHA_INVALIDA", e.getMessage(), null, e.linha() == 0 ? null : e.linha());
        }
    }

    /** Mapeamento salvo cujo cabeçalho bate com o desta planilha (primeira leitura, só até a linha do cabeçalho). */
    private Mapeamento reconhecer(long clienteId, DocumentoFiscal documento, ContextoProcessamento contexto) throws IOException {
        for (Mapeamento m : planilhas.mapeamentos(clienteId)) {
            try (FontePlanilha fonte = FontePlanilha.abrir(contexto.abrir(), documento.getExtensao())) {
                List<String> cabecalho = new ArrayList<>();
                fonte.ler(aba(fonte, m.aba()), (n, celulas) -> {
                    if (n == m.linhaCabecalho()) {
                        cabecalho.addAll(celulas);
                    }
                    return n < m.linhaCabecalho();
                });
                if (!cabecalho.isEmpty() && RepositorioPlanilha.assinatura(cabecalho).equals(m.assinatura())) {
                    return m;
                }
            }
        }
        return null;
    }

    /** A aba do mapeamento, se existir nesta planilha; senão a primeira. */
    static String aba(FontePlanilha fonte, String desejada) throws IOException {
        return desejada != null && fonte.abas().contains(desejada) ? desejada : null;
    }

    record LinhaConta(String codigo, String nome, BigDecimal inicial, BigDecimal debitos, BigDecimal creditos,
                      BigDecimal saldoFinal, long linha) {
    }

    private List<LinhaConta> ler(DocumentoFiscal documento, Mapeamento m, ContextoProcessamento contexto) throws IOException {
        Colunas c = m.colunas();
        Map<String, LinhaConta> porCodigo = new LinkedHashMap<>();
        try (FontePlanilha fonte = FontePlanilha.abrir(contexto.abrir(), documento.getExtensao())) {
            fonte.ler(aba(fonte, m.aba()), (n, celulas) -> {
                if (n <= m.linhaCabecalho()) {
                    return true;
                }
                String codigo = celula(celulas, c.codigo());
                if (codigo == null) {
                    return true; // linha em branco, título ou total
                }
                try {
                    LinhaConta l = new LinhaConta(codigo, nome(celula(celulas, c.descricao()), codigo),
                            saldo(celulas, c.saldoInicial(), c.dcInicial()), absoluto(celulas, c.debitos()),
                            absoluto(celulas, c.creditos()), saldo(celulas, c.saldoFinal(), c.dcFinal()), n);
                    if (l.saldoFinal() == null) {
                        contexto.registrar(Severidade.ALERTA, registro(m), n, "SEM_SALDO_FINAL",
                                "A conta " + codigo + " está sem saldo final e foi ignorada.");
                    } else if (porCodigo.putIfAbsent(codigo, l) != null) {
                        contexto.registrar(Severidade.ALERTA, registro(m), n, "CONTA_DUPLICADA",
                                "A conta " + codigo + " aparece mais de uma vez; vale a primeira.");
                    }
                } catch (IllegalArgumentException e) {
                    contexto.registrar(Severidade.ERRO, registro(m), n, "VALOR_INVALIDO",
                            "Conta " + codigo + ": " + e.getMessage() + ". A linha foi ignorada.");
                }
                return true;
            });
        }
        if (porCodigo.isEmpty()) {
            throw new FalhaImportacao("PLANILHA_VAZIA", "Nenhuma conta com saldo foi encontrada abaixo do cabeçalho.");
        }
        return List.copyOf(porCodigo.values());
    }

    /**
     * Contas sintéticas (grupos) não somam: são as que têm outra conta "dentro" do código (1.1 em 1.1.01; 11 em 11101).
     * ponytail: deduzido pelo prefixo do código; coluna de tipo (S/A) no mapeamento se aparecer planilha que fuja disso.
     */
    static Set<String> sinteticas(List<LinhaConta> linhas) {
        Set<String> codigos = new HashSet<>();
        linhas.forEach(l -> codigos.add(l.codigo()));
        Set<String> grupos = new HashSet<>();
        for (String codigo : codigos) {
            for (int i = 1; i < codigo.length(); i++) {
                char ch = codigo.charAt(i);
                String prefixo = codigo.substring(0, i);
                boolean corte = ch == '.' || ch == '-' || ch == '/' || (Character.isDigit(ch) && prefixo.chars().allMatch(Character::isDigit));
                if (corte && codigos.contains(prefixo)) {
                    grupos.add(prefixo);
                }
            }
        }
        return grupos;
    }

    private static void validar(List<LinhaConta> linhas, Colunas colunas, String registro, ContextoProcessamento contexto) {
        Set<String> grupos = sinteticas(linhas);
        List<LinhaConta> analiticas = linhas.stream().filter(l -> !grupos.contains(l.codigo())).toList();
        BigDecimal debitos = BigDecimal.ZERO;
        BigDecimal creditos = BigDecimal.ZERO;
        BigDecimal finais = BigDecimal.ZERO;
        BigDecimal iniciais = BigDecimal.ZERO;
        for (LinhaConta l : analiticas) {
            debitos = debitos.add(valor(l.debitos()));
            creditos = creditos.add(valor(l.creditos()));
            finais = finais.add(l.saldoFinal());
            iniciais = iniciais.add(valor(l.inicial()));
            if (l.inicial() != null && l.debitos() != null && l.creditos() != null) {
                BigDecimal esperado = l.inicial().add(l.debitos()).subtract(l.creditos());
                if (esperado.subtract(l.saldoFinal()).abs().compareTo(TOLERANCIA) > 0) {
                    contexto.registrar(Severidade.ERRO, registro, l.linha(), "SALDO_INCONSISTENTE", "Conta " + l.codigo()
                            + ": saldo inicial + débitos − créditos = " + esperado.toPlainString() + ", mas o saldo final é "
                            + l.saldoFinal().toPlainString() + " (devedor +, credor −).");
                }
            }
        }
        if (colunas.debitos() != null && colunas.creditos() != null && debitos.subtract(creditos).abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, registro, null, "DEBITO_DIFERENTE_CREDITO", "A soma dos débitos ("
                    + debitos.toPlainString() + ") é diferente da soma dos créditos (" + creditos.toPlainString() + ").");
        }
        // balancete fecha: a soma dos saldos das contas analíticas (devedor +, credor −) é zero
        if (finais.abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, registro, null, "BALANCETE_NAO_FECHA", "A soma dos saldos finais das contas "
                    + "analíticas é " + finais.toPlainString() + " (devedor +, credor −), e deveria ser zero.");
        }
        if (colunas.saldoInicial() != null && iniciais.abs().compareTo(TOLERANCIA) > 0) {
            contexto.registrar(Severidade.ERRO, registro, null, "BALANCETE_NAO_FECHA_INICIAL", "A soma dos saldos iniciais "
                    + "das contas analíticas é " + iniciais.toPlainString() + ", e deveria ser zero.");
        }
    }

    private void gravar(DocumentoFiscal documento, Cliente cliente, Mapeamento m, List<LinhaConta> linhas,
                        ContextoProcessamento contexto) {
        Set<String> grupos = sinteticas(linhas);
        // de-para: o salvo pelo usuário vale; depois, o I051 da ECD do mesmo cliente para o mesmo código de conta
        Map<String, String> referencial = new HashMap<>(planilhas.referencialDasEcds(cliente.getId()));
        referencial.putAll(planilhas.deparaSalvo(cliente.getId()));
        String registro = registro(m);

        repositorio.apagarDoDocumento(documento.getId());
        long escrituracaoId = repositorio.inserirEscrituracao(new RepositorioContabil.NovaEscrituracao(documento.getId(),
                cliente.getId(), "PLANILHA", documento.getPeriodoInicio(), documento.getPeriodoFim(), null,
                m.planoReferencial(), "ORIGINAL"));
        Map<String, Long> ids = repositorio.inserirContas(escrituracaoId, linhas.stream().map(l -> new Conta(l.codigo(), null,
                null, null, grupos.contains(l.codigo()), l.nome(), grupos.contains(l.codigo()) ? null : referencial.get(l.codigo()),
                registro, l.linha())).toList());
        try (RepositorioContabil.LoteSaldos lote = repositorio.lote(escrituracaoId)) {
            for (LinhaConta l : linhas) {
                lote.adicionar(new Saldo(ids.get(l.codigo()), "PLANILHA", null, documento.getPeriodoInicio(),
                        documento.getPeriodoFim(), l.inicial(), l.debitos(), l.creditos(), l.saldoFinal(), registro, l.linha()));
            }
        }
        List<String> semDepara = linhas.stream().filter(l -> !grupos.contains(l.codigo()) && !referencial.containsKey(l.codigo()))
                .map(LinhaConta::codigo).toList();
        if (!semDepara.isEmpty()) {
            contexto.registrar(Severidade.ALERTA, registro, null, "CONTA_SEM_DEPARA", semDepara.size() + " conta(s) sem "
                    + "associação ao plano referencial ficam fora das demonstrações referenciais: "
                    + String.join(", ", semDepara.subList(0, Math.min(30, semDepara.size()))) + (semDepara.size() > 30 ? " e outras." : "."));
        }
        for (RepositorioContabil.Vigente anterior : repositorio.vigentesDoPeriodo(cliente.getId(), "PLANILHA",
                documento.getPeriodoInicio(), documento.getPeriodoFim(), escrituracaoId)) {
            repositorio.marcarSituacao(anterior.id(), "SUBSTITUIDA");
            contexto.registrar(Severidade.INFO, registro, null, "RETIFICACAO", "Este balancete substitui o do documento "
                    + anterior.documentoId() + " para o mesmo período.");
        }
        montagem.montar(new MontagemReferencial.Pedido(escrituracaoId, m.planoReferencial(), documento.getPeriodoInicio(),
                documento.getPeriodoFim(), "PLANILHA", "PLANILHA", List.of(), List.of()), contexto);
    }

    private static String registro(Mapeamento m) {
        String aba = m.aba() == null ? "Planilha" : "Aba " + m.aba();
        return aba.length() > 40 ? aba.substring(0, 40) : aba;
    }

    private static String celula(List<String> celulas, Integer coluna) {
        if (coluna == null || coluna >= celulas.size()) {
            return null;
        }
        String v = celulas.get(coluna).trim();
        return v.isEmpty() ? null : v;
    }

    private static String nome(String nome, String codigo) {
        String n = nome == null ? codigo : nome;
        return n.length() > 500 ? n.substring(0, 500) : n;
    }

    /** Saldo com sinal: indicador D/C da coluna própria, senão o do valor ("1.000,00 C"), senão o sinal do número. */
    private static BigDecimal saldo(List<String> celulas, Integer coluna, Integer colunaDc) {
        NumeroPlanilha.Valor v = NumeroPlanilha.ler(celula(celulas, coluna));
        if (v == null) {
            return null;
        }
        String indicador = colunaDc != null ? celula(celulas, colunaDc) : v.indicador();
        if (indicador == null) {
            return v.numero();
        }
        if (!indicador.equalsIgnoreCase("D") && !indicador.equalsIgnoreCase("C")) {
            throw new IllegalArgumentException("indicador \"" + indicador + "\" (use D ou C)");
        }
        return Saldos.comSinal(v.numero().abs(), indicador);
    }

    private static BigDecimal absoluto(List<String> celulas, Integer coluna) {
        NumeroPlanilha.Valor v = NumeroPlanilha.ler(celula(celulas, coluna));
        return v == null ? null : v.numero().abs();
    }

    private static BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
