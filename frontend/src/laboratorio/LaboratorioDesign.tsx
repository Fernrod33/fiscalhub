// Referência viva do design system (só em desenvolvimento, fora do build de produção): tokens e componentes.
import { useState, type ReactNode } from 'react';
import { AlertTriangle, ChevronDown, FileText, Inbox, Moon, RotateCcw, Sun, Upload } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { DataTable, type ColumnDef } from '@/components/data-table';
import { EstadoVazio } from '@/components/estado-vazio';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import { TextoTruncado } from '@/components/texto-truncado';
import { AnimatedNumber, Collapse, Reveal, Stagger, StaggerItem } from '@/components/motion';
import { useTema } from '@/app/tema';
import * as fmt from '@/lib/formatadores';
import { documentosExemplo, kpisExemplo, type DocumentoExemplo } from './fixture';

const CORES = ['background', 'surface', 'foreground', 'muted', 'muted-foreground', 'border', 'primary', 'primary-foreground',
  'accent', 'success', 'success-bg', 'warning', 'warning-bg', 'danger', 'danger-bg', 'info', 'info-bg', 'sidebar', 'sidebar-foreground'];

const colunas: ColumnDef<DocumentoExemplo>[] = [
  { accessorKey: 'arquivo', header: 'Arquivo', cell: ({ row }) => (
    <span className="flex items-center gap-2"><FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden /><TextoTruncado texto={row.original.arquivo} className="max-w-48" /></span>) },
  { accessorKey: 'tipo', header: 'Tipo' },
  { accessorKey: 'competencia', header: 'Competência', meta: { classe: 'whitespace-nowrap' } },
  { accessorKey: 'tamanho', header: 'Tamanho', cell: ({ getValue }) => fmt.tamanhoArquivo(getValue<number>()), meta: { classe: 'text-right whitespace-nowrap' } },
  { accessorKey: 'importadoEm', header: 'Importado em', cell: ({ getValue }) => fmt.dataHora(getValue<string>()), meta: { classe: 'whitespace-nowrap' } },
];

export default function LaboratorioDesign() {
  const { escuro, setTema } = useTema();
  const [rodada, setRodada] = useState(0);
  const [aberto, setAberto] = useState(false);
  return (
    <div className="mx-auto grid max-w-6xl gap-6 p-6">
      <div className="flex items-center justify-between gap-3">
        <Badge variant="outline">Referência do design system — só desenvolvimento, dados de exemplo</Badge>
        <Button variant="outline" size="sm" onClick={() => setTema(escuro ? 'claro' : 'escuro')}>
          {escuro ? <Sun aria-hidden /> : <Moon aria-hidden />} {escuro ? 'Tema claro' : 'Tema escuro'}
        </Button>
      </div>

      <CabecalhoPagina titulo="Metalúrgica Exemplo Ltda" subtitulo={<span className="num">CNPJ {fmt.cnpj('11222333000181')}</span>}
                       acoes={<Button><Upload aria-hidden /> Importar arquivo</Button>} />

      <Secao titulo="Cores semânticas">
        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-6">
          {CORES.map((c) => (
            <li key={c} className="overflow-hidden rounded-md border text-xs">
              <div className="h-10" style={{ background: `var(--${c})` }} />
              <div className="px-2 py-1 font-mono">{c}</div>
            </li>
          ))}
        </ul>
      </Secao>

      <Secao titulo="Tipografia">
        <div className="grid gap-2">
          <p className="font-serif text-2xl font-semibold">Título de página (h1) — Source Serif 4</p>
          <p className="font-serif text-lg font-semibold">Título de seção de card (h2) — Source Serif 4</p>
          <p>Texto de interface, tabelas, formulários, botões e menu — Inter 14px.</p>
          <p className="num text-lg">Números sempre em Inter tabular: {fmt.moeda(1284937.42)} · {fmt.moeda(9.9)} · {fmt.percentual(13.8093)}</p>
        </div>
      </Secao>

      <Secao titulo="Movimento: KPIs em cascata e número animado (cor só quando indica estado)">
        <Button variant="outline" size="sm" className="mb-3" onClick={() => setRodada((r) => r + 1)}>
          <RotateCcw aria-hidden /> Repetir animações
        </Button>
        <Reveal key={`r${rodada}`}>
          <Stagger as="ul" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {kpisExemplo.map((k) => (
              <StaggerItem as="li" key={k.rotulo} className="rounded-lg border bg-surface p-3 shadow-sm transition-transform duration-(--dur-base) ease-(--ease-standard) hover:-translate-y-0.5">
                <p className="text-muted-foreground">{k.rotulo}</p>
                <AnimatedNumber valor={k.valor} formatar={k.formato === 'moeda' ? fmt.moeda : fmt.inteiro}
                                className={`text-xl font-semibold ${k.estado === 'perigo' && k.valor > 0 ? 'text-danger' : ''}`} />
              </StaggerItem>
            ))}
          </Stagger>
        </Reveal>
        <div className="mt-4">
          <Button variant="ghost" size="sm" aria-expanded={aberto} aria-controls="lab-memoria" onClick={() => setAberto((a) => !a)}>
            <ChevronDown aria-hidden className={`transition-transform duration-(--dur-base) ${aberto ? 'rotate-180' : ''}`} /> Memória de cálculo (Collapse)
          </Button>
          <Collapse aberto={aberto} id="lab-memoria">
            <ol className="num mt-2 grid gap-1 rounded-md bg-muted p-3">
              <li>Valor original: {fmt.moeda(10000)}</li><li>Multa de mora (20%): {fmt.moeda(2000)}</li><li>Juros SELIC: {fmt.moeda(1380.93)}</li>
            </ol>
          </Collapse>
        </div>
      </Secao>

      <Secao titulo="Botões, campos e selos">
        <div className="flex flex-wrap items-center gap-2">
          <Button>Primário</Button><Button variant="outline">Secundário</Button><Button variant="ghost">Discreto</Button>
          <Button variant="destructive">Excluir</Button><Button disabled>Desabilitado</Button>
        </div>
        <div className="mt-4 grid max-w-sm gap-1.5">
          <Label htmlFor="lab-cnpj">CNPJ</Label>
          <Input id="lab-cnpj" placeholder="00.000.000/0000-00" />
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <Badge className="bg-info-bg text-info">Recebido</Badge><Badge className="bg-warning-bg text-warning">Processando</Badge>
          <Badge className="bg-success-bg text-success">Processado</Badge><Badge className="bg-danger-bg text-danger">Erro</Badge>
        </div>
      </Secao>

      <Secao titulo="Tabela (DataTable)">
        <DataTable rotulo="Documentos de exemplo" colunas={colunas} dados={documentosExemplo} filtro="Filtrar documentos"
                   vazio={<EstadoVazio icone={Inbox} titulo="Nenhum documento" />} />
      </Secao>

      <Secao titulo="Carregando, vazio e alerta">
        <div className="grid items-start gap-3 md:grid-cols-3">
          <div className="grid gap-2"><Skeleton className="h-4 w-3/4" /><Skeleton className="h-4 w-1/2" /><Skeleton className="h-16" /></div>
          <EstadoVazio icone={Inbox} titulo="Importe a ECD" descricao="As demonstrações aparecem depois da importação." />
          <Alert><AlertTriangle aria-hidden /><AlertTitle>Ponto de atenção</AlertTitle><AlertDescription>Débito inscrito em dívida ativa.</AlertDescription></Alert>
        </div>
      </Secao>
    </div>
  );
}

function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return <Cartao><CartaoTitulo>{titulo}</CartaoTitulo>{children}</Cartao>;
}
