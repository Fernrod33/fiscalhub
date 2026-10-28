import { useEffect, useState } from 'react';
import { FileDown, FileSpreadsheet, FileText } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';
import type { PedidoRelatorio, RelatorioEmitido, TipoRelatorio } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { mensagemErro } from '@/lib/http';
import { TIPOS_RELATORIO } from '@/lib/rotulos';
import { usePermissoes } from '@/features/auth/permissoes';
import { useClienteId } from '@/features/clientes/rota';
import { baixarRelatorio, useEmitirRelatorio, usePrevia, useRelatoriosEmitidos } from '../api';

/** Espera o usuário parar de mexer nos parâmetros antes de pedir a prévia de novo. */
function useAtrasado<T>(valor: T, ms = 400): T {
  const [atrasado, setAtrasado] = useState(valor);
  useEffect(() => {
    const t = setTimeout(() => setAtrasado(valor), ms);
    return () => clearTimeout(t);
  }, [valor, ms]);
  return atrasado;
}

/**
 * Aba Relatórios: tipo, período e opções; prévia do HTML do backend (o mesmo template do PDF) num iframe com sandbox
 * (sem scripts nem origem); emissão imutável e o histórico com o PDF e o Excel gravados na emissão.
 */
export default function RelatoriosPage() {
  const clienteId = useClienteId();
  const { alterar } = usePermissoes();
  const [tipo, setTipo] = useState<TipoRelatorio>('TRIBUTOS');
  const [de, setDe] = useState('');
  const [ate, setAte] = useState('');
  const [incluirDivergencias, setIncluirDivergencias] = useState(true);
  const [incluirFontes, setIncluirFontes] = useState(true);
  const anual = tipo === 'CARGA_TRIBUTARIA';
  // retrato atual dos documentos, sem período: situação fiscal, capacidade e dossiê
  const semPeriodo = tipo === 'SITUACAO_FISCAL' || tipo === 'CAPACIDADE' || tipo === 'DOSSIE_CAPAG';
  const pedido: PedidoRelatorio = {
    tipo,
    // carga: o backend usa só o ano
    de: semPeriodo || !de ? undefined : anual ? `${de}-01` : de,
    ate: semPeriodo || !ate ? undefined : anual ? `${ate}-12` : ate,
    incluirDivergencias, incluirFontes,
  };
  // texto: um objeto novo a cada render reiniciaria a espera para sempre
  const previa = usePrevia(clienteId, JSON.parse(useAtrasado(JSON.stringify(pedido))) as PedidoRelatorio);
  const emitir = useEmitirRelatorio(clienteId);
  const emitidos = useRelatoriosEmitidos(clienteId);

  const colunas: ColumnDef<RelatorioEmitido>[] = [
    { accessorKey: 'numero', header: 'Nº', cell: ({ row }) => <span className="num">{String(row.original.numero).padStart(6, '0')}</span> },
    { accessorKey: 'tipo', header: 'Tipo', cell: ({ row }) => TIPOS_RELATORIO[row.original.tipo].rotulo },
    { accessorKey: 'periodo', header: 'Período' },
    { accessorKey: 'emitidoPor', header: 'Emitido por' },
    { accessorKey: 'emitidoEm', header: 'Emitido em', cell: ({ row }) => <span className="num">{fmt.dataHora(row.original.emitidoEm)}</span> },
    { id: 'hash', header: 'Hash (PDF)', cell: ({ row }) => (
      <abbr title={`SHA-256 do PDF: ${row.original.sha256Pdf}`} className="num font-mono text-xs no-underline">{row.original.sha256Pdf.slice(0, 12)}…</abbr>) },
    { id: 'acoes', header: () => <span className="sr-only">Baixar</span>, cell: ({ row }) => {
      const r = row.original;
      const baixar = (formato: 'pdf' | 'xlsx') => baixarRelatorio(clienteId, r.id, formato)
        .catch((e) => toast.error(mensagemErro(e, 'Não foi possível baixar o relatório.')));
      return (
        <div className="flex justify-end gap-1">
          <Button variant="ghost" size="sm" onClick={() => baixar('pdf')} aria-label={`Baixar PDF do relatório ${r.numero}`}><FileDown aria-hidden /> PDF</Button>
          <Button variant="ghost" size="sm" onClick={() => baixar('xlsx')} aria-label={`Baixar Excel do relatório ${r.numero}`}><FileSpreadsheet aria-hidden /> Excel</Button>
        </div>
      );
    } },
  ];

  const aoEmitir = () => emitir.mutate(pedido, {
    onSuccess: (r) => toast.success(`Relatório nº ${r.numero} emitido.`),
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível emitir o relatório.')),
  });

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-novo" className="grid gap-4">
        <CartaoTitulo id="titulo-novo" acoes={alterar && (
          <Button onClick={aoEmitir} disabled={emitir.isPending}><FileText aria-hidden /> {emitir.isPending ? 'Emitindo…' : 'Emitir'}</Button>)}>
          Novo relatório
        </CartaoTitulo>
        <fieldset className="grid gap-2">
          <legend className="mb-1 text-sm font-medium">Tipo</legend>
          <div className="grid gap-2 md:grid-cols-3">
            {(Object.keys(TIPOS_RELATORIO) as TipoRelatorio[]).map((t) => (
              <label key={t} htmlFor={`tipo-${t}`} className="grid cursor-pointer grid-cols-[auto_1fr] gap-x-2 gap-y-0.5 rounded-md border p-3 has-checked:border-primary has-checked:bg-accent">
                <input id={`tipo-${t}`} type="radio" name="tipo" value={t} checked={tipo === t} onChange={() => { setTipo(t); setDe(''); setAte(''); }}
                       className="row-span-2 mt-0.5 accent-(--primary)" />
                <span className="font-medium">{TIPOS_RELATORIO[t].rotulo}</span>
                <span className="text-sm text-muted-foreground">{TIPOS_RELATORIO[t].descricao}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="flex flex-wrap items-end gap-4">
          {!semPeriodo && (
            <>
              <label className="grid gap-1 text-sm">
                <span>{anual ? 'Exercício inicial' : 'Competência inicial'}</span>
                {anual
                  ? <input type="number" min={2000} max={2100} value={de} onChange={(e) => setDe(e.target.value)} placeholder="todos"
                           className="h-9 w-32 rounded-md border bg-background px-2" />
                  : <input type="month" value={de} onChange={(e) => setDe(e.target.value)} className="h-9 rounded-md border bg-background px-2" />}
              </label>
              <label className="grid gap-1 text-sm">
                <span>{anual ? 'Exercício final' : 'Competência final'}</span>
                {anual
                  ? <input type="number" min={2000} max={2100} value={ate} onChange={(e) => setAte(e.target.value)} placeholder="todos"
                           className="h-9 w-32 rounded-md border bg-background px-2" />
                  : <input type="month" value={ate} onChange={(e) => setAte(e.target.value)} className="h-9 rounded-md border bg-background px-2" />}
              </label>
            </>
          )}
          {tipo === 'TRIBUTOS' && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={incluirDivergencias} onChange={(e) => setIncluirDivergencias(e.target.checked)} className="accent-(--primary)" />
              Incluir divergências
            </label>
          )}
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={incluirFontes} onChange={(e) => setIncluirFontes(e.target.checked)} className="accent-(--primary)" />
            Incluir anexo de fontes
          </label>
        </div>
      </Cartao>

      <Cartao aria-labelledby="titulo-previa" className="grid gap-2">
        <CartaoTitulo id="titulo-previa">Prévia</CartaoTitulo>
        <p className="text-sm text-muted-foreground">O mesmo layout do PDF; o número, a paginação e o hash entram na emissão.</p>
        {previa.isError
          ? <p role="alert" className="text-sm text-danger">{mensagemErro(previa.error, 'Não foi possível gerar a prévia.')}</p>
          : previa.data === undefined
            ? <Skeleton className="h-[60vh]" />
            // sandbox vazio: sem scripts, formulários nem acesso à origem do app
            : <iframe title="Prévia do relatório" sandbox="" srcDoc={previa.data} aria-busy={previa.isFetching}
                      className="h-[75vh] w-full rounded-md border bg-white" />}
      </Cartao>

      <Cartao aria-labelledby="titulo-emitidos" className="grid gap-3">
        <CartaoTitulo id="titulo-emitidos">Relatórios emitidos</CartaoTitulo>
        <DataTable rotulo="Relatórios emitidos" colunas={colunas} dados={emitidos.data} carregando={emitidos.isPending}
                   vazio={<EstadoVazio icone={FileText} titulo="Nenhum relatório emitido"
                                       descricao="Cada emissão gera um número, grava os dados usados e guarda o PDF e o Excel: baixar devolve sempre o mesmo arquivo." />} />
      </Cartao>
    </Reveal>
  );
}
