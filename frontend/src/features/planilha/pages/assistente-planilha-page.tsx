import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao } from '@/components/cartao';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import { useMovimentoReduzido } from '@/components/motion/use-movimento-reduzido';
import type { PreviaPlanilha } from '@/lib/api/tipos';
import { mensagemErro } from '@/lib/http';
import { PLANOS_REFERENCIAIS } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { curva, duracao, segundos } from '@/styles/motion';
import { useDocumento, useStatusDocumento } from '@/features/documentos/api';
import { LinhaDoTempo } from '@/features/documentos/components/linha-do-tempo';
import { StatusDocumento } from '@/features/documentos/components/status-documento';
import { useMapearPlanilha, usePreviaPlanilha } from '../api';
import { completo, type Colunas } from '../campos';
import { PassoColunas } from '../components/passo-colunas';
import { PassoDepara } from '../components/passo-depara';

const PASSOS = ['Colunas', 'Período', 'De-para'] as const;

/** Assistente do balancete em planilha: colunas → período → de-para referencial, com transição entre os passos. */
export default function AssistentePlanilhaPage() {
  const documentoId = Number(useParams().documentoId);
  const documento = useDocumento(documentoId);
  const [aba, setAba] = useState<string>();
  const previa = usePreviaPlanilha(documentoId, aba);

  return (
    <div className="grid gap-5">
      <CabecalhoPagina titulo="Mapear balancete"
                       subtitulo={documento.data ? documento.data.nomeOriginalArquivo : 'Carregando…'}
                       acoes={documento.data && <StatusDocumento status={documento.data.status} />} />
      {previa.isPending || !documento.data
        ? <Skeleton className="h-96" />
        : previa.error || !previa.data
          ? <p className="text-danger">{mensagemErro(previa.error, 'Não foi possível ler a planilha.')}</p>
          : <Assistente key={previa.data.aba} documentoId={documentoId} previa={previa.data} aoMudarAba={setAba}
                        jaProcessada={['PROCESSADO', 'PROCESSADO_COM_ALERTAS'].includes(documento.data.status)}
                        exercicio={Number((documento.data.periodoFim ?? `${new Date().getFullYear()}`).slice(0, 4))}
                        semCliente={!documento.data.clienteId} />}
    </div>
  );
}

