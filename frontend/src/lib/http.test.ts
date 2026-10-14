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
