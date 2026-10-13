import axios from 'axios';

const client = axios.create({
  baseURL: 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

client.interceptors.request.use((config) => {
  const stored = localStorage.getItem('fiscalhub_user');
  if (stored) {
    const user = JSON.parse(stored);
    if (user?.token) {
      config.headers.Authorization = `Bearer ${user.token}`;
    }
  }
  return config;
});

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('fiscalhub_user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

/** Mensagem de erro devolvida pela API (ProblemDetail, RFC 9457: campo `detail`). */
export function mensagemErro(err: unknown, padrao: string): string {
  return (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? padrao;
}

export default client;
