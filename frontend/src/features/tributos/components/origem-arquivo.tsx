import type { OrigemDocumento } from '@/lib/api/tipos';
import { ValorDocumento } from '@/features/situacao-fiscal/components/origem-documento';

/** Valor de uma apuração com a origem já conhecida (arquivo, registro e linha do SPED), sem nova consulta. */
export function ValorComOrigem({ clienteId, rotulo, valor, arquivo, registro, linha, destaque }: {
  clienteId: number; rotulo: string; valor: number | null | undefined; arquivo: string;
  registro?: string; linha?: number; destaque?: boolean;
}) {
  const origem = { arquivo, registro, linha, pagina: null, trecho: null } as unknown as OrigemDocumento;
  return <ValorDocumento clienteId={clienteId} rotulo={rotulo} valor={valor} origens={registro ? [origem] : []} destaque={destaque} />;
}
