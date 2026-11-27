import { Selo } from '@/components/selo';
import type { PontoAtencao } from '@/lib/api/tipos';
import { SEVERIDADES } from '../rotulos';

/** Severidade com ícone e texto: o significado não depende só da cor. */
export function SeloSeveridade({ severidade }: { severidade: PontoAtencao['severidade'] }) {
  const s = SEVERIDADES[severidade];
  return <Selo tom={s.tom}><s.Icone aria-hidden className="mr-1 inline size-3" />{s.rotulo}</Selo>;
}
