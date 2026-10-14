import { useId, useRef, useState, type DragEvent } from 'react';
import { FileUp, Upload } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { UPLOAD } from '@/lib/config';
import { ErroApi, mensagemErro } from '@/lib/http';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';
import { useImportarDocumento } from '../api';

const extensao = (nome: string) => nome.slice(nome.lastIndexOf('.')).toLowerCase();

function problemaDoArquivo(arquivo: File): string | null {
  if (!UPLOAD.extensoes.includes(extensao(arquivo.name))) return `Tipo não permitido. Use ${UPLOAD.extensoes.join(', ')}.`;
  if (arquivo.size > UPLOAD.limiteBytes) return `Arquivo acima do limite de ${fmt.tamanhoArquivo(UPLOAD.limiteBytes)}.`;
  return null;
}

/**
 * Upload por arrastar e soltar (ou seleção). Tipo, período e empresa vêm do conteúdo: nada a preencher.
 * Sem clienteId envia para a central do escritório. Em 409 (duplicado) avisa quem chamou.
 */
export function ZonaUpload({ clienteId, aoDuplicado }: { clienteId?: number; aoDuplicado: (documentoExistenteId: number) => void }) {
  const importar = useImportarDocumento(clienteId);
  const entrada = useRef<HTMLInputElement>(null);
  const idInstrucoes = useId();
  const [arrastando, setArrastando] = useState(false);
  const [enviando, setEnviando] = useState<string | null>(null);

  function enviar(arquivo: File | undefined) {
    if (!arquivo) return;
    const problema = problemaDoArquivo(arquivo);
    if (problema) {
      toast.error(problema);
      return;
    }
    setEnviando(arquivo.name);
    importar.mutate(arquivo, {
      onSuccess: () => toast.success(`${arquivo.name} enviado. O processamento continua em segundo plano.`),
      onError: (e) => {
        if (e instanceof ErroApi && e.status === 409 && e.recursoExistenteId) aoDuplicado(e.recursoExistenteId);
        else toast.error(mensagemErro(e, 'Não foi possível enviar o arquivo.'));
      },
      onSettled: () => { setEnviando(null); if (entrada.current) entrada.current.value = ''; },
    });
  }

  function soltar(e: DragEvent) {
    e.preventDefault();
    setArrastando(false);
    enviar(e.dataTransfer.files[0]);
  }

  return (
    <div onDragOver={(e) => { e.preventDefault(); setArrastando(true); }} onDragLeave={() => setArrastando(false)} onDrop={soltar}
         className={cn('flex flex-col items-center gap-2 rounded-lg border border-dashed bg-surface px-6 py-8 text-center transition-colors duration-(--dur-fast)',
           arrastando && 'border-primary bg-accent')}>
      <FileUp className="size-8 text-muted-foreground" aria-hidden />
      <p id={idInstrucoes}>Arraste o arquivo aqui ou</p>
      <Button type="button" variant="outline" disabled={importar.isPending} onClick={() => entrada.current?.click()} aria-describedby={idInstrucoes}>
        <Upload aria-hidden /> Escolher arquivo
      </Button>
      <p className="text-xs text-muted-foreground">
        ECD (SPED), balancete em planilha, PDF · {UPLOAD.extensoes.join(', ')} · até {fmt.tamanhoArquivo(UPLOAD.limiteBytes)}
      </p>
      <input ref={entrada} type="file" className="sr-only" tabIndex={-1} aria-hidden accept={UPLOAD.extensoes.join(',')}
             onChange={(e) => enviar(e.target.files?.[0])} />
      {enviando && (
        <div className="flex items-center gap-3">
          <progress value={importar.progresso} max={1} aria-label={`Enviando ${enviando}`}
                    className="h-2 w-48 overflow-hidden rounded-full bg-muted [&::-moz-progress-bar]:bg-primary [&::-webkit-progress-bar]:bg-muted [&::-webkit-progress-value]:bg-primary [&::-webkit-progress-value]:transition-[width] [&::-webkit-progress-value]:duration-(--dur-fast)" />
          <span className="num w-12 text-right text-muted-foreground" aria-hidden>{fmt.progresso(importar.progresso)}</span>
        </div>
      )}
    </div>
  );
}
