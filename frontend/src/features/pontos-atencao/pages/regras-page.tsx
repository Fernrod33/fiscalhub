import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { Reveal } from '@/components/motion';
import { Selo } from '@/components/selo';
import type { ConfiguracaoRegra } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { useAlterarRegra, useConfiguracaoRegras } from '../api';
import { CATEGORIAS, SEVERIDADES } from '../rotulos';

/** Uma regra: severidade e parâmetros do escritório ao lado do padrão (dado versionado). */
function Regra({ r }: { r: ConfiguracaoRegra }) {
  const alterar = useAlterarRegra();
  const [severidade, setSeveridade] = useState(r.severidade);
  const [parametros, setParametros] = useState<Record<string, unknown>>(r.parametros as Record<string, unknown>);
  const padrao = r.parametrosPadrao as Record<string, unknown>;
  return (
    <form aria-label={`Regra ${r.codigo}`} className="grid gap-2 border-b pb-3 last:border-0" onSubmit={(e) => {
      e.preventDefault();
      alterar.mutate({ codigo: r.codigo, parametros, severidade }, {
        onSuccess: () => toast.success(`Regra ${r.codigo} salva.`), onError: (err) => toast.error(mensagemErro(err, 'Não foi possível salvar.')) });
    }}>
      <p className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{r.codigo} · {r.titulo}</span>
        <Selo tom="info">{CATEGORIAS[r.categoria]}</Selo>
        {!r.conferido && <Selo tom="warning">fundamento a conferir</Selo>}
        <span className="text-xs text-muted-foreground">versão {r.versao}{r.atualizadoPor && ` · alterada por ${r.atualizadoPor} em ${fmt.dataHora(r.atualizadoEm)}`}</span>
      </p>
      <p className="text-xs text-muted-foreground">{r.fundamento}</p>
      <div className="flex flex-wrap items-end gap-3 text-sm">
        <label className="grid gap-1">Severidade (padrão: {SEVERIDADES[r.severidadePadrao].rotulo})
          <select value={severidade} onChange={(e) => setSeveridade(e.target.value as ConfiguracaoRegra['severidade'])} className="h-9 rounded-md border bg-background px-2">
            {Object.entries(SEVERIDADES).map(([k, v]) => <option key={k} value={k}>{v.rotulo}</option>)}
          </select>
        </label>
        {Object.entries(padrao).filter(([, v]) => typeof v === 'number').map(([k, v]) => (
          <label key={k} className="grid gap-1">{k} (padrão: {String(v)})
            <input type="number" step="any" min={0} value={String(parametros[k] ?? v)}
                   onChange={(e) => setParametros((p) => ({ ...p, [k]: Number(e.target.value) }))} className="num h-9 w-32 rounded-md border bg-background px-2" />
          </label>
        ))}
        <Button type="submit" size="sm" disabled={alterar.isPending}>Salvar</Button>
      </div>
    </form>
  );
}

/** Configurações → Regras: parâmetros dos pontos de atenção por escritório (ADMIN), com o padrão, a versão e a auditoria. */
export default function RegrasPage() {
  const regras = useConfiguracaoRegras();
  return (
    <Reveal>
      <Cartao aria-labelledby="titulo-regras" className="grid gap-3">
        <CartaoTitulo id="titulo-regras">Regras dos pontos de atenção</CartaoTitulo>
        <p className="text-sm text-muted-foreground">Os limites e a severidade valem para todos os clientes do escritório; cada alteração fica na auditoria.</p>
        {regras.isPending ? <Skeleton className="h-64" /> : (regras.data ?? []).map((r) => <Regra key={r.codigo} r={r} />)}
      </Cartao>
    </Reveal>
  );
}
