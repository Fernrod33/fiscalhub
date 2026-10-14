/**
 * Cliente HTTP único (fetch nativo): sessão por cookie, CSRF, normalização de erro ProblemDetail (RFC 9457)
 * e tratamento de 401. Nenhum outro módulo chama fetch para a API.
 * O JWT viaja num cookie httpOnly; alterações levam o valor do cookie XSRF-TOKEN no cabeçalho X-XSRF-TOKEN.
 * Frontend e API precisam estar no mesmo site (cookies não distinguem porta).
 */
const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

/** Erro da API já normalizado a partir do ProblemDetail. */
export class ErroApi extends Error {
  readonly status: number;
  readonly tipo?: string;
  readonly correlacao?: string;
  readonly recursoExistenteId?: number;
  readonly campos?: Record<string, string>;

  constructor(status: number, problema: Partial<Problema>) {
    super(problema.detail || problema.title || `Erro ${status}`);
    this.name = 'ErroApi';
    this.status = status;
    this.tipo = problema.type;
    this.correlacao = problema.correlacao;
    this.recursoExistenteId = problema.recursoExistenteId;
    this.campos = problema.errors;
  }
}

type Problema = {
  type: string;
  title: string;
  status: number;
  detail: string;
  correlacao: string;
  recursoExistenteId: number;
  errors: Record<string, string>;
};

/** Mensagem para o usuário: detail do ProblemDetail ou o texto padrão (falha de rede, por exemplo). */
export function mensagemErro(err: unknown, padrao: string): string {
  return err instanceof ErroApi ? err.message : padrao;
}

type Sessao = { aberta: () => boolean; aoExpirar: () => void };
let sessao: Sessao = { aberta: () => false, aoExpirar: () => {} };

/** Ligado pelo módulo de sessão: se há sessão aberta e o que fazer quando a API responde 401. */
export function configurarSessao(s: Sessao) {
  sessao = s;
}

const lerCookieCsrf = () => /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie)?.[1];

/** Token CSRF para métodos que alteram estado; na primeira vez pede o cookie à API. */
async function tokenCsrf(): Promise<string | undefined> {
  if (!lerCookieCsrf()) await fetch(API_URL + '/api/auth/csrf', { credentials: 'include' });
  const valor = lerCookieCsrf();
  return valor && decodeURIComponent(valor);
}

async function requisitar(metodo: string, caminho: string, corpo?: unknown): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };
  if (metodo !== 'GET') {
    const csrf = await tokenCsrf();
    if (csrf) headers['X-XSRF-TOKEN'] = csrf;
  }
  let body: BodyInit | undefined;
  if (corpo instanceof FormData) {
    body = corpo; // o navegador define o multipart/form-data com boundary
  } else if (corpo !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(corpo);
  }

  const resposta = await fetch(API_URL + caminho, { method: metodo, headers, body, credentials: 'include' });
  if (resposta.ok) return resposta;

  const problema = await resposta.json().catch(() => ({}));
  if (resposta.status === 401 && sessao.aberta()) sessao.aoExpirar();
  throw new ErroApi(resposta.status, problema);
}

async function json<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
  const resposta = await requisitar(metodo, caminho, corpo);
  return (resposta.status === 204 ? undefined : await resposta.json()) as T;
}

export const http = {
  get: <T>(caminho: string) => json<T>('GET', caminho),
  post: <T>(caminho: string, corpo?: unknown) => json<T>('POST', caminho, corpo),
  put: <T>(caminho: string, corpo?: unknown) => json<T>('PUT', caminho, corpo),
  patch: <T>(caminho: string, corpo?: unknown) => json<T>('PATCH', caminho, corpo),
  delete: (caminho: string) => json<void>('DELETE', caminho),
  /** Baixa um arquivo e devolve o conteúdo com o nome sugerido pelo servidor. */
  async baixar(caminho: string): Promise<{ blob: Blob; nome: string }> {
    const resposta = await requisitar('GET', caminho);
    const disposicao = resposta.headers.get('Content-Disposition') ?? '';
    const nome = decodeURIComponent(/filename\*=UTF-8''([^;]+)/.exec(disposicao)?.[1] ?? /filename="([^"]+)"/.exec(disposicao)?.[1] ?? 'arquivo');
    return { blob: await resposta.blob(), nome };
  },
};
