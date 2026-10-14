import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, ListChecks, RotateCw, Table2, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Confirmar } from '@/components/confirmar';
import type { Documento } from '@/lib/api/tipos';
import { mensagemErro } from '@/lib/http';
import { STATUS_FINAL } from '@/lib/rotulos';
import { usePermissoes } from '@/features/auth/permissoes';
import { baixarDocumento, useExcluirDocumento, useReprocessarDocumento } from '../api';
import { PainelInconsistencias } from './painel-inconsistencias';

type DocumentoAcoes = Pick<Documento, 'id' | 'nomeOriginalArquivo' | 'status' | 'tipo'>;

function Acao({ rotulo, children, ...props }: { rotulo: string; children: React.ReactNode } & React.ComponentProps<typeof Button>) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={rotulo} {...props}>{children}</Button>
      </TooltipTrigger>
      <TooltipContent>{rotulo}</TooltipContent>
    </Tooltip>
  );
}

/** Ações de um documento (mesmas na central e na aba do cliente), respeitando o papel. */
export function AcoesDocumento({ documento }: { documento: DocumentoAcoes }) {
  const { alterar } = usePermissoes();
  const reprocessar = useReprocessarDocumento();
  const excluir = useExcluirDocumento();
  const [inconsistencias, setInconsistencias] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const parado = STATUS_FINAL.includes(documento.status);
  const nome = documento.nomeOriginalArquivo;

  return (
    <div className="flex justify-end gap-0.5">
      {documento.tipo === 'BALANCETE' && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" asChild>
              <Link to={`/importacoes/${documento.id}/planilha`} viewTransition aria-label={`Mapear planilha ${nome}`}><Table2 aria-hidden /></Link>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Mapear colunas e de-para</TooltipContent>
        </Tooltip>
      )}
      <Acao rotulo={`Inconsistências de ${nome}`} disabled={!parado} onClick={() => setInconsistencias(true)}><ListChecks aria-hidden /></Acao>
      {alterar && (
        <Acao rotulo={`Reprocessar ${nome}`} disabled={!parado || reprocessar.isPending}
              onClick={() => reprocessar.mutate(documento.id, {
                onSuccess: () => toast.success(`${nome} vai ser processado de novo.`),
                onError: (e) => toast.error(mensagemErro(e, 'Não foi possível reprocessar.')),
              })}><RotateCw aria-hidden /></Acao>
      )}
      <Acao rotulo={`Baixar ${nome}`} onClick={() => baixarDocumento(documento.id)
        .catch((e) => toast.error(mensagemErro(e, 'Não foi possível baixar o arquivo.')))}><Download aria-hidden /></Acao>
      {alterar && <Acao rotulo={`Excluir ${nome}`} disabled={!parado} onClick={() => setExcluindo(true)}><Trash2 aria-hidden /></Acao>}

      <PainelInconsistencias documentoId={documento.id} arquivo={nome} aberto={inconsistencias} aoMudar={setInconsistencias} />
      <Confirmar aberto={excluindo} aoMudar={setExcluindo} titulo="Excluir documento"
                 descricao={<>Excluir <strong>{nome}</strong>, o arquivo armazenado e os dados lidos dele?</>}
                 rotuloAcao="Excluir" ocupado={excluir.isPending}
                 aoConfirmar={() => excluir.mutate(documento.id, {
                   onSuccess: () => { toast.success('Documento excluído.'); setExcluindo(false); },
                   onError: (e) => { toast.error(mensagemErro(e, 'Não foi possível excluir.')); setExcluindo(false); },
                 })} />
    </div>
  );
}
