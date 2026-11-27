/** CNPJ: única implementação no frontend (mesmo algoritmo do CnpjValidationUtils do backend). */
export const somenteDigitos = (valor: string) => valor.replace(/\D/g, '');

export function cnpjValido(valor: string): boolean {
  const d = somenteDigitos(valor);
  if (d.length !== 14 || /^(\d)\1{13}$/.test(d)) return false;
  const digito = (base: string, pesos: number[]) => {
    const resto = [...base].reduce((soma, c, i) => soma + Number(c) * pesos[i], 0) % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  const p1 = digito(d.slice(0, 12), [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  const p2 = digito(d.slice(0, 13), [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  return p1 === Number(d[12]) && p2 === Number(d[13]);
}
