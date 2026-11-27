import { useId, useRef, useState, type DragEvent } from 'react';
import { FileUp, Upload } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { UPLOAD } from '@/lib/config';
import * as fmt from '@/lib/formatadores';
import { cn } from '@/lib/utils';

const extensao = (nome: string) => nome.slice(nome.lastIndexOf('.')).toLowerCase();

function problemaDoArquivo(arquivo: File): string | null {
  if (!UPLOAD.extensoes.includes(extensao(arquivo.name))) return `${arquivo.name}: tipo não permitido. Use ${UPLOAD.extensoes.join(', ')}.`;
  if (arquivo.size > UPLOAD.limiteBytes) return `${arquivo.name}: acima do limite de ${fmt.tamanhoArquivo(UPLOAD.limiteBytes)}.`;
  return null;
}

/** Arrastar e soltar (ou seleção) de vários arquivos. Tipo, período e empresa vêm do conteúdo: nada a preencher. */
export function ZonaUpload({ aoEscolher }: { aoEscolher: (arquivos: File[]) => void }) {
  const entrada = useRef<HTMLInputElement>(null);
  const idInstrucoes = useId();
  const [arrastando, setArrastando] = useState(false);

  function escolher(lista: FileList | null | undefined) {
    const arquivos = Array.from(lista ?? []);
    const validos = arquivos.filter((a) => {
      const problema = problemaDoArquivo(a);
      if (problema) toast.error(problema);
      return !problema;
    });
    if (validos.length) aoEscolher(validos);
    if (entrada.current) entrada.current.value = '';
  }

  function soltar(e: DragEvent) {
    e.preventDefault();
    setArrastando(false);
    escolher(e.dataTransfer.files);
  }

  return (
    <div onDragOver={(e) => { e.preventDefault(); setArrastando(true); }} onDragLeave={() => setArrastando(false)} onDrop={soltar}
         className={cn('flex flex-col items-center gap-2 rounded-lg border border-dashed bg-surface px-6 py-8 text-center transition-colors duration-(--dur-fast)',
           arrastando && 'border-primary bg-accent')}>
      <FileUp className="size-8 text-muted-foreground" aria-hidden />
      <p id={idInstrucoes}>Arraste os arquivos aqui ou</p>
      <Button type="button" variant="outline" onClick={() => entrada.current?.click()} aria-describedby={idInstrucoes}>
        <Upload aria-hidden /> Escolher arquivos
      </Button>
      <p className="text-xs text-muted-foreground">
        ECD, ECF, EFD-Contribuições, EFD ICMS/IPI, balancete em planilha, PDF · {UPLOAD.extensoes.join(', ')} · até {fmt.tamanhoArquivo(UPLOAD.limiteBytes)} cada
      </p>
      <input ref={entrada} type="file" multiple className="sr-only" tabIndex={-1} aria-hidden accept={UPLOAD.extensoes.join(',')}
             onChange={(e) => escolher(e.target.files)} />
    </div>
  );
}
