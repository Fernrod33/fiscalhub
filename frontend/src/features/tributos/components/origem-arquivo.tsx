import { Link } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { ValorOrigem } from '@/components/valor-origem';
import * as fmt from '@/lib/formatadores';

/** Valor de uma apuração com a origem já conhecida: arquivo, registro e linha (sem nova consulta). */
export function ValorComOrigem({ clienteId, rotulo, valor, arquivo, registro, linha, destaque }: {
  clienteId: number; rotulo: string; valor: number | null | undefined; arquivo: string;
  registro?: string; linha?: number; destaque?: boolean;
}) {
  const texto = fmt.moeda(valor);
  if (!registro) return <span className="num block px-1 text-right">{texto}</span>;
  return (
    <ValorOrigem rotulo={`Origem de ${rotulo}: ${texto}`} texto={texto} destaque={destaque}>
      {() => (
        <div className="grid gap-2 text-sm">
          <p className="font-semibold">{rotulo}</p>
          <Link to={`/clientes/${clienteId}/importacoes`} viewTransition
                className="flex items-center gap-1.5 text-primary underline-offset-4 hover:underline">
            <FileText className="size-4 shrink-0" aria-hidden /><span className="truncate">{arquivo}</span>
          </Link>
          <p className="num">Registro {registro} · linha {fmt.inteiro(linha)}</p>
        </div>
      )}
    </ValorOrigem>
  );
}
