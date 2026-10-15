package com.fiscalhub.backend.integracao;

import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoStatus;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.importacao.ProcessamentoDocumentoService;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.storage.ArmazenamentoArquivos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ECD sintética de 300 MB ou mais (quase tudo lançamentos I200/I250, que a leitura pula), processada pelo pipeline
 * real. Opcional (lento): mvn test -Dtest=DesempenhoEcdTest -Ddesempenho=true. Registra tempo e pico de heap.
 */
@TesteIntegracao
@EnabledIfSystemProperty(named = "desempenho", matches = "true")
class DesempenhoEcdTest {

    private static final long TAMANHO_ALVO = 300L * 1024 * 1024;
    private static final int CONTAS_ANALITICAS = 2000;

    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired ArmazenamentoArquivos armazenamento;
    @Autowired ProcessamentoDocumentoService processamento;
    @Autowired TransactionTemplate transacao;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ecdDe300MbEmStreamComMemoriaLimitada() throws Exception {
        Path arquivo = Path.of("target", "ecd-grande.txt");
        long linhas = gerar(arquivo);
        long tamanho = Files.size(arquivo);
        assertThat(tamanho).isGreaterThanOrEqualTo(TAMANHO_ALVO);

        String sufixo = UUID.randomUUID().toString();
        Usuario u = dados.usuario(dados.escritorio("Desempenho " + sufixo), "desemp-" + sufixo + "@test.com", Papel.ADMIN);
        Cliente cliente = dados.cliente(u, "11222333000181");
        String chave = "teste/ecd-grande-" + sufixo + ".txt";
        try (InputStream in = Files.newInputStream(arquivo)) {
            armazenamento.salvar(chave, in, tamanho, "text/plain");
        }

        MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
        System.gc();
        long base = memoria.getHeapMemoryUsage().getUsed();
        AtomicLong pico = new AtomicLong(base);
        Thread medidor = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                pico.accumulateAndGet(memoria.getHeapMemoryUsage().getUsed(), Math::max);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        long inicio = System.nanoTime();
        Long documentoId = transacao.execute(t -> {
            DocumentoFiscal d = new DocumentoFiscal();
            d.setEscritorio(cliente.getEscritorio());
            d.setCliente(cliente);
            d.setUsuario(u);
            d.setTipo(DocumentoTipo.OUTRO);
            d.setNomeOriginalArquivo("ecd-grande.txt");
            d.setExtensao(".txt");
            d.setMimeType("text/plain");
            d.setTamanhoBytes(tamanho);
            d.setChaveArmazenamento(chave);
            documentoRepository.save(d);
            processamento.enfileirar(d);
            return d.getId();
        });
        aguardar(() -> documentoRepository.findById(documentoId).orElseThrow().getStatus().finalizado(), 30 * 60_000);
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);
        medidor.interrupt();

        System.out.printf("DESEMPENHO ECD: %,d bytes, %,d linhas, %s, pico de heap %,d MB (base %,d MB, -Xmx %,d MB)%n",
                tamanho, linhas, tempo, pico.get() / (1024 * 1024), base / (1024 * 1024),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        assertThat(documentoRepository.findById(documentoId).orElseThrow().getStatus())
                .as("inconsistências: %s", jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ?", String.class, documentoId))
                .isEqualTo(DocumentoStatus.PROCESSADO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saldo_conta s JOIN escrituracao_contabil e ON e.id = s.escrituracao_id "
                + "WHERE e.documento_id = ?", Long.class, documentoId)).isEqualTo(12L * (CONTAS_ANALITICAS + 1) + CONTAS_ANALITICAS / 2); // I155 mensais (+ capital) e I355
        Files.deleteIfExists(arquivo);
    }

