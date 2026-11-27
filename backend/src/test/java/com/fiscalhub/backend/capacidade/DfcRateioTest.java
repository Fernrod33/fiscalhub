package com.fiscalhub.backend.capacidade;

import com.fiscalhub.backend.capacidade.DfcDiretaService.Movimento;
import com.fiscalhub.backend.capacidade.DfcDiretaService.Partida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Rateio das contrapartidas, transferência entre contas de caixa e o de-para por prefixo. */
class DfcRateioTest {

    static final String[] LANC = {"10", "15012025", "123"};

    static Partida p(String ref, String valor) {
        return new Partida(ref, new BigDecimal(valor));
    }

    @Test
    void entradaDeCaixaRateadaNaProporcaoDosCreditosEOsDebitosSemCaixaFicamDeFora() {
        List<Movimento> m = new ArrayList<>();
        // D banco 1.000 | C clientes 600 | C receita 400 | D despesa 50 / C fornecedor 50 (sem caixa, não entra)
        DfcDiretaService.ratear(LANC, List.of(p("1.01.01.02.01", "1000"), p("1.01.02.02.01", "-600"), p("3.01.01.01.01.04", "-400"),
                p("3.01.01.09.01.99", "50"), p("2.01.01.03.01", "-50")), m);
        // os créditos sem caixa somam 1.050: 1.000 × 600/1.050, × 400/1.050 e o resto em fornecedores
        assertThat(m).extracting(Movimento::contaReferencial).containsExactly("1.01.02.02.01", "3.01.01.01.01.04", "2.01.01.03.01");
        assertThat(m.stream().map(Movimento::valor).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1000");
        assertThat(m.getFirst().valor()).isEqualByComparingTo("571.43");
        assertThat(m.get(1).valor()).isEqualByComparingTo("380.95");
        assertThat(m.getFirst().linha()).isEqualTo(123);
    }

    @Test
    void saidaDeCaixaVaiParaOsDebitosComSinalNegativo() {
        List<Movimento> m = new ArrayList<>();
        DfcDiretaService.ratear(LANC, List.of(p("2.01.01.01.01", "720"), p("2.01.01.01.03", "200"), p("1.01.01.02.01", "-920")), m);
        assertThat(m).extracting(Movimento::valor).containsExactly(new BigDecimal("-720.00"), new BigDecimal("-200.00"));
    }

    @Test
    void transferenciaEntreContasDeCaixaEhIgnorada() {
        List<Movimento> m = new ArrayList<>();
        DfcDiretaService.ratear(LANC, List.of(p("1.01.01.01.01", "5000"), p("1.01.01.02.01", "-5000")), m);
        assertThat(m).isEmpty();
    }

    @Test
    void categoriaPeloPrefixoMaisLongoPorNivelDoCodigo() {
        Map<String, String> depara = Map.of("2.01.01.09", "TRIBUTOS", "2.01.01.09.09", "PARCELAMENTOS_TRIBUTARIOS", "1.02", "INVESTIMENTOS");
        assertThat(DfcDiretaService.categoria("2.01.01.09.09", depara)).isEqualTo("PARCELAMENTOS_TRIBUTARIOS");
        assertThat(DfcDiretaService.categoria("2.01.01.09.04", depara)).isEqualTo("TRIBUTOS");
        assertThat(DfcDiretaService.categoria("1.020", depara)).isEqualTo("OUTROS"); // "1.02" não é prefixo de nível de "1.020"
        assertThat(DfcDiretaService.categoria(null, depara)).isEqualTo("OUTROS");
    }
}
