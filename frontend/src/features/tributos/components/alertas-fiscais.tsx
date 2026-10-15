import { Link } from 'react-router-dom';
import { Selo } from '@/components/selo';
import type { AlertaFiscal, Inconsistencia } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { SEVERIDADES } from '@/lib/rotulos';

/**
 * Alertas das escriturações fiscais vigentes (fórmulas que não fecham, contagem) e conferências entre arquivos
 * (ECD × ECF). São as mesmas inconsistências da gaveta de cada documento, reunidas por cliente.
 */
export function AlertasFiscais({ clienteId, alertas }: { clienteId: number; alertas: AlertaFiscal[] }) {
  return (
    <ul className="grid gap-2">
      {alertas.map((a, i) => {
        const severidade = SEVERIDADES[a.severidade as Inconsistencia['severidade']];
        return (
          <li key={i} className="grid gap-1 rounded-md border bg-surface p-3">
            <div className="flex flex-wrap items-center gap-2">
              <Selo tom={severidade.tom}>{severidade.rotulo}</Selo>
              <Link to={`/clientes/${clienteId}/importacoes`} viewTransition className="text-sm text-primary underline-offset-4 hover:underline">
                {a.arquivo}
              </Link>
            </div>
            <p>{a.mensagem}</p>
            {(a.registro || a.linha) && (
              <p className="num text-xs text-muted-foreground">
                {[a.registro && `Registro ${a.registro}`, a.linha && `linha ${fmt.inteiro(a.linha)}`].filter(Boolean).join(' · ')}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
