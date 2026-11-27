import { QueryClient } from '@tanstack/react-query';
import { ErroApi } from './http';

/** Repetição só para falhas de rede: erro da API (4xx/5xx com ProblemDetail) não muda ao repetir. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (tentativas, erro) => !(erro instanceof ErroApi) && tentativas < 2,
      refetchOnWindowFocus: false,
    },
    mutations: { retry: false },
  },
});
