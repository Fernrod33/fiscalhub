package com.fiscalhub.backend.situacao;

import com.fiscalhub.backend.situacao.ConciliacaoService.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Status da conciliação pelos três valores (o atraso sem acréscimos depende dos acréscimos: ver o teste de aceite). */
class ConciliacaoStatusTest {

    private static final BigDecimal TOL = new BigDecimal("0.01");

    @Test
    void cadaStatus() {
        assertThat(status("100", "100", "100")).isEqualTo(Status.CONCILIADO);
        assertThat(status("100", "100", "100.01")).isEqualTo(Status.CONCILIADO); // dentro da tolerância
        assertThat(status("100", "90", "90")).isEqualTo(Status.DIVERGENCIA_APURADO_DECLARADO);
        assertThat(status("100", null, null)).isEqualTo(Status.NAO_DECLARADO);
        assertThat(status("100", "100", "60")).isEqualTo(Status.PAGO_A_MENOR);
        assertThat(status("100", "100", "160")).isEqualTo(Status.PAGO_A_MAIOR);
        assertThat(status("100", "100", null)).isEqualTo(Status.NAO_PAGO);
        assertThat(status(null, "100", "100")).isEqualTo(Status.CONCILIADO); // só declarado e pago (sem SPED)
        assertThat(status("0", "0", null)).isEqualTo(Status.CONCILIADO); // saldo credor: nada a recolher
        assertThat(ConciliacaoService.classificar(dec("100"), dec("100"), null, dec("100"), null, false, TOL))
                .isEqualTo(Status.COMPENSADO_OU_SUSPENSO);
        assertThat(ConciliacaoService.classificar(dec("100"), dec("100"), null, null, dec("100"), false, TOL))
                .isEqualTo(Status.PARCELADO);
        assertThat(ConciliacaoService.classificar(dec("100"), dec("100"), null, null, null, true, TOL))
                .isEqualTo(Status.PARCELADO); // o débito do período está parcelado na situação fiscal
    }

    @Test
    void toleranciaConfiguravel() {
        assertThat(ConciliacaoService.classificar(dec("100"), dec("100"), dec("99.50"), null, null, false, new BigDecimal("1.00")))
                .isEqualTo(Status.CONCILIADO);
    }

    private static Status status(String apurado, String declarado, String pago) {
        return ConciliacaoService.classificar(dec(apurado), dec(declarado), dec(pago), null, null, false, TOL);
    }

    private static BigDecimal dec(String v) {
        return v == null ? null : new BigDecimal(v);
    }
}
