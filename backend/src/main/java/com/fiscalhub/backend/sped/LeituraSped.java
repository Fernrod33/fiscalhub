package com.fiscalhub.backend.sped;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.importacao.ContextoProcessamento;
import com.fiscalhub.backend.importacao.Escrituracoes;
import com.fiscalhub.backend.importacao.FalhaImportacao;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * O que é comum à leitura de qualquer arquivo SPED: o laço em stream (só os registros de interesse viram campos), o
 * progresso, a versão do leiaute, o 0000 (empresa, período, finalidade), o cliente pelo CNPJ, a escrituração, a
 * contagem do 9900/9999, o conteúdo depois do 9999 e a retificação. A subclasse lê os registros do tipo dela.
 * <p>
 * Uma instância por execução. Ordem: {@link #tratar} para cada registro de interesse, {@link #validar}, retificação e
 * {@link #gravar}. Exceção desfaz tudo (uma transação por documento).
 */
public abstract class LeituraSped {

    private static final int LINHAS_POR_PROGRESSO = 4096;

    protected final DocumentoFiscal documento;
    protected final ContextoProcessamento contexto;
    protected final Escrituracoes escrituracoes;
    private final LayoutSped layout;
    private final String origem;
    private final String nome;
    private final Set<String> interesse = new HashSet<>(Set.of("0000", "9900", "9999"));

    protected String versao;
    protected Cliente cliente;
    protected LocalDate inicio;
    protected LocalDate fim;
    protected long escrituracaoId;
    protected long linha0000;
    private String finalidade;
    private String[] campos0000;

    private Map<String, Long> contagem;
    private final Map<String, long[]> declarados = new LinkedHashMap<>(); // registro -> [quantidade, linha do 9900]
    private Long quantidadeLinhasDeclarada;
    private long linha9999;

    /**
     * @param origem    origem gravada na escrituração (ECD, ECF, EFD_CONTRIBUICOES, EFD_ICMS_IPI)
     * @param nome      nome do arquivo nas mensagens ("ECD", "EFD-Contribuições"...)
     * @param registros registros lidos; os demais são pulados sem virar campos (0000, 9900 e 9999 sempre entram)
     */
    protected LeituraSped(String origem, String nome, LayoutSped layout, Set<String> registros, DocumentoFiscal documento,
                          ContextoProcessamento contexto, Escrituracoes escrituracoes) {
        this.origem = origem;
        this.nome = nome;
        this.layout = layout;
        this.interesse.addAll(registros);
        this.documento = documento;
        this.contexto = contexto;
        this.escrituracoes = escrituracoes;
    }

    /** Versão do leiaute, se esta linha a traz (no 0000 das EFD e da ECF; no I010 da ECD); senão null. */
    protected abstract String versao(String registro, String[] campos);

    /** 0000 lido pelo leiaute da versão: chame {@link #abrirEscrituracao}. */
    protected abstract void abertura(RegistroSped r0000);

    /** Um registro de interesse (exceto 0000, 9900 e 9999), na ordem do arquivo. */
    protected abstract void tratar(RegistroSped r);

    /** Fim da leitura, com ou sem erro (ex.: gravar o resto de um lote). */
    protected void fimDaLeitura() {
    }

    /** Validações do tipo, depois da contagem do 9900. */
    protected void validar() {
    }

    /** Gravação do que ficou em memória, depois da validação e da retificação. */
    protected void gravar() {
    }

    public final void executar() {
        contexto.etapa(EtapaProcessamento.LENDO);
        try (LeitorSped leitor = new LeitorSped(contexto.abrir(), interesse)) {
            ler(leitor);
            contexto.lidos(contexto.tamanho());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (LeitorSped.LinhaInvalida e) {
            throw new FalhaImportacao("LINHA_INVALIDA", "Linha " + e.linha() + ": " + e.getMessage(), null, e.linha());
        } finally {
            fimDaLeitura();
        }
        contexto.etapa(EtapaProcessamento.VALIDANDO);
        validarContagem();
        validar();
        escrituracoes.retificar(contexto, escrituracaoId, cliente.getId(), origem, inicio, fim, finalidade, nome, "0000", linha0000);
        contexto.etapa(EtapaProcessamento.GRAVANDO);
        gravar();
    }

    private void ler(LeitorSped leitor) throws IOException {
        String linha = leitor.proximaLinha();
        if (linha == null || !"0000".equals(LeitorSped.registro(linha))) {
            throw new FalhaImportacao("SEM_REGISTRO_0000", "O arquivo não começa pelo registro 0000 da " + nome + ".", "0000", 1L);
        }
        long progresso = 0;
        do {
            String registro = LeitorSped.registro(linha);
            long numero = leitor.numeroLinha();
            if (numero - progresso >= LINHAS_POR_PROGRESSO) {
                progresso = numero;
                contexto.lidos(leitor.bytesLidos());
            }
            if (registro == null) {
                if (linha.isBlank()) {
                    continue;
                }
                throw new FalhaImportacao("LINHA_FORA_DO_FORMATO", "A linha não segue o formato SPED (|REG|...|).", null, numero);
            }
            linha(registro, numero, LeitorSped.campos(linha));
            if ("9999".equals(registro)) {
                contagem = leitor.contagem(); // o que vem depois do 9999 não entra na contagem
                verificarDepoisDo9999(leitor);
                return;
            }
        } while ((linha = leitor.proximaLinha()) != null);
        throw new FalhaImportacao("SEM_REGISTRO_9999", "Arquivo incompleto: o registro 9999 (encerramento) não foi encontrado.");
    }

    private void linha(String registro, long numero, String[] campos) {
        if ("0000".equals(registro)) {
            campos0000 = campos;
            linha0000 = numero;
        }
        if (versao == null) {
            String v = versao(registro, campos);
            if (v == null) {
                if ("0000".equals(registro)) {
                    return; // a versão vem num registro seguinte (I010 da ECD)
                }
                throw new FalhaImportacao("SEM_VERSAO_DO_LEIAUTE", "O registro " + registro
                        + " aparece antes do registro que informa a versão do leiaute.", registro, numero);
            }
            if (!layout.cobre(v)) {
                throw new FalhaImportacao("LEIAUTE_NAO_COBERTO", "Leiaute da " + nome + " " + v + " não coberto. Versões lidas: "
                        + String.join(", ", layout.versoes()) + ".", registro, numero);
            }
            versao = v;
            abertura(layout.registro(versao, "0000", linha0000, campos0000));
            if ("0000".equals(registro)) {
                return;
            }
        }
        RegistroSped r = layout.registro(versao, registro, numero, campos);
        switch (registro) {
            case "0000" -> throw new FalhaImportacao("0000_REPETIDO", "O registro 0000 aparece mais de uma vez.", "0000", numero);
            case "9900" -> declarados.put(r.texto("REG_BLC"), new long[]{Long.parseLong(r.texto("QTD_REG_BLC")), numero});
            case "9999" -> {
                quantidadeLinhasDeclarada = Long.parseLong(r.texto("QTD_LIN"));
                linha9999 = numero;
            }
            default -> tratar(r);
        }
    }

    /** O PVA pode anexar assinatura ou outro conteúdo depois do 9999: é ignorado, com alerta. */
    private void verificarDepoisDo9999(LeitorSped leitor) {
        try {
            String depois = leitor.proximaLinha();
            while (depois != null && depois.isBlank()) {
                depois = leitor.proximaLinha();
            }
            if (depois == null) {
                return;
            }
        } catch (IOException | LeitorSped.LinhaInvalida e) {
            // bloco binário de assinatura: também é conteúdo depois do 9999
        }
        contexto.registrar(Severidade.ALERTA, "9999", linha9999, "CONTEUDO_APOS_9999",
                "Há conteúdo depois do registro 9999 (ex.: bloco de assinatura do PVA). Foi ignorado.");
    }

    /**
     * Empresa, período e finalidade lidos do 0000: associa o cliente pelo CNPJ e cria a escrituração (apagando a de um
     * processamento anterior do mesmo documento).
     */
    protected void abrirEscrituracao(String cnpj, String nomeEmpresa, LocalDate inicio, LocalDate fim, boolean substituta,
                                     String planoReferencial) {
        if (inicio == null || fim == null || cnpj == null) {
            throw new FalhaImportacao("0000_INCOMPLETO", "O registro 0000 não tem período ou CNPJ.", "0000", linha0000);
        }
        this.inicio = inicio;
        this.fim = fim;
        this.finalidade = substituta ? "SUBSTITUTA" : "ORIGINAL";
        cliente = contexto.cliente(cnpj, nomeEmpresa);
        documento.setPeriodoInicio(inicio);
        documento.setPeriodoFim(fim);
        escrituracoes.apagarDoDocumento(documento.getId());
        escrituracaoId = escrituracoes.inserir(new Escrituracoes.Nova(documento.getId(), cliente.getId(), origem, inicio, fim,
                versao, planoReferencial, finalidade));
    }

    private void validarContagem() {
        declarados.forEach((registro, declarado) -> {
            long lidos = contagem.getOrDefault(registro, 0L);
            if (lidos != declarado[0]) {
                contexto.registrar(Severidade.ERRO, "9900", declarado[1], "CONTAGEM_9900", "O 9900 declara "
                        + declarado[0] + " registro(s) " + registro + ", mas o arquivo tem " + lidos + ".");
            }
        });
        contagem.keySet().stream().filter(r -> !declarados.containsKey(r)).sorted().forEach(r ->
                contexto.registrar(Severidade.ERRO, "9900", null, "CONTAGEM_9900",
                        "O registro " + r + " aparece no arquivo, mas não está na contagem do bloco 9."));
        if (quantidadeLinhasDeclarada != null && quantidadeLinhasDeclarada != linha9999) {
            contexto.registrar(Severidade.ERRO, "9999", linha9999, "QUANTIDADE_LINHAS", "O 9999 declara "
                    + quantidadeLinhasDeclarada + " linhas, mas o arquivo tem " + linha9999 + " até o 9999.");
        }
    }
}