function Assistente({ documentoId, previa, aoMudarAba, jaProcessada, exercicio, semCliente }: {
  documentoId: number; previa: PreviaPlanilha; aoMudarAba: (aba: string) => void; jaProcessada: boolean; exercicio: number;
  semCliente: boolean;
}) {
  const reduzido = useMovimentoReduzido();
  const sugestao = previa.sugestao;
  const [passo, setPasso] = useState(jaProcessada ? 2 : 0);
  const [linhaCabecalho, setLinhaCabecalho] = useState(sugestao.linhaCabecalho);
  const [colunas, setColunas] = useState<Colunas>(sugestao.colunas ?? {});
  const [periodo, setPeriodo] = useState({ inicio: previa.periodoInicio ?? '', fim: previa.periodoFim ?? '' });
  const [plano, setPlano] = useState(sugestao.planoReferencial);
  const [enviado, setEnviado] = useState(false);
  const mapear = useMapearPlanilha(documentoId);
  const status = useStatusDocumento(enviado ? documentoId : undefined);
  const processando = enviado && !['PROCESSADO', 'PROCESSADO_COM_ALERTAS', 'ERRO', 'AGUARDANDO_MAPEAMENTO'].includes(status.data?.status ?? '');

  const salvar = () => mapear.mutate({
    aba: previa.aba, linhaCabecalho, colunas: colunas as Required<Colunas>,
    periodoInicio: periodo.inicio, periodoFim: periodo.fim, planoReferencial: plano,
  }, {
    onSuccess: () => setEnviado(true),
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível salvar o mapeamento.')),
  });

  const movimento = reduzido
    ? { initial: { opacity: 0 }, animate: { opacity: 1 }, exit: { opacity: 0 } }
    : { initial: { opacity: 0, x: 16 }, animate: { opacity: 1, x: 0 }, exit: { opacity: 0, x: -16 } };

  if (semCliente) {
    return <Cartao><p>Confirme primeiro o cliente deste arquivo na <Link to="/importacoes" className="text-primary underline">central de importação</Link>.</p></Cartao>;
  }
  return (
    <Cartao className="grid gap-5">
      <nav aria-label="Passos do assistente">
        <ol className="flex flex-wrap gap-2">
          {PASSOS.map((p, i) => (
            <li key={p} aria-current={i === passo ? 'step' : undefined}
                className={cn('rounded-full border px-3 py-1 text-xs font-medium', i === passo ? 'border-primary bg-primary text-primary-foreground'
                  : i < passo ? 'border-primary text-primary' : 'text-muted-foreground')}>
              {i + 1}. {p}
            </li>
          ))}
        </ol>
      </nav>

      {previa.abas.length > 1 && passo === 0 && (
        <div className="grid max-w-xs gap-1.5">
          <Label htmlFor="aba">Aba</Label>
          <Select value={previa.aba} onValueChange={aoMudarAba}>
            <SelectTrigger id="aba"><SelectValue /></SelectTrigger>
            <SelectContent>{previa.abas.map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}</SelectContent>
          </Select>
        </div>
      )}

      <AnimatePresence mode="wait" initial={false}>
        <motion.div key={passo} {...movimento} transition={{ duration: segundos(duracao.base), ease: curva.standard }}>
          {passo === 0 && (
            <PassoColunas linhas={previa.linhas} linhaCabecalho={linhaCabecalho} colunas={colunas}
                          aoMudarCabecalho={setLinhaCabecalho} aoMudarColunas={setColunas} aoContinuar={() => setPasso(1)} />
          )}
          {passo === 1 && (
            <div className="grid gap-4">
              {sugestao.reconhecido && <p className="text-muted-foreground">Formato já conhecido deste cliente: as colunas vieram do último mapeamento.</p>}
              <div className="grid max-w-2xl gap-3 sm:grid-cols-3">
                <div className="grid gap-1.5">
                  <Label htmlFor="periodo-inicio">Início do período</Label>
                  <Input id="periodo-inicio" type="date" value={periodo.inicio} onChange={(e) => setPeriodo({ ...periodo, inicio: e.target.value })} />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="periodo-fim">Fim do período</Label>
                  <Input id="periodo-fim" type="date" value={periodo.fim} onChange={(e) => setPeriodo({ ...periodo, fim: e.target.value })} />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="plano">Plano referencial</Label>
                  <Select value={plano} onValueChange={setPlano}>
                    <SelectTrigger id="plano"><SelectValue /></SelectTrigger>
                    <SelectContent>{Object.entries(PLANOS_REFERENCIAIS).map(([v, r]) => <SelectItem key={v} value={v}>{r}</SelectItem>)}</SelectContent>
                  </Select>
                </div>
              </div>
              {enviado && <LinhaDoTempo status={status.data} />}
              <div className="flex justify-between gap-2">
                <Button variant="ghost" onClick={() => setPasso(0)} disabled={processando}>Voltar</Button>
                {enviado && !processando && ['PROCESSADO', 'PROCESSADO_COM_ALERTAS'].includes(status.data?.status ?? '')
                  ? <Button onClick={() => setPasso(2)}>Continuar para o de-para</Button>
                  : <Button disabled={!periodo.inicio || !periodo.fim || !completo(colunas) || mapear.isPending || processando} onClick={salvar}>
                      Salvar e processar
                    </Button>}
              </div>
            </div>
          )}
          {passo === 2 && (
            <PassoDepara documentoId={documentoId} exercicio={Number((periodo.fim || `${exercicio}`).slice(0, 4))}
                         aoSalvar={() => setEnviado(true)} />
          )}
        </motion.div>
      </AnimatePresence>
      {passo === 2 && enviado && <LinhaDoTempo status={status.data} />}
    </Cartao>
  );
}
