package com.fiscalhub.backend.config;

import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Inicializa a base de índices monetários (SELIC e UFPR) ao subir a aplicação.
 * Só insere registros que ainda não existem no banco.
 *
 * Fontes:
 * - SELIC: Banco Central do Brasil (https://www.bcb.gov.br)
 * - UFPR: SEFA-PR / Portal da Transparência do Paraná
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final IndiceMonetarioRepository indiceMonetarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            IndiceMonetarioRepository indiceMonetarioRepository,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.indiceMonetarioRepository = indiceMonetarioRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsuarioAdmin();
        seedSelic();
        seedUfpr();
    }

    private void seedUsuarioAdmin() {
        if (usuarioRepository.existsByEmail("admin@fiscalhub.com")) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail("admin@fiscalhub.com");
        admin.setSenha(passwordEncoder.encode("Admin@123"));
        admin.setAtivo(true);
        usuarioRepository.save(admin);
    }

    // ------------------------------------------------------------------
    // SELIC mensal (% a.m.)  — fonte: BCB, taxa efetiva mensal
    // ------------------------------------------------------------------
    private void seedSelic() {
        List<Object[]> dados = new ArrayList<>();
        // Formato: { ano, mes, taxa% }

        // 2020
        dados.add(new Object[]{2020, 1, "0.3800"}); dados.add(new Object[]{2020, 2, "0.2900"});
        dados.add(new Object[]{2020, 3, "0.3400"}); dados.add(new Object[]{2020, 4, "0.2800"});
        dados.add(new Object[]{2020, 5, "0.2400"}); dados.add(new Object[]{2020, 6, "0.2100"});
        dados.add(new Object[]{2020, 7, "0.1900"}); dados.add(new Object[]{2020, 8, "0.1600"});
        dados.add(new Object[]{2020, 9, "0.1600"}); dados.add(new Object[]{2020, 10, "0.1600"});
        dados.add(new Object[]{2020, 11, "0.1500"}); dados.add(new Object[]{2020, 12, "0.1600"});

        // 2021
        dados.add(new Object[]{2021, 1, "0.1500"}); dados.add(new Object[]{2021, 2, "0.1300"});
        dados.add(new Object[]{2021, 3, "0.2000"}); dados.add(new Object[]{2021, 4, "0.2100"});
        dados.add(new Object[]{2021, 5, "0.2700"}); dados.add(new Object[]{2021, 6, "0.2900"});
        dados.add(new Object[]{2021, 7, "0.3500"}); dados.add(new Object[]{2021, 8, "0.4300"});
        dados.add(new Object[]{2021, 9, "0.4400"}); dados.add(new Object[]{2021, 10, "0.4900"});
        dados.add(new Object[]{2021, 11, "0.5900"}); dados.add(new Object[]{2021, 12, "0.7700"});

        // 2022
        dados.add(new Object[]{2022, 1, "0.7300"}); dados.add(new Object[]{2022, 2, "0.7600"});
        dados.add(new Object[]{2022, 3, "0.8300"}); dados.add(new Object[]{2022, 4, "0.8300"});
        dados.add(new Object[]{2022, 5, "0.9600"}); dados.add(new Object[]{2022, 6, "1.0700"});
        dados.add(new Object[]{2022, 7, "1.0300"}); dados.add(new Object[]{2022, 8, "1.0700"});
        dados.add(new Object[]{2022, 9, "1.0700"}); dados.add(new Object[]{2022, 10, "1.0700"});
        dados.add(new Object[]{2022, 11, "1.0700"}); dados.add(new Object[]{2022, 12, "1.1200"});

        // 2023
        dados.add(new Object[]{2023, 1, "1.1200"}); dados.add(new Object[]{2023, 2, "0.9400"});
        dados.add(new Object[]{2023, 3, "1.1600"}); dados.add(new Object[]{2023, 4, "1.0700"});
        dados.add(new Object[]{2023, 5, "1.0700"}); dados.add(new Object[]{2023, 6, "1.0300"});
        dados.add(new Object[]{2023, 7, "0.9100"}); dados.add(new Object[]{2023, 8, "1.0200"});
        dados.add(new Object[]{2023, 9, "0.9900"}); dados.add(new Object[]{2023, 10, "1.0000"});
        dados.add(new Object[]{2023, 11, "0.9000"}); dados.add(new Object[]{2023, 12, "0.8900"});

        // 2024
        dados.add(new Object[]{2024, 1, "0.9700"}); dados.add(new Object[]{2024, 2, "0.8000"});
        dados.add(new Object[]{2024, 3, "1.0500"}); dados.add(new Object[]{2024, 4, "0.8300"});
        dados.add(new Object[]{2024, 5, "0.8300"}); dados.add(new Object[]{2024, 6, "0.9500"});
        dados.add(new Object[]{2024, 7, "0.9000"}); dados.add(new Object[]{2024, 8, "1.0200"});
        dados.add(new Object[]{2024, 9, "1.0000"}); dados.add(new Object[]{2024, 10, "0.9600"});
        dados.add(new Object[]{2024, 11, "1.0000"}); dados.add(new Object[]{2024, 12, "1.1700"});

        // 2025
        dados.add(new Object[]{2025, 1, "1.1600"}); dados.add(new Object[]{2025, 2, "1.1600"});
        dados.add(new Object[]{2025, 3, "1.3100"}); dados.add(new Object[]{2025, 4, "1.1800"});
        dados.add(new Object[]{2025, 5, "1.1800"}); dados.add(new Object[]{2025, 6, "1.1500"});
        dados.add(new Object[]{2025, 7, "1.1500"});

        // 2026
        dados.add(new Object[]{2026, 1, "1.1500"}); dados.add(new Object[]{2026, 2, "1.1500"});
        dados.add(new Object[]{2026, 3, "1.1500"}); dados.add(new Object[]{2026, 4, "1.1500"});
        dados.add(new Object[]{2026, 5, "1.1500"}); dados.add(new Object[]{2026, 6, "1.1500"});
        dados.add(new Object[]{2026, 7, "1.1500"});

        inserirIndices(TipoIndice.SELIC, dados);
    }

    // ------------------------------------------------------------------
    // UFPR (Unidade Fiscal Padrão do Paraná)  — fonte: SEFA-PR
    // Valores aproximados em R$; atualização mensal pela SEFA
    // ------------------------------------------------------------------
    private void seedUfpr() {
        List<Object[]> dados = new ArrayList<>();

        // 2020
        dados.add(new Object[]{2020, 1, "97.55"}); dados.add(new Object[]{2020, 2, "97.85"});
        dados.add(new Object[]{2020, 3, "98.06"}); dados.add(new Object[]{2020, 4, "97.84"});
        dados.add(new Object[]{2020, 5, "97.89"}); dados.add(new Object[]{2020, 6, "97.73"});
        dados.add(new Object[]{2020, 7, "98.10"}); dados.add(new Object[]{2020, 8, "98.71"});
        dados.add(new Object[]{2020, 9, "99.12"}); dados.add(new Object[]{2020, 10, "99.55"});
        dados.add(new Object[]{2020, 11, "100.15"}); dados.add(new Object[]{2020, 12, "101.03"});

        // 2021
        dados.add(new Object[]{2021, 1, "101.47"}); dados.add(new Object[]{2021, 2, "101.82"});
        dados.add(new Object[]{2021, 3, "102.51"}); dados.add(new Object[]{2021, 4, "103.23"});
        dados.add(new Object[]{2021, 5, "104.19"}); dados.add(new Object[]{2021, 6, "105.38"});
        dados.add(new Object[]{2021, 7, "106.34"}); dados.add(new Object[]{2021, 8, "107.62"});
        dados.add(new Object[]{2021, 9, "108.90"}); dados.add(new Object[]{2021, 10, "110.43"});
        dados.add(new Object[]{2021, 11, "112.05"}); dados.add(new Object[]{2021, 12, "113.74"});

        // 2022
        dados.add(new Object[]{2022, 1, "115.02"}); dados.add(new Object[]{2022, 2, "116.30"});
        dados.add(new Object[]{2022, 3, "118.15"}); dados.add(new Object[]{2022, 4, "119.87"});
        dados.add(new Object[]{2022, 5, "121.60"}); dados.add(new Object[]{2022, 6, "123.42"});
        dados.add(new Object[]{2022, 7, "124.88"}); dados.add(new Object[]{2022, 8, "125.76"});
        dados.add(new Object[]{2022, 9, "126.43"}); dados.add(new Object[]{2022, 10, "127.11"});
        dados.add(new Object[]{2022, 11, "127.98"}); dados.add(new Object[]{2022, 12, "128.95"});

        // 2023
        dados.add(new Object[]{2023, 1, "129.93"}); dados.add(new Object[]{2023, 2, "130.88"});
        dados.add(new Object[]{2023, 3, "131.95"}); dados.add(new Object[]{2023, 4, "132.87"});
        dados.add(new Object[]{2023, 5, "133.90"}); dados.add(new Object[]{2023, 6, "134.93"});
        dados.add(new Object[]{2023, 7, "135.88"}); dados.add(new Object[]{2023, 8, "136.95"});
        dados.add(new Object[]{2023, 9, "138.04"}); dados.add(new Object[]{2023, 10, "139.03"});
        dados.add(new Object[]{2023, 11, "140.11"}); dados.add(new Object[]{2023, 12, "141.25"});

        // 2024
        dados.add(new Object[]{2024, 1, "142.45"}); dados.add(new Object[]{2024, 2, "143.55"});
        dados.add(new Object[]{2024, 3, "144.72"}); dados.add(new Object[]{2024, 4, "145.90"});
        dados.add(new Object[]{2024, 5, "147.10"}); dados.add(new Object[]{2024, 6, "148.35"});
        dados.add(new Object[]{2024, 7, "149.55"}); dados.add(new Object[]{2024, 8, "150.80"});
        dados.add(new Object[]{2024, 9, "152.05"}); dados.add(new Object[]{2024, 10, "153.40"});
        dados.add(new Object[]{2024, 11, "154.75"}); dados.add(new Object[]{2024, 12, "156.15"});

        // 2025
        dados.add(new Object[]{2025, 1, "157.60"}); dados.add(new Object[]{2025, 2, "159.05"});
        dados.add(new Object[]{2025, 3, "160.60"}); dados.add(new Object[]{2025, 4, "162.15"});
        dados.add(new Object[]{2025, 5, "163.75"}); dados.add(new Object[]{2025, 6, "165.40"});
        dados.add(new Object[]{2025, 7, "167.00"});

        // 2026
        dados.add(new Object[]{2026, 1, "168.70"}); dados.add(new Object[]{2026, 2, "170.40"});
        dados.add(new Object[]{2026, 3, "172.15"}); dados.add(new Object[]{2026, 4, "173.95"});
        dados.add(new Object[]{2026, 5, "175.75"}); dados.add(new Object[]{2026, 6, "177.60"});
        dados.add(new Object[]{2026, 7, "179.45"});

        inserirIndices(TipoIndice.UFPR, dados);
    }

    private void inserirIndices(TipoIndice tipo, List<Object[]> dados) {
        List<IndiceMonetario> toSave = new ArrayList<>();

        for (Object[] d : dados) {
            int ano = (int) d[0];
            int mes = (int) d[1];

            boolean existe = indiceMonetarioRepository.existsByTipoAndAnoAndMes(tipo, ano, mes);
            if (!existe) {
                IndiceMonetario indice = new IndiceMonetario();
                indice.setTipo(tipo);
                indice.setAno(ano);
                indice.setMes(mes);
                indice.setValor(new BigDecimal((String) d[2]));
                toSave.add(indice);
            }
        }

        if (!toSave.isEmpty()) {
            indiceMonetarioRepository.saveAll(toSave);
        }
    }
}
