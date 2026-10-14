/**
 * Tokens de movimento: fonte única. O CSS os recebe como variáveis (--dur-*, --ease-*) por aplicarTokensDeMovimento(),
 * e os componentes Motion usam as mesmas constantes.
 */
export const duracao = { fast: 120, base: 200, slow: 320 } as const;

export const curva = {
  standard: [0.2, 0, 0, 1],
  emphasized: [0.3, 0, 0, 1],
} as const;

/** Em segundos, para a biblioteca Motion. */
export const segundos = (ms: number) => ms / 1000;

/** Publica os tokens como variáveis CSS na raiz (chamado uma vez, no main). */
export function aplicarTokensDeMovimento(raiz: HTMLElement = document.documentElement) {
  for (const [nome, ms] of Object.entries(duracao)) raiz.style.setProperty(`--dur-${nome}`, `${ms}ms`);
  for (const [nome, [x1, y1, x2, y2]] of Object.entries(curva)) {
    raiz.style.setProperty(`--ease-${nome}`, `cubic-bezier(${x1}, ${y1}, ${x2}, ${y2})`);
  }
}
