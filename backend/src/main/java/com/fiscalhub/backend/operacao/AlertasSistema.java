package com.fiscalhub.backend.operacao;

import com.fiscalhub.backend.parcelamento.RegrasParcelamento;
import com.fiscalhub.backend.security.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Avisos para quem mantém o sistema (o "ADMIN do sistema": os ids de usuário de fiscalhub.admin-sistema, não o ADMIN de um
 * escritório): edital com adesão terminando em até 30 dias, índice do mês ainda não importado e os layouts que caíram
 * sem leitura em todos os escritórios (só a assinatura e a contagem, sem dado do cliente). Também vão para o log, todo dia.
 */
@Service
public class AlertasSistema {

    public record AlertaSistema(String tipo, String mensagem, LocalDate prazo) {
    }

    public record LayoutNaoReconhecido(String tipo, String assinatura, long quantidade, LocalDateTime ultimo) {
    }

    private static final Logger log = LoggerFactory.getLogger(AlertasSistema.class);
    private static final int DIAS_EDITAL = 30;

    private final RegrasParcelamento regras;
    private final JdbcTemplate jdbc;
    /**
     * Ids, não e-mails: o cadastro é aberto e o e-mail não é verificado, então quem se cadastrasse com um e-mail da lista
     * ainda não usado viraria ADMIN do sistema. O id só existe depois que a conta do mantenedor foi criada.
     */
    private final Set<Long> admins;

    public AlertasSistema(RegrasParcelamento regras, JdbcTemplate jdbc, @Value("${fiscalhub.admin-sistema:}") List<String> admins) {
        this.regras = regras;
        this.jdbc = jdbc;
        this.admins = admins.stream().map(String::trim).filter(e -> !e.isEmpty()).map(Long::valueOf).collect(Collectors.toSet());
    }

    public boolean adminSistema(UsuarioAutenticado ua) {
        return ua != null && admins.contains(ua.id());
    }

    public List<AlertaSistema> alertas(LocalDate hoje) {
        List<AlertaSistema> alertas = new ArrayList<>();
        regras.modalidades(hoje).stream()
                .filter(m -> m.adesaoFim() != null && !hoje.isAfter(m.adesaoFim()) && ChronoUnit.DAYS.between(hoje, m.adesaoFim()) <= DIAS_EDITAL)
                .forEach(m -> alertas.add(new AlertaSistema("EDITAL", "A adesão a \"" + m.nome() + "\" termina em "
                        + ChronoUnit.DAYS.between(hoje, m.adesaoFim()) + " dia(s). Confira se houve prorrogação ou edital novo "
                        + "e atualize parcelamento/regras.json.", m.adesaoFim())));
        YearMonth selicEsperada = YearMonth.from(hoje).minusMonths(1);
        if (ultimo("SELIC") == null || ultimo("SELIC").isBefore(selicEsperada)) {
            alertas.add(new AlertaSistema("INDICE", "SELIC de " + selicEsperada + " ainda não importada do Banco Central.", null));
        }
        // a SEFA-PR publica a UPF/PR do mês nos primeiros dias dele
        if (hoje.getDayOfMonth() > 5 && (ultimo("UPF_PR") == null || ultimo("UPF_PR").isBefore(YearMonth.from(hoje)))) {
            alertas.add(new AlertaSistema("INDICE", "UPF/PR de " + YearMonth.from(hoje) + " ainda não importada da SEFA-PR.", null));
        }
        return alertas;
    }

    public List<LayoutNaoReconhecido> layoutsSemLeitura() {
        return jdbc.query("""
                SELECT tipo, COALESCE(assinatura_layout, '(sem assinatura)'), count(*), max(data_importacao) FROM documento_fiscal
                WHERE status = 'LAYOUT_NAO_RECONHECIDO' OR tipo = 'OUTRO' GROUP BY 1, 2 ORDER BY 3 DESC, 4 DESC LIMIT 200""",
                (rs, i) -> new LayoutNaoReconhecido(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toLocalDateTime()));
    }

    @Scheduled(cron = "${fiscalhub.alertas.cron:0 0 7 * * *}")
    public void registrarNoLog() {
        alertas(LocalDate.now()).forEach(a -> log.warn("Alerta do sistema [{}]: {}", a.tipo(), a.mensagem()));
    }

    private YearMonth ultimo(String tipo) {
        Integer m = jdbc.queryForObject("SELECT max(ano * 100 + mes) FROM indice_monetario WHERE tipo = ?", Integer.class, tipo);
        return m == null ? null : YearMonth.of(m / 100, m % 100);
    }
}
