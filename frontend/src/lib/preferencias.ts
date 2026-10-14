/** Preferências de interface no navegador (tema, menu recolhido). Falhas de storage não quebram a tela. */
const PREFIXO = 'fiscalhub.';

export function lerPreferencia<T>(chave: string, padrao: T): T {
  try {
    const valor = localStorage.getItem(PREFIXO + chave);
    return valor === null ? padrao : (JSON.parse(valor) as T);
  } catch {
    return padrao;
  }
}

export function salvarPreferencia(chave: string, valor: unknown) {
  try {
    localStorage.setItem(PREFIXO + chave, JSON.stringify(valor));
  } catch {
    // modo privado ou storage bloqueado: só não lembra a preferência
  }
}
