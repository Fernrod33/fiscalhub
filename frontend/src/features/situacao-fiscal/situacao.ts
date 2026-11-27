import type { Debito, OrigemDocumento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { ORGAO_CREDOR } from '@/lib/rotulos';

/** Onde no documento: página e linha (PDF) ou registro e linha (SPED, .dec). */
export function local(o: OrigemDocumento) {
  return o.pagina != null ? `Página ${o.pagina} · linha ${fmt.inteiro(o.linha)}` : `Registro ${o.registro ?? '—'} · linha ${fmt.inteiro(o.linha)}`;
}

/** Total dos débitos por órgão (só os órgãos com débito). */
export function totaisPorOrgao(debitos: Debito[]) {
  return (Object.keys(ORGAO_CREDOR) as Debito['orgaoCredor'][])
    .map((o) => ({ orgao: o, total: debitos.filter((d) => d.orgaoCredor === o).reduce((t, d) => t + (d.valorTotal ?? 0), 0) }))
    .filter((t) => t.total > 0);
}
