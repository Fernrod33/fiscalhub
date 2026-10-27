import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { FileText, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Selo } from '@/components/selo';
import { TextoTruncado } from '@/components/texto-truncado';
import { Stagger, StaggerItem } from '@/components/motion';
import type { Documento } from '@/lib/api/tipos';
import { ErroApi, mensagemErro } from '@/lib/http';
import * as fmt from '@/lib/formatadores';
import { TIPOS_DOCUMENTO } from '@/lib/rotulos';
import { chavesDocumentos, enviarDocumento, useDocumento, useStatusDocumento } from '../api';
import { ClienteDoArquivo } from './cliente-do-arquivo';
import { LinhaDoTempo } from './linha-do-tempo';
import { MapearPlanilha } from './mapear-planilha';
import { PainelInconsistencias } from './painel-inconsistencias';
import { ZonaUpload } from './zona-upload';

type Item = {
  chave: string;
  arquivo: File;
  estado: 'fila' | 'enviando' | 'enviado' | 'erro';
  progresso: number;
  documento?: Documento;
  erro?: string;
  existenteId?: number;
};

/**
 * Central de importação: vários arquivos numa fila (um envio por vez), barra de progresso do upload e, depois, a
 * linha do tempo do processamento. Sem clienteId: escritório (o cliente vem do arquivo).
 */
export function CentralImportacao({ clienteId, aoDuplicado }: { clienteId?: number; aoDuplicado?: (documentoId: number) => void }) {
  const qc = useQueryClient();
  const [fila, setFila] = useState<Item[]>([]);
  const pendentes = useRef<Item[]>([]);
  const enviando = useRef(false);
  const atualizar = (chave: string, mudanca: Partial<Item>) => setFila((f) => f.map((i) => (i.chave === chave ? { ...i, ...mudanca } : i)));

  /** Um envio por vez, na ordem da fila; o laço nasce do evento de escolha dos arquivos (sem efeito). */
  async function processarFila() {
    if (enviando.current) return;
    enviando.current = true;
    for (let item = pendentes.current.shift(); item; item = pendentes.current.shift()) {
      const chave = item.chave;
      atualizar(chave, { estado: 'enviando' });
      try {
        const documento = await enviarDocumento(item.arquivo, clienteId, (p) => atualizar(chave, { progresso: p }));
        atualizar(chave, { estado: 'enviado', documento, progresso: 1 });
        qc.invalidateQueries({ queryKey: chavesDocumentos.todos });
      } catch (e) {
        const duplicado = e instanceof ErroApi && e.status === 409 ? e.recursoExistenteId : undefined;
        if (duplicado) aoDuplicado?.(duplicado);
        atualizar(chave, { estado: 'erro', erro: mensagemErro(e, 'Não foi possível enviar o arquivo.'), existenteId: duplicado });
      }
    }
    enviando.current = false;
  }

  function adicionar(arquivos: File[]) {
    const novos = arquivos.map((arquivo) => ({
      chave: `${arquivo.name}-${arquivo.size}-${arquivo.lastModified}-${crypto.randomUUID()}`, arquivo, estado: 'fila' as const, progresso: 0,
    }));
    setFila((f) => [...f, ...novos]);
    pendentes.current.push(...novos);
    void processarFila();
  }

  return (
    <div className="grid gap-3">
      <ZonaUpload aoEscolher={adicionar} />
      {fila.length > 0 && (
        <section aria-label="Fila de importação">
          <Stagger as="ul" className="grid gap-2">
            {fila.map((item) => (
              <StaggerItem as="li" key={item.chave}>
                <ItemFila item={item} aoRemover={() => setFila((f) => f.filter((i) => i.chave !== item.chave))} />
              </StaggerItem>
            ))}
          </Stagger>
        </section>
      )}
    </div>
  );
}

function ItemFila({ item, aoRemover }: { item: Item; aoRemover: () => void }) {
  const status = useStatusDocumento(item.documento?.id);
  const [inconsistencias, setInconsistencias] = useState(false);
  const s = status.data;
  const doc = item.documento;

  return (
    <div className="grid gap-2 rounded-md border bg-surface p-3">
      <div className="flex items-center gap-3">
        <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <div className="min-w-0 flex-1"><TextoTruncado texto={item.arquivo.name} /></div>
        <span className="num whitespace-nowrap text-xs text-muted-foreground">{fmt.tamanhoArquivo(item.arquivo.size)}</span>
        {s && s.tipo !== 'OUTRO' && <Selo tom="info">{TIPOS_DOCUMENTO[s.tipo]}</Selo>}
        {item.estado !== 'enviando' && (
          <Button variant="ghost" size="icon" aria-label={`Tirar ${item.arquivo.name} da lista`} onClick={aoRemover}><X aria-hidden /></Button>
        )}
      </div>
      {item.estado === 'erro'
        ? <p className="text-danger" role="alert">
            {item.erro}{' '}
            {item.existenteId && <span className="text-muted-foreground">(documento nº <span className="num">{item.existenteId}</span>)</span>}
          </p>
        : (
          <>
            {item.estado === 'enviando' && !s && (
              <progress value={item.progresso} max={1} aria-label={`Enviando ${item.arquivo.name}`}
                        className="h-1.5 w-full overflow-hidden rounded-full bg-muted [&::-moz-progress-bar]:bg-primary [&::-webkit-progress-bar]:bg-muted [&::-webkit-progress-value]:bg-primary" />
            )}
            <LinhaDoTempo status={s} enviando={item.estado === 'fila' ? undefined : item.progresso} />
          </>
        )}
      {s?.status === 'AGUARDANDO_CLIENTE' && doc && <ClienteDoArquivoAtual documentoId={doc.id} />}
      {s?.status === 'AGUARDANDO_MAPEAMENTO' && doc && (
        <MapearPlanilha documentoId={doc.id} prefixo="Planilha recebida. " />
      )}
      {(s?.status === 'ERRO' || s?.status === 'LAYOUT_NAO_RECONHECIDO') && s.mensagem && (
        // PDF sem texto, layout desconhecido...: a mensagem já diz o que fazer
        <p className={s.status === 'ERRO' ? 'text-danger' : 'text-warning'} role={s.status === 'ERRO' ? 'alert' : 'status'}>{s.mensagem}</p>
      )}
      {(s?.status === 'PROCESSADO_COM_ALERTAS' || s?.status === 'ERRO' || s?.status === 'LAYOUT_NAO_RECONHECIDO') && doc && (
        <div>
          <Button variant="link" className="h-auto p-0" onClick={() => setInconsistencias(true)}>Ver inconsistências</Button>
          <PainelInconsistencias documentoId={doc.id} arquivo={doc.nomeOriginalArquivo} aberto={inconsistencias} aoMudar={setInconsistencias} />
        </div>
      )}
    </div>
  );
}

/** A sugestão (CNPJ e nome lidos do arquivo) só existe depois do processamento: lê o documento atualizado. */
function ClienteDoArquivoAtual({ documentoId }: { documentoId: number }) {
  const documento = useDocumento(documentoId);
  return documento.data ? <ClienteDoArquivo documento={documento.data} /> : null;
}
