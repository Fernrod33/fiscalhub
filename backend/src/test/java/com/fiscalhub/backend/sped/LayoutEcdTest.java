package com.fiscalhub.backend.sped;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Diferenças entre leiautes tiradas dos manuais (ver scripts/sped/gerar_layout_ecd.py). */
class LayoutEcdTest {

    private static final LayoutSped LAYOUT = LayoutSped.carregar("sped/ecd-leiautes.properties");

    private static RegistroSped ler(String versao, String linha) {
        return LAYOUT.registro(versao, LeitorSped.registro(linha), 1, LeitorSped.campos(linha));
    }

    @Test
    void versoesCobertas() {
        assertThat(LAYOUT.versoes()).containsExactly("7.00", "8.00", "9.00");
        assertThat(LAYOUT.cobre("6.00")).isFalse();
    }

    @Test
    void i051TemOPlanoReferencialSoNoLeiaute7() {
        assertThat(ler("7.00", "|I051|1||1.01.01.01.01|").texto("COD_CTA_REF")).isEqualTo("1.01.01.01.01");
        assertThat(ler("7.00", "|I051|1||1.01.01.01.01|").texto("COD_PLAN_REF")).isEqualTo("1");
        assertThat(ler("9.00", "|I051||1.01.01.01.01|").texto("COD_CTA_REF")).isEqualTo("1.01.01.01.01");
        assertThat(ler("9.00", "|I051||1.01.01.01.01|").texto("COD_PLAN_REF")).isNull();
    }

    @Test
    void j150GanhouOrdemEValorInicialNoLeiaute8() {
        RegistroSped l7 = ler("7.00", "|J150|3|T|1||Lucro|4500,00|C|R||");
        assertThat(l7.saldo("VL_CTA", "IND_DC_CTA")).isEqualByComparingTo("-4500");
        assertThat(l7.texto("NU_ORDEM")).isNull();
        RegistroSped l9 = ler("9.00", "|J150|1|3|T|1||Lucro|100,00|C|4500,00|C|R||");
        assertThat(l9.texto("NU_ORDEM")).isEqualTo("1");
        assertThat(l9.saldo("VL_CTA_INI", "IND_DC_CTA_INI")).isEqualByComparingTo("-100");
        assertThat(l9.saldo("VL_CTA_FIN", "IND_DC_CTA_FIN")).isEqualByComparingTo("-4500");
    }

    @Test
    void plano0000SoExisteAPartirDoLeiaute8() {
        String l9 = "|0000|LECD|01012024|31122024|EMPRESA|11222333000181|PR||4106902||0|0|1|0||0|0||N|N|0|0|1|";
        assertThat(ler("9.00", l9).texto("COD_PLAN_REF")).isEqualTo("1");
        assertThat(ler("9.00", l9).texto("CNPJ")).isEqualTo("11222333000181");
        assertThat(ler("7.00", "|0000|LECD|01012024|31122024|EMPRESA|11222333000181|PR||4106902||0|0|1|0||0|0||N|N|")
                .texto("COD_PLAN_REF")).isNull();
    }
}
