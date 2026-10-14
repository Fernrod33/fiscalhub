import type { ColunasPlanilha } from '@/lib/api/tipos';

export type Colunas = Partial<ColunasPlanilha>;

/** Campos do balancete que o usuário aponta na planilha (só aponta: nenhum valor é digitado). */
export const CAMPOS: { campo: keyof ColunasPlanilha; rotulo: string; obrigatorio?: boolean }[] = [
  { campo: 'codigo', rotulo: 'Código da conta', obrigatorio: true },
  { campo: 'descricao', rotulo: 'Descrição da conta', obrigatorio: true },
  { campo: 'saldoInicial', rotulo: 'Saldo inicial' },
  { campo: 'dcInicial', rotulo: 'D/C do saldo inicial' },
  { campo: 'debitos', rotulo: 'Débitos' },
  { campo: 'creditos', rotulo: 'Créditos' },
  { campo: 'saldoFinal', rotulo: 'Saldo final', obrigatorio: true },
  { campo: 'dcFinal', rotulo: 'D/C do saldo final' },
];

/** Coluna 0 -> "A", 27 -> "AB" (como na planilha). */
export const letra = (i: number) => (i < 26 ? '' : String.fromCharCode(64 + Math.floor(i / 26))) + String.fromCharCode(65 + (i % 26));

export const completo = (c: Colunas) => CAMPOS.every((campo) => !campo.obrigatorio || c[campo.campo] !== undefined);
