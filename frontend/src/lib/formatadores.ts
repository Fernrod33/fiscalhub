/** Formatação pt-BR em um só lugar (Intl). Valores nulos viram "—". */
import { somenteDigitos } from './cnpj';

const VAZIO = '—';
const moedaFmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const percentualFmt = new Intl.NumberFormat('pt-BR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const inteiroFmt = new Intl.NumberFormat('pt-BR');
const dataFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' });
const dataHoraFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

type Num = number | null | undefined;

export const moeda = (v: Num) => (v == null ? VAZIO : moedaFmt.format(v));
export const inteiro = (v: Num) => (v == null ? VAZIO : inteiroFmt.format(v));
/** Recebe o percentual como na API (1.5 = 1,5%). */
export const percentual = (v: Num) => (v == null ? VAZIO : percentualFmt.format(v / 100));

/** "11222333000181" -> "11.222.333/0001-81". */
export function cnpj(v: string | null | undefined): string {
  const d = somenteDigitos(v ?? '');
  return d.length === 14 ? d.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5') : v || VAZIO;
}

/** Data ISO sem hora ("2024-03-01") -> "01/03/2024", sem deslocamento de fuso. */
export const data = (iso: string | null | undefined) => (iso ? dataFmt.format(new Date(`${iso.slice(0, 10)}T00:00:00Z`)) : VAZIO);

/** Data e hora ISO local ("2026-10-12T14:02:00") -> "12/10/2026, 14:02". */
export const dataHora = (iso: string | null | undefined) => (iso ? dataHoraFmt.format(new Date(iso)) : VAZIO);

/** Competência (data do 1º dia do mês, "2024-03-01") -> "03/2024". */
export const competencia = (iso: string | null | undefined) => (iso ? `${iso.slice(5, 7)}/${iso.slice(0, 4)}` : VAZIO);

export function tamanhoArquivo(bytes: Num): string {
  if (bytes == null) return VAZIO;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${inteiroFmt.format(Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1_048_576).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}
