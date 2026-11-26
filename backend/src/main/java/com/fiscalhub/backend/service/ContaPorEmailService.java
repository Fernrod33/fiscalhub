package com.fiscalhub.backend.service;

import com.fiscalhub.backend.auditoria.AcaoAuditoria;
import com.fiscalhub.backend.auditoria.AuditoriaEvento;
import com.fiscalhub.backend.auditoria.AuditoriaEventoRepository;
import com.fiscalhub.backend.email.EmailsConta;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.exception.BusinessRuleException;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.BloqueioLogin;
import com.fiscalhub.backend.security.LimiteFrequencia;
import com.fiscalhub.backend.security.SegurancaContaService;
import com.fiscalhub.backend.security.TokensAcesso;
import com.fiscalhub.backend.security.TokensAcesso.Tipo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Convite e recuperação de senha por link de uso único enviado por e-mail. A
 * recuperação responde igual e no mesmo tempo exista ou não a conta (busca, token e e-mail saem em segundo plano), tem limite por IP (429) e por conta (um e-mail a cada 5 minutos, em
 * silêncio) e não dispensa o segundo fator: com ele ativo, o código é pedido junto com a senha nova.
 */
@Service
@Transactional
public class ContaPorEmailService {

    public static final String LINK_INVALIDO = "Link inválido ou expirado. Peça um novo.";
    private static final Duration INTERVALO_POR_CONTA = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(ContaPorEmailService.class);

    private final UsuarioRepository usuarios;
    private final TokensAcesso tokens;
    private final EmailsConta emails;
    private final PasswordEncoder senhas;
    private final SegurancaContaService seguranca;
    private final BloqueioLogin bloqueio;
    private final LimiteFrequencia limite;
    private final AuditoriaEventoRepository auditoria;
    private final JdbcTemplate jdbc;
    private final int pedidosPorMinutoPorIp;
    private final TaskExecutor executor;
    private final TransactionTemplate transacao;

    public ContaPorEmailService(UsuarioRepository usuarios, TokensAcesso tokens, EmailsConta emails, PasswordEncoder senhas,
                                SegurancaContaService seguranca, BloqueioLogin bloqueio, LimiteFrequencia limite,
                                AuditoriaEventoRepository auditoria, JdbcTemplate jdbc,
                                @Value("${fiscalhub.recuperacao.pedidos-por-minuto-por-ip:5}") int pedidosPorMinutoPorIp,
                                @Qualifier("applicationTaskExecutor") TaskExecutor executor, TransactionTemplate transacao) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.emails = emails;
        this.senhas = senhas;
        this.seguranca = seguranca;
        this.bloqueio = bloqueio;
        this.limite = limite;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
        this.pedidosPorMinutoPorIp = pedidosPorMinutoPorIp;
        this.executor = executor;
        this.transacao = transacao;
    }

    // ---- convite

    /** Usuário recém-criado (senha aleatória, convite pendente): link de 72 h para definir a senha. */
    public void convidar(Usuario u) {
        emails.convite(u, tokens.emitir(u.getId(), Tipo.CONVITE));
    }

    /** Novo link (o anterior deixa de valer); só enquanto o convite não foi aceito. */
    public void reenviarConvite(Usuario u) {
        if (!u.getAtivo() || !u.getConvitePendente()) {
            throw new BusinessRuleException("Este usuário já definiu a senha; não há convite para reenviar.");
        }
        convidar(u);
    }

    public void aceitarConvite(String token, String senha, String ip) {
        Usuario u = tokens.consumir(Tipo.CONVITE, token).flatMap(usuarios::findById)
                .filter(x -> x.getAtivo() && x.getConvitePendente())
                .orElseThrow(() -> new BusinessRuleException(LINK_INVALIDO));
        definirSenha(u, senha);
        u.setConvitePendente(false);
        auditar(AcaoAuditoria.CONVITE_ACEITAR, u, ip);
    }

    // ---- recuperação de senha

    /** Na requisição só o limite por IP (não depende da conta); o resto vai para o pool, para o tempo não revelar a conta. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void solicitarRecuperacao(String email, String ip) {
        limite.verificar("pedidos de recuperação de senha", ip, pedidosPorMinutoPorIp);
        executor.execute(() -> {
            try {
                transacao.executeWithoutResult(t -> recuperar(email.strip(), ip));
            } catch (RuntimeException e) {
                log.warn("Pedido de recuperação de senha falhou ({})", e.getClass().getSimpleName());
            }
        });
    }

    private void recuperar(String email, String ip) {
        usuarios.findByEmail(email)
                .filter(u -> u.getAtivo() && !u.getConvitePendente())
                .filter(u -> !tokens.emitidoHaMenosDe(u.getId(), Tipo.RECUPERACAO, INTERVALO_POR_CONTA))
                .ifPresent(u -> {
                    emails.recuperacao(u, tokens.emitir(u.getId(), Tipo.RECUPERACAO));
                    auditar(AcaoAuditoria.SENHA_RECUPERACAO_SOLICITAR, u, ip);
                });
    }

    /** Confere o 2º fator (se ativo) antes de gastar o token: código errado não queima o link, mas conta no bloqueio. */
    public void redefinirSenha(String token, String novaSenha, String codigo, String ip) {
        Usuario u = tokens.dono(Tipo.RECUPERACAO, token).flatMap(usuarios::findById).filter(Usuario::getAtivo)
                .orElseThrow(() -> new BusinessRuleException(LINK_INVALIDO));
        if (u.getTotpAtivo()) {
            bloqueio.verificar(u.getEmail(), ip);
            if (codigo == null || codigo.isBlank() || !seguranca.conferirSegundoFator(u, codigo)) {
                bloqueio.registrarFalha(u.getEmail(), ip);
                throw new BusinessRuleException("Informe o código atual do aplicativo autenticador (ou um código de recuperação).");
            }
            bloqueio.registrarSucesso(u.getEmail());
        }
        if (tokens.consumir(Tipo.RECUPERACAO, token).isEmpty()) {
            throw new BusinessRuleException(LINK_INVALIDO);
        }
        definirSenha(u, novaSenha);
        emails.aviso(u, EmailsConta.Aviso.SENHA_ALTERADA);
        auditar(AcaoAuditoria.SENHA_REDEFINIR, u, ip);
    }

    /** Senha nova: invalida os tokens de sessão e encerra as sessões abertas. */
    private void definirSenha(Usuario u, String senha) {
        u.setSenha(senhas.encode(senha));
        u.setVersaoToken(u.getVersaoToken() + 1);
        jdbc.update("UPDATE sessao SET revogada_em = now() WHERE usuario_id = ? AND revogada_em IS NULL", u.getId());
    }

    /** Rotas sem sessão: a auditoria é gravada aqui, com o usuário do token (nunca o e-mail digitado). */
    private void auditar(AcaoAuditoria acao, Usuario u, String ip) {
        AuditoriaEvento e = new AuditoriaEvento();
        e.setAcao(acao);
        e.setRecursoTipo(acao.recurso());
        e.setRecursoId(u.getId());
        e.setUsuarioId(u.getId());
        e.setEscritorioId(u.getEscritorio().getId());
        e.setIp(ip);
        e.setDetalhes(Map.of("resultado", "SUCESSO"));
        auditoria.save(e);
    }
}
