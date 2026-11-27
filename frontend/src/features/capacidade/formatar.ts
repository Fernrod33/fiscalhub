import type { CriterioCapacidade } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';

/** Valor cru do backend no formato da coluna (só exibição: nenhum cálculo aqui). */
export function valorCriterio(c: Pick<CriterioCapacidade, 'valor' | 'formato'>): string {
  if (c.valor == null) return '—';
  const n = Number(c.valor);
  if (Number.isNaN(n)) return c.valor;
  if (c.formato === 'MOEDA') return fmt.moeda(n);
  if (c.formato === 'PERCENTUAL') return fmt.percentual(n);
  if (c.formato === 'INTEIRO') return fmt.inteiro(n);
  return c.valor;
}
