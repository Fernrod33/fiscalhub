package com.fiscalhub.backend.integracao;


import com.fiscalhub.backend.entity.Cliente;
import com.fiscalhub.backend.entity.Debito;
import com.fiscalhub.backend.entity.DocumentoFiscal;
import com.fiscalhub.backend.entity.Escritorio;
import com.fiscalhub.backend.entity.IndiceMonetario;
import com.fiscalhub.backend.entity.SimulacaoParcelamento;
import com.fiscalhub.backend.entity.Usuario;
import com.fiscalhub.backend.entity.enums.DocumentoTipo;
import com.fiscalhub.backend.entity.enums.ModalidadeParcelamento;
import com.fiscalhub.backend.entity.enums.OrgaoCredor;
import com.fiscalhub.backend.entity.enums.OrgaoReceita;
import com.fiscalhub.backend.entity.enums.Papel;
import com.fiscalhub.backend.entity.enums.SituacaoDebito;
import com.fiscalhub.backend.entity.enums.TipoIndice;
import com.fiscalhub.backend.entity.enums.TipoTributo;
import com.fiscalhub.backend.repository.ClienteRepository;
import com.fiscalhub.backend.repository.DocumentoFiscalRepository;
import com.fiscalhub.backend.repository.EscritorioRepository;
import com.fiscalhub.backend.repository.IndiceMonetarioRepository;
import com.fiscalhub.backend.repository.SimulacaoParcelamentoRepository;
import com.fiscalhub.backend.repository.UsuarioRepository;
import com.fiscalhub.backend.security.CookieSessao;
import com.fiscalhub.backend.security.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fiscalhub.backend.repository.DebitoRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fixture dos testes de integração: escritórios, usuários com papel, tokens e dados de cliente. */
@TestComponent
public class DadosTeste {

    public static final String SENHA = "senha-de-teste";

    private final EscritorioRepository escritorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final DocumentoFiscalRepository documentoRepository;
    private final SimulacaoParcelamentoRepository simulacaoRepository;
    private final IndiceMonetarioRepository indiceRepository;
    private final DebitoRepository debitoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private String senhaCodificada;

    public DadosTeste(EscritorioRepository escritorioRepository, UsuarioRepository usuarioRepository,
                      ClienteRepository clienteRepository, DocumentoFiscalRepository documentoRepository,
                      SimulacaoParcelamentoRepository simulacaoRepository, IndiceMonetarioRepository indiceRepository,
                      DebitoRepository debitoRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.escritorioRepository = escritorioRepository;
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.documentoRepository = documentoRepository;
        this.simulacaoRepository = simulacaoRepository;
        this.indiceRepository = indiceRepository;
        this.debitoRepository = debitoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    public Escritorio escritorio(String nome) {
        Escritorio e = new Escritorio();
        e.setNome(nome);
        return escritorioRepository.save(e);
    }

    public Usuario usuario(Escritorio escritorio, String email, Papel papel) {
        if (senhaCodificada == null) {
            senhaCodificada = passwordEncoder.encode(SENHA); // BCrypt é lento: codifica uma vez só
        }
        Usuario u = new Usuario();
        u.setNome(email);
        u.setEmail(email);
        u.setSenha(senhaCodificada);
        u.setPapel(papel);
        u.setEscritorio(escritorio);
        return usuarioRepository.save(u);
    }

    /**
     * CSRF como o navegador faz (cookie XSRF-TOKEN repetido no cabeçalho X-XSRF-TOKEN), pelo repositório real.
     * O csrf() do spring-security-test troca o repositório no contexto compartilhado e não deve ser usado.
     */
    public static final RequestPostProcessor CSRF = req -> {
        adicionarCookie(req, new Cookie("XSRF-TOKEN", "csrf-de-teste"));
        req.addHeader("X-XSRF-TOKEN", "csrf-de-teste");
        return req;
    };

    /** Sessão do navegador: cookie com o JWT e token CSRF válido. */
    public RequestPostProcessor sessao(Usuario usuario) {
        Cookie jwt = new Cookie(CookieSessao.NOME, jwtUtils.gerarToken(usuario));
        return req -> {
            adicionarCookie(req, jwt);
            return CSRF.postProcessRequest(req);
        };
    }

    private static void adicionarCookie(MockHttpServletRequest req, Cookie cookie) {
        Cookie[] atuais = req.getCookies() == null ? new Cookie[0] : req.getCookies();
        Cookie[] todos = Arrays.copyOf(atuais, atuais.length + 1);
        todos[atuais.length] = cookie;
        req.setCookies(todos);
    }

    public Cliente cliente(Usuario criadoPor, String cnpj) {
        Cliente c = new Cliente();
        c.setRazaoSocial("Cliente " + cnpj);
        c.setCnpj(cnpj);
        c.setEscritorio(criadoPor.getEscritorio());
        c.setCriadoPor(criadoPor);
        return clienteRepository.save(c);
    }

    public DocumentoFiscal documento(Cliente cliente) {
        DocumentoFiscal d = new DocumentoFiscal();
        d.setCliente(cliente);
        d.setEscritorio(cliente.getEscritorio());
        d.setUsuario(cliente.getCriadoPor());
        d.setTipo(DocumentoTipo.DCTF);
        d.setPeriodoInicio(LocalDate.of(2024, 1, 1));
        d.setPeriodoFim(LocalDate.of(2024, 1, 31));
        d.setNomeOriginalArquivo("dctf.pdf");
        d.setExtensao(".pdf");
        d.setMimeType("application/pdf");
        d.setTamanhoBytes(10L);
        d.setChaveArmazenamento("teste/interno-" + System.nanoTime() + ".pdf");
        return documentoRepository.save(d);
    }

    /** Débito sempre ligado ao documento de origem (como os da importação; o fluxo real é pelos adaptadores). */
    public Debito debito(DocumentoFiscal origem, String valor, LocalDate vencimento) {
        Debito d = new Debito();
        d.setCliente(origem.getCliente());
        d.setDocumentoOrigem(origem);
        d.setReferenciaOrigem("registro 1");
        d.setOrgaoCredor(OrgaoCredor.RFB);
        d.setTributo(TipoTributo.IRPJ);
        d.setCompetencia(vencimento.withDayOfMonth(1));
        d.setDataVencimento(vencimento);
        d.setValorPrincipal(new BigDecimal(valor));
        d.setSituacao(SituacaoDebito.EM_ABERTO);
        return debitoRepository.save(d);
    }

    public SimulacaoParcelamento simulacao(Debito debito) {
        SimulacaoParcelamento s = new SimulacaoParcelamento();
        s.setOrgao(OrgaoReceita.RECEITA_FEDERAL);
        s.setModalidade(ModalidadeParcelamento.ORDINARIO_RFB);
        s.setValorOriginal(debito.getValorPrincipal());
        s.setDataVencimentoOriginal(debito.getDataVencimento());
        s.setDataCalculo(LocalDate.of(2024, 6, 1));
        s.setCliente(debito.getCliente());
        s.setDebitos(new ArrayList<>(List.of(debito)));
        return simulacaoRepository.save(s);
    }

    /** Mesma taxa (% a.m.) em todos os meses do intervalo, inclusive. */
    public void selic(YearMonth de, YearMonth ate, String taxa) {
        for (YearMonth m = de; !m.isAfter(ate); m = m.plusMonths(1)) {
            IndiceMonetario i = new IndiceMonetario();
            i.setTipo(TipoIndice.SELIC);
            i.setAno(m.getYear());
            i.setMes(m.getMonthValue());
            i.setValor(new BigDecimal(taxa));
            indiceRepository.save(i);
        }
    }
}
