import { Link } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { ValorOrigem } from '@/components/valor-origem';
import type { OrigemDocumento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { TIPOS_DOCUMENTO } from '@/lib/rotulos';
import { local } from '../situacao';

/** Lista de origens de um valor: arquivo (link para as importações), página/registro, linha e o trecho lido. */
export function ListaOrigens({ clienteId, origens }: { clienteId: number; origens: OrigemDocumento[] }) {
  return (
    <ul className="grid gap-2" aria-label="Origens do valor">
      {origens.map((o, i) => (
        <li key={`${o.arquivo}-${o.linha}-${i}`} className="grid gap-0.5 rounded-md border p-2 text-sm">
          <Link to={`/clientes/${clienteId}/importacoes`} viewTransition
                className="flex min-w-0 items-center gap-1.5 text-primary underline-offset-4 hover:underline">
            <FileText className="size-4 shrink-0" aria-hidden /><span className="truncate">{o.arquivo}</span>
          </Link>
          <p className="num">{local(o)}</p>
          {o.tipoDocumento && (
            <p className="text-xs text-muted-foreground">{TIPOS_DOCUMENTO[o.tipoDocumento as keyof typeof TIPOS_DOCUMENTO] ?? o.tipoDocumento}</p>
          )}
          {o.trecho && <p className="num break-words text-xs">“{o.trecho}”</p>}
        </li>
      ))}
    </ul>
  );
}

/** Um valor que abre as origens num popover. Sem origem: só o valor. */
export function ValorDocumento({ clienteId, rotulo, valor, origens, destaque }: {
  clienteId: number; rotulo: string; valor: number | null | undefined; origens: OrigemDocumento[] | undefined; destaque?: boolean;
}) {
  const texto = fmt.moeda(valor);
  if (!origens?.length) return <span className="num block px-1 text-right">{texto}</span>;
  return (
    <ValorOrigem rotulo={`Origem de ${rotulo}: ${texto}`} texto={texto} destaque={destaque}>
      {() => (
        <div className="grid gap-2">
          <p className="text-sm font-semibold">{rotulo}</p>
          <ListaOrigens clienteId={clienteId} origens={origens} />
        </div>
      )}
    </ValorOrigem>
  );
}
