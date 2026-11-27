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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.fiscalhub.backend.integracao.Espera.aguardar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * EFD ICMS/IPI sintética de 1 GB (quase tudo documentos C100/C170/C190, que a leitura pula sem montar campos), com o
 * bloco E da amostra efd-icms-ipi-2025-01.txt, processada pelo pipeline real. Opcional (lento):
 * mvn test -Dtest=DesempenhoEfdTest -Ddesempenho=true. Registra tempo e pico de heap.
 */
@Tag("desempenho")
@TesteIntegracao
@EnabledIfSystemProperty(named = "desempenho", matches = "true")
class DesempenhoEfdTest {

    private static final long TAMANHO_ALVO = 1024L * 1024 * 1024;

    @Autowired DadosTeste dados;
    @Autowired DocumentoFiscalRepository documentoRepository;
    @Autowired ArmazenamentoArquivos armazenamento;
    @Autowired ProcessamentoDocumentoService processamento;
    @Autowired TransactionTemplate transacao;
    @Autowired JdbcTemplate jdbc;

    @Test
    void efdDe1GbEmStreamComMemoriaLimitada() throws Exception {
        // fora da pasta do projeto (que pode estar sincronizada): o arquivo e a cópia no storage são apagados no fim
        Path arquivo = Path.of(System.getProperty("java.io.tmpdir"), "fiscalhub-efd-grande.txt");
        long linhas = gerar(arquivo);
        long tamanho = Files.size(arquivo);
        assertThat(tamanho).isGreaterThanOrEqualTo(TAMANHO_ALVO);

        String sufixo = UUID.randomUUID().toString();
        Usuario u = dados.usuario(dados.escritorio("Desempenho EFD " + sufixo), "desemp-efd-" + sufixo + "@test.com", Papel.ADMIN);
        Cliente cliente = dados.cliente(u, "11222333000181");
        String chave = "teste/efd-grande-" + sufixo + ".txt";
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
            d.setNomeOriginalArquivo("efd-grande.txt");
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

        System.out.printf("DESEMPENHO EFD: %,d bytes, %,d linhas, %s, pico de heap %,d MB (base %,d MB, -Xmx %,d MB)%n",
                tamanho, linhas, tempo, pico.get() / (1024 * 1024), base / (1024 * 1024),
                Runtime.getRuntime().maxMemory() / (1024 * 1024));
        assertThat(documentoRepository.findById(documentoId).orElseThrow().getStatus())
                .as("inconsistências: %s", jdbc.queryForList("SELECT regra FROM inconsistencia_importacao WHERE documento_id = ?", String.class, documentoId))
                .isEqualTo(DocumentoStatus.PROCESSADO);
        assertThat(jdbc.queryForObject("SELECT valor_a_recolher FROM apuracao_tributo WHERE documento_id = ? AND tributo = 'ICMS'",
                BigDecimal.class, documentoId)).isEqualByComparingTo("5000");
        Files.deleteIfExists(arquivo);
        armazenamento.excluir(chave);
    }

    /** Abertura e bloco E da amostra; no meio, documentos até passar de 1 GB. Contagens 9900/9999 calculadas. */
    private static long gerar(Path arquivo) throws IOException {
        List<String> amostra = List.of(new String(new ClassPathResource("amostras/efd-icms-ipi-2025-01.txt").getContentAsByteArray(),
                StandardCharsets.ISO_8859_1).split("\r\n"));
        Map<String, Long> contagem = new LinkedHashMap<>();
        long linhas = 0;
        try (Writer w = new BufferedWriter(Files.newBufferedWriter(arquivo, StandardCharsets.ISO_8859_1), 1 << 20)) {
            for (String l : amostra.subList(0, amostra.indexOf("|0990|3|") + 1)) {
                linhas += escrever(w, contagem, l);
            }
            linhas += escrever(w, contagem, "|C001|0|");
            long numero = 0;
            long blocoC = 1;
            while (BYTES.get() < TAMANHO_ALVO) {
                numero++;
                linhas += escrever(w, contagem, "|C100|0|1|F" + numero + "|55|00|1|" + numero + "|4125010000000000000000000000000000000000"
                        + "|15012025|15012025|1000,00|0|0,00|0,00|1000,00|9|0,00|0,00|0,00|1000,00|180,00|0,00|0,00|0,00|0,00|0,00|0,00|0,00|");
                for (int item = 1; item <= 3; item++) {
                    linhas += escrever(w, contagem, "|C170|" + item + "|PROD" + item + "|Produto de teste " + item
                            + "|1,00000|UN|333,33|0,00|0|000|5102|5102|333,33|18,00|60,00|0,00|0,00|0,00|0|||0,00|0,00|0,00|01|333,33|1,65|||5,50|01|333,33|7,60|||25,33||");
                }
                linhas += escrever(w, contagem, "|C190|000|5102|18,00|1000,00|1000,00|180,00|0,00|0,00|0,00|0,00||");
                blocoC += 5;
            }
            linhas += escrever(w, contagem, "|C990|" + (blocoC + 1) + "|");
            for (String l : amostra.subList(amostra.indexOf("|E001|0|"), amostra.indexOf("|9001|0|"))) {
                linhas += escrever(w, contagem, l);
            }
            linhas += escrever(w, contagem, "|9001|0|");
            Map<String, Long> declarada = new LinkedHashMap<>(contagem);
            declarada.put("9900", (long) contagem.size() + 3); // + 9900, 9990, 9999
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

    private static final AtomicLong BYTES = new AtomicLong();

    private static long escrever(Writer w, Map<String, Long> contagem, String linha) throws IOException {
        w.write(linha);
        w.write("\r\n");
        BYTES.addAndGet(linha.length() + 2L);
        if (contagem != null) {
            contagem.merge(linha.substring(1, 5), 1L, Long::sum);
        }
        return 1;
    }
}
