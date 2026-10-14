import { afterEach, describe, expect, it, vi } from 'vitest';
import { configurarSessao, ErroApi, http, mensagemErro } from './http';

function responder(status: number, corpo: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(corpo), {
    status, headers: { 'Content-Type': 'application/problem+json' },
  })));
}

/** Promessa que deve falhar: devolve o ErroApi. */
const falha = (p: Promise<unknown>) => p.then(() => { throw new Error('esperava erro'); }, (e: ErroApi) => e);

afterEach(() => {
  vi.unstubAllGlobals();
  configurarSessao({ aberta: () => false, aoExpirar: () => {} });
  document.cookie = 'XSRF-TOKEN=; max-age=0';
});

describe('cliente HTTP', () => {
  it('normaliza o ProblemDetail em ErroApi', async () => {
    responder(409, { status: 409, detail: 'Este arquivo já foi importado para o cliente.', recursoExistenteId: 7 });

    const erro = await falha(http.get('/x'));

    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro.status).toBe(409);
    expect(erro.recursoExistenteId).toBe(7);
    expect(mensagemErro(erro, 'padrão')).toBe('Este arquivo já foi importado para o cliente.');
  });

  it('erro interno traz o id de correlação', async () => {
    responder(500, { status: 500, detail: 'Erro interno. Informe o código abc ao suporte.', correlacao: 'abc' });
    const erro = await falha(http.get('/x'));
    expect(erro.correlacao).toBe('abc');
  });

  it('falha de rede vira a mensagem padrão', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const erro = await falha(http.get('/x'));
    expect(mensagemErro(erro, 'Sem conexão com o servidor.')).toBe('Sem conexão com o servidor.');
  });

  it('401 com sessão aberta encerra a sessão; o cookie vai junto e GET não leva CSRF', async () => {
    const aoExpirar = vi.fn();
    configurarSessao({ aberta: () => true, aoExpirar });
    responder(401, { status: 401 });

    await http.get('/x').catch(() => {});

    expect(aoExpirar).toHaveBeenCalledOnce();
    const [, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(init.credentials).toBe('include');
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('alteração devolve o cookie XSRF-TOKEN no cabeçalho', async () => {
    document.cookie = 'XSRF-TOKEN=abc%2D1';
    responder(200, {});

    await http.post('/x', {});

    const [, init] = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(init.headers['X-XSRF-TOKEN']).toBe('abc-1');
  });

  it('sem o cookie CSRF, pede um à API antes de alterar', async () => {
    responder(200, {});

    await http.delete('/x').catch(() => {});

    const urls = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls.map(([url]) => url);
    expect(urls).toEqual(['http://localhost:8080/api/auth/csrf', 'http://localhost:8080/x']);
  });
});

/** XMLHttpRequest falso: guarda cabeçalhos e deixa o teste simular progresso e resposta. */
class XhrFalso {
  static ultimo: XhrFalso;
  cabecalhos: Record<string, string> = {};
  withCredentials = false;
  status = 0;
  responseText = '';
  upload: { onprogress?: (e: { lengthComputable: boolean; loaded: number; total: number }) => void } = {};
  onload?: () => void;
  onerror?: () => void;
  constructor() { XhrFalso.ultimo = this; }
  open() {}
  setRequestHeader(nome: string, valor: string) { this.cabecalhos[nome] = valor; }
  send() {}
  responder(status: number, corpo: unknown) { this.status = status; this.responseText = JSON.stringify(corpo); this.onload?.(); }
}

describe('upload com progresso', () => {
  it('manda cookie e CSRF, informa o progresso e devolve o JSON', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    const progresso = vi.fn();

    const envio = http.enviarComProgresso<{ id: number }>('/up', new FormData(), progresso);
    await vi.waitFor(() => expect(XhrFalso.ultimo?.cabecalhos['X-XSRF-TOKEN']).toBe('tok'));
    const xhr = XhrFalso.ultimo;
    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 50, total: 200 });
    xhr.responder(201, { id: 9 });

    expect(await envio).toEqual({ id: 9 });
    expect(xhr.withCredentials).toBe(true);
    expect(progresso).toHaveBeenCalledWith(0.25);
  });

  it('erro vira ErroApi como nas outras chamadas', async () => {
    document.cookie = 'XSRF-TOKEN=tok';
    vi.stubGlobal('XMLHttpRequest', XhrFalso);

    const envio = falha(http.enviarComProgresso('/up', new FormData(), () => {}));
    await vi.waitFor(() => expect(XhrFalso.ultimo?.cabecalhos['X-XSRF-TOKEN']).toBe('tok'));
    XhrFalso.ultimo.responder(409, { status: 409, detail: 'Este arquivo já foi importado para o cliente.', recursoExistenteId: 3 });

    const erro = await envio;
    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro.recursoExistenteId).toBe(3);
  });
});
