import { CheckCircle2 } from 'lucide-react';
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { Skeleton } from '@/components/ui/skeleton';
import { Selo } from '@/components/selo';
import { EstadoVazio } from '@/components/estado-vazio';
import type { Inconsistencia } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { SEVERIDADES } from '@/lib/rotulos';
import { useInconsistencias } from '../api';

const ORDEM: Inconsistencia['severidade'][] = ['ERRO', 'ALERTA', 'INFO'];

/** Gaveta com as inconsistências do arquivo, agrupadas por severidade, cada uma com registro e linha. */
export function PainelInconsistencias({ documentoId, arquivo, aberto, aoMudar }: {
  documentoId: number; arquivo: string; aberto: boolean; aoMudar: (aberto: boolean) => void;
}) {
  const inconsistencias = useInconsistencias(documentoId, aberto);
  const grupos = ORDEM.map((s) => ({ severidade: s, itens: (inconsistencias.data ?? []).filter((i) => i.severidade === s) }))
    .filter((g) => g.itens.length > 0);

  return (
    <Sheet open={aberto} onOpenChange={aoMudar}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Inconsistências</SheetTitle>
          <SheetDescription className="break-all">{arquivo}</SheetDescription>
        </SheetHeader>
        <div className="grid gap-5 px-4 pb-6">
          {inconsistencias.isPending
            ? <div className="grid gap-2">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>
            : grupos.length === 0
              ? <EstadoVazio icone={CheckCircle2} titulo="Nenhuma inconsistência" descricao="O arquivo passou em todas as validações." />
              : grupos.map((g) => (
                <section key={g.severidade} aria-labelledby={`sev-${g.severidade}`} className="grid gap-2">
                  <h3 id={`sev-${g.severidade}`} className="flex items-center gap-2 font-semibold">
                    <Selo tom={SEVERIDADES[g.severidade].tom}>{SEVERIDADES[g.severidade].plural}</Selo>
                    <span className="num text-muted-foreground">{fmt.inteiro(g.itens.length)}</span>
                  </h3>
                  <ul className="grid gap-2">
                    {g.itens.map((i) => (
                      <li key={i.id} className="rounded-md border bg-surface p-3">
                        <p>{i.mensagem}</p>
                        {(i.registro || i.linha) && (
                          <p className="num mt-1 text-xs text-muted-foreground">
                            {[i.registro && `Registro ${i.registro}`, i.linha && `linha ${fmt.inteiro(i.linha)}`].filter(Boolean).join(' · ')}
                          </p>
                        )}
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}