    /**
     * Plano com 2.000 contas analíticas (metade ativo, metade resultado), saldos mensais coerentes (sem movimento) e
     * lançamentos até passar de 300 MB. Contagens 9900/9999 calculadas.
     */
    private static long gerar(Path arquivo) throws IOException {
        Map<String, Long> contagem = new LinkedHashMap<>();
        long linhas = 0;
        try (Writer w = new BufferedWriter(Files.newBufferedWriter(arquivo, StandardCharsets.ISO_8859_1), 1 << 20)) {
            linhas += escrever(w, contagem, "|0000|LECD|01012021|31122021|EMPRESA GRANDE LTDA|11222333000181|PR||4106902||0|0|1|0||0|0||N|N|0|0|1|");
            linhas += escrever(w, contagem, "|0001|0|");
            linhas += escrever(w, contagem, "|0990|3|");
            linhas += escrever(w, contagem, "|I001|0|");
            linhas += escrever(w, contagem, "|I010|G|9.00|");
            linhas += escrever(w, contagem, "|I050|01012021|01|S|1|1||ATIVO|");
            linhas += escrever(w, contagem, "|I050|01012021|03|S|1|3||PATRIMONIO LIQUIDO|");
            linhas += escrever(w, contagem, "|I050|01012021|03|A|2|3.1|3|Capital|");
            linhas += escrever(w, contagem, "|I051||2.03.01.01.01|");
            linhas += escrever(w, contagem, "|I050|01012021|04|S|1|4||RESULTADO|");
            for (int i = 0; i < CONTAS_ANALITICAS; i++) {
                boolean ativo = i % 2 == 0;
                linhas += escrever(w, contagem, "|I050|01012021|" + (ativo ? "01" : "04") + "|A|2|" + conta(i) + "|"
                        + (ativo ? "1" : "4") + "|Conta " + i + "|");
                linhas += escrever(w, contagem, "|I051||" + (ativo ? "1.01.01.01.01" : "3.01.01.01.01.05") + "|");
            }
            for (int mes = 1; mes <= 12; mes++) {
                YearMonth m = YearMonth.of(2021, mes);
                linhas += escrever(w, contagem, "|I150|" + data(m.atDay(1).getDayOfMonth(), mes) + "|" + data(m.lengthOfMonth(), mes) + "|");
                // ativo: 1,00 D em cada conta; capital fecha o balanço; resultado sem saldo
                for (int i = 0; i < CONTAS_ANALITICAS; i++) {
                    String dc = i % 2 == 0 ? "D" : "C";
                    String v = i % 2 == 0 ? "1,00" : "0,00";
                    linhas += escrever(w, contagem, "|I155|" + conta(i) + "||" + v + "|" + dc + "|0,00|0,00|" + v + "|" + dc + "|||||||");
                }
                linhas += escrever(w, contagem, "|I155|3.1||1000,00|C|0,00|0,00|1000,00|C|||||||");
            }
            long numero = 0;
            while (BYTES.get() < TAMANHO_ALVO) {
                numero++;
                linhas += escrever(w, contagem, "|I200|" + numero + "|15062021|1234,56|N||");
                linhas += escrever(w, contagem, "|I250|" + conta(0) + "||1234,56|D||HIST|Lancamento de teste " + numero + "|");
                linhas += escrever(w, contagem, "|I250|" + conta(2) + "||1234,56|C||HIST|Lancamento de teste " + numero + "|");
            }
            linhas += escrever(w, contagem, "|I350|31122021|");
            for (int i = 1; i < CONTAS_ANALITICAS; i += 2) {
                linhas += escrever(w, contagem, "|I355|" + conta(i) + "||0,00|D|||");
            }
            long blocoI = contagem.entrySet().stream().filter(e -> e.getKey().startsWith("I")).mapToLong(Map.Entry::getValue).sum();
            linhas += escrever(w, contagem, "|I990|" + (blocoI + 1) + "|");
            linhas += escrever(w, contagem, "|9001|0|");
            int registros9900 = contagem.size() + 3; // + 9900, 9990, 9999
            Map<String, Long> declarada = new LinkedHashMap<>(contagem);
            declarada.put("9900", (long) registros9900);
            declarada.put("9990", 1L);
            declarada.put("9999", 1L);
            for (Map.Entry<String, Long> e : declarada.entrySet()) {
                linhas += escrever(w, null, "|9900|" + e.getKey() + "|" + e.getValue() + "|");
            }
            linhas += escrever(w, null, "|9990|" + (declarada.size() + 3) + "|");
            linhas += escrever(w, null, "|9999|" + (linhas + 1) + "|");
        }
        return linhas;
    }

    /** Bytes já escritos (ISO-8859-1: um por caractere). */
    private static final AtomicLong BYTES = new AtomicLong();

    private static long escrever(Writer w, Map<String, Long> contagem, String linha) throws IOException {
        w.write(linha);
        w.write("\r\n");
        BYTES.addAndGet(linha.length() + 2L);
        if (contagem != null) {
            contagem.merge(linha.substring(1, linha.indexOf('|', 1)), 1L, Long::sum);
        }
        return 1;
    }

    private static String conta(int i) {
        return (i % 2 == 0 ? "1." : "4.") + String.format("%05d", i);
    }

    private static String data(int dia, int mes) {
        return String.format("%02d%02d2021", dia, mes);
    }
}
