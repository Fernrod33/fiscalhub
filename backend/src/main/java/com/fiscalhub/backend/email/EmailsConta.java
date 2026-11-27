package com.fiscalhub.backend.email;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.AuditoriaEvento;
import com.fiscalhub.backend.auditoria.AuditoriaEventoRepository;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.relatorio.IdentidadeEscritorioService;
import com.fiscalhub.backend.relatorio.IdentidadeEscritorioService.Identidade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * E-mails da conta (convite, recuperação de senha e avisos de segurança), com a identidade do escritório. Montados na
 * transação de quem pede e enviados só depois do commit, no pool dos jobs, com até 3 tentativas. A auditoria registra
 * o envio (modelo, resultado, tentativas), nunca o conteúdo: o link leva um token que só existe no e-mail.
 * ponytail: a mensagem fica só na memória até sair (o token não pode ir em claro para o banco); se o backend cair
 * antes, o usuário pede de novo (reenviar convite, nova recuperação). O pool é o das importações: uma importação
 * longa atrasa o e-mail; pool próprio se isso incomodar.
 */
@Service
public class EmailsConta {

    public enum Aviso {
        SENHA_ALTERADA("Sua senha foi alterada", "A senha da sua conta no FiscalHub foi alterada."),
        SEGUNDO_FATOR_ATIVADO("Verificação em duas etapas ativada", "A verificação em duas etapas foi ativada na sua conta."),
        SEGUNDO_FATOR_DESATIVADO("Verificação em duas etapas desativada", "A verificação em duas etapas foi desativada na sua conta."),
        PAPEL_ALTERADO("Seu papel no escritório mudou", "O administrador do escritório alterou o seu papel.");

        final String assunto;
        final String texto;

        Aviso(String assunto, String texto) {
            this.assunto = assunto;
            this.texto = texto;
        }
    }

    record Pedido(long usuarioId, long escritorioId, String modelo, EnvioDeEmail.Mensagem mensagem) {
    }

    private static final Logger log = LoggerFactory.getLogger(EmailsConta.class);
    private static final int TENTATIVAS = 3;
    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final ITemplateEngine templates;
    private final IdentidadeEscritorioService identidades;
    private final ApplicationEventPublisher eventos;
    private final EnvioDeEmail envio;
    private final TaskExecutor executor;
    private final TaskScheduler agendador;
    private final AuditoriaEventoRepository auditoria;
    private final String origem;
    private final long esperaMs;

    public EmailsConta(ITemplateEngine templates, IdentidadeEscritorioService identidades, ApplicationEventPublisher eventos,
                       EnvioDeEmail envio, @Qualifier("applicationTaskExecutor") TaskExecutor executor, TaskScheduler agendador,
                       AuditoriaEventoRepository auditoria, @Value("${fiscalhub.origem-publica}") String origem,
                       @Value("${fiscalhub.email.espera-tentativa-ms:5000}") long esperaMs) {
        this.templates = templates;
        this.identidades = identidades;
        this.eventos = eventos;
        this.envio = envio;
        this.executor = executor;
        this.agendador = agendador;
        this.auditoria = auditoria;
        this.origem = origem.replaceAll("/+$", "");
        this.esperaMs = esperaMs;
    }

    // ---- modelos (o token vai no fragmento #: não aparece em log de servidor nem no Referer)

    public void convite(Usuario u, String token) {
        pedir(u, "CONVITE", "Convite para o FiscalHub", "Você foi convidado",
                List.of(nome(IdentidadeEscritorioService.identidade(u.getEscritorio())) + " convidou você para usar o FiscalHub.",
                        "Defina sua senha pelo botão abaixo. O link vale por 72 horas e só pode ser usado uma vez."),
                "Definir minha senha", origem + "/convite#" + token);
    }

    public void recuperacao(Usuario u, String token) {
        pedir(u, "RECUPERACAO_SENHA", "Redefinição de senha do FiscalHub", "Redefinir a senha",
                List.of("Recebemos um pedido para redefinir a senha da sua conta.",
                        "O link vale por 30 minutos e só pode ser usado uma vez. Se a verificação em duas etapas estiver ativa, "
                                + "o código do aplicativo também será pedido.",
                        "Se não foi você, ignore este e-mail: a senha continua a mesma."),
                "Redefinir minha senha", origem + "/redefinir-senha#" + token);
    }

    public void aviso(Usuario u, Aviso aviso) {
        pedir(u, "AVISO_" + aviso.name(), aviso.assunto, aviso.assunto,
                List.of(aviso.texto + " Quando: " + LocalDateTime.now().format(QUANDO) + ".",
                        "Se não foi você, troque sua senha e avise o administrador do escritório."),
                "Abrir o FiscalHub", origem + "/login");
    }

    private void pedir(Usuario u, String modelo, String assunto, String titulo, List<String> paragrafos, String botao, String link) {
        Identidade id = IdentidadeEscritorioService.identidade(u.getEscritorio());
        byte[] logo = logo(u.getEscritorio().getLogoChave());
        Context c = new Context(Locale.of("pt", "BR"));
        c.setVariables(Map.of("escritorio", nome(id), "cor", id.corPrincipal(), "titulo", titulo, "paragrafos", paragrafos,
                "botao", botao, "link", link, "logo", logo != null));
        String html = templates.process("email/mensagem", c);
        String texto = titulo + "\n\n" + String.join("\n\n", paragrafos) + "\n\n" + botao + ": " + link
                + "\n\n--\n" + nome(id) + " · enviado pelo FiscalHub. Não responda a este e-mail.";
        eventos.publishEvent(new Pedido(u.getId(), id.escritorioId(), modelo,
                new EnvioDeEmail.Mensagem(u.getEmail(), assunto, html, texto, logo)));
    }

    // ---- envio depois do commit (sem transação, na hora)

    @TransactionalEventListener(fallbackExecution = true)
    void aoConfirmar(Pedido p) {
        executor.execute(() -> tentar(p, 1));
    }

    private void tentar(Pedido p, int tentativa) {
        try {
            envio.enviar(p.mensagem());
            auditar(p, tentativa, "SUCESSO");
        } catch (RuntimeException e) {
            log.warn("E-mail {} ao usuário {}: tentativa {} de {} falhou ({})", p.modelo(), p.usuarioId(), tentativa, TENTATIVAS,
                    e.getClass().getSimpleName());
            if (tentativa < TENTATIVAS) {
                agendador.schedule(() -> executor.execute(() -> tentar(p, tentativa + 1)),
                        Instant.now().plusMillis(esperaMs * tentativa * tentativa));
            } else {
                auditar(p, tentativa, "FALHA");
            }
        }
    }

    private void auditar(Pedido p, int tentativas, String resultado) {
        AuditoriaEvento e = new AuditoriaEvento();
        e.setAcao(AcaoAuditoria.EMAIL_ENVIAR);
        e.setRecursoTipo(AcaoAuditoria.EMAIL_ENVIAR.recurso());
        e.setRecursoId(p.usuarioId());
        e.setEscritorioId(p.escritorioId());
        e.setDetalhes(Map.of("resultado", resultado, "modelo", p.modelo(), "tentativas", tentativas));
        auditoria.save(e);
    }

    private byte[] logo(String chave) {
        try {
            return chave == null ? null : identidades.ler(chave);
        } catch (RuntimeException e) {
            log.warn("Logotipo do escritório indisponível; o e-mail sai sem ele ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String nome(Identidade id) {
        return id.nomeExibicao() == null ? id.nome() : id.nomeExibicao();
    }
}
