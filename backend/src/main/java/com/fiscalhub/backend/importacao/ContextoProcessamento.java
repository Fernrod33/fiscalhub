package com.fiscalhub.backend.importacao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.EtapaProcessamento;
import com.fiscalhub.backend.importacao.Inconsistencia.Severidade;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * O que um processador usa durante a leitura: o arquivo, o registro de etapa/progresso, as inconsistências e a
 * associação do cliente pela empresa lida do arquivo. Um contexto por execução de job.
 */
public class ContextoProcessamento {

    /** Acima disso as inconsistências são só contadas (um arquivo ruim não vira milhões de linhas). */
    static final int LIMITE_INCONSISTENCIAS = 1000;

    private final Long jobId;
    private DocumentoFiscal documento;
    private final ArmazenamentoArquivos armazenamento;
    private final ClienteRepository clienteRepository;
    private final RegistroProgresso registroProgresso;
    private final List<Inconsistencia> inconsistencias = new ArrayList<>();
    private int omitidas;
    private EtapaProcessamento etapa = EtapaProcessamento.RECEBIDO;
    private int progresso = -1;

    ContextoProcessamento(Long jobId, ArmazenamentoArquivos armazenamento, ClienteRepository clienteRepository,
                          RegistroProgresso registroProgresso) {
        this.jobId = jobId;
        this.armazenamento = armazenamento;
        this.clienteRepository = clienteRepository;
        this.registroProgresso = registroProgresso;
    }

    /** O documento gerenciado pela transação do processamento (o que o processador altera vai junto no commit). */
    void usar(DocumentoFiscal documentoGerenciado) {
        documento = documentoGerenciado;
    }

    public InputStream abrir() {
        return armazenamento.abrir(documento.getChaveArmazenamento());
    }

    public long tamanho() {
        return documento.getTamanhoBytes();
    }

    public void etapa(EtapaProcessamento novaEtapa) {
        etapa = novaEtapa;
        registroProgresso.atualizar(jobId, etapa, Math.max(progresso, 0));
    }

    /** Progresso = bytes lidos / tamanho; só grava quando o percentual muda. */
    public void lidos(long bytes) {
        int percentual = (int) Math.min(100, tamanho() == 0 ? 100 : bytes * 100 / tamanho());
        if (percentual != progresso) {
            progresso = percentual;
            registroProgresso.atualizar(jobId, etapa, progresso);
        }
    }

    public void registrar(Severidade severidade, String registro, Long linha, String regra, String mensagem) {
        if (inconsistencias.size() < LIMITE_INCONSISTENCIAS) {
            inconsistencias.add(new Inconsistencia(severidade, registro, linha, regra, mensagem));
        } else {
            omitidas++;
        }
    }

    /**
     * Associa o documento ao cliente da empresa lida no arquivo. Arquivo de filial (EFD ICMS/IPI é por estabelecimento)
     * vai para o cliente com a mesma raiz de CNPJ (8 primeiros dígitos), se houver um só. Upload feito dentro de um
     * cliente de outra empresa é erro (nunca associação silenciosa); sem cliente com esse CNPJ ou raiz no escritório, o
     * documento aguarda a confirmação.
     */
    public Cliente cliente(String cnpj, String nome) {
        Cliente atual = documento.getCliente();
        if (atual != null) {
            if (!atual.getCnpj().equals(cnpj) && !mesmaRaiz(atual.getCnpj(), cnpj)) {
                throw new FalhaImportacao("CNPJ_DIVERGENTE", "O arquivo é do CNPJ " + cnpj + " (" + nome
                        + "), mas foi enviado no cliente " + atual.getRazaoSocial() + " (CNPJ " + atual.getCnpj()
                        + "). Envie-o no cliente certo ou pela central de importação.");
            }
            return atual;
        }
        Long escritorio = documento.getEscritorio().getId();
        Cliente encontrado = clienteRepository.findByEscritorioIdAndCnpj(escritorio, cnpj).orElseGet(() -> {
            List<Cliente> mesmaRaiz = cnpj.length() == 14 ? clienteRepository.findByEscritorioIdAndCnpjStartingWith(escritorio,
                    cnpj.substring(0, 8)) : List.of();
            if (mesmaRaiz.size() != 1) {
                throw new AguardandoUsuario(DocumentoStatus.AGUARDANDO_CLIENTE,
                        "Nenhum cliente do escritório tem o CNPJ " + cnpj + ". Confirme o cadastro sugerido.", cnpj, nome);
            }
            return mesmaRaiz.get(0);
        });
        documento.setCliente(encontrado);
        return encontrado;
    }

    private static boolean mesmaRaiz(String a, String b) {
        return a != null && b != null && a.length() == 14 && b.length() == 14 && a.regionMatches(0, b, 0, 8);
    }

    /** Arquivos que não identificam a empresa (planilhas): exigem o cliente escolhido pelo usuário. */
    public Cliente clienteObrigatorio() {
        if (documento.getCliente() == null) {
            throw new AguardandoUsuario(DocumentoStatus.AGUARDANDO_CLIENTE,
                    "O arquivo não identifica a empresa. Escolha o cliente.", null, null);
        }
        return documento.getCliente();
    }

    boolean temAlertaOuErro() {
        return inconsistencias.stream().anyMatch(i -> i.getSeveridade() != Severidade.INFO);
    }

    /** Novas a cada chamada: podem ser gravadas mais de uma vez (conclusão que falha e vira erro). */
    List<Inconsistencia> inconsistencias() {
        List<Inconsistencia> todas = new ArrayList<>(inconsistencias.stream()
                .map(i -> new Inconsistencia(i.getSeveridade(), i.getRegistro(), i.getLinha(), i.getRegra(), i.getMensagem()))
                .toList());
        if (omitidas > 0) {
            todas.add(new Inconsistencia(Severidade.INFO, null, null, "INCONSISTENCIAS_OMITIDAS",
                    omitidas + " outras inconsistências não foram listadas (limite de " + LIMITE_INCONSISTENCIAS + ")."));
        }
        return todas;
    }
}
