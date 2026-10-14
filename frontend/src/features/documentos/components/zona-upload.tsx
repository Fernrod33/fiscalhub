import { useId, useRef, useState, type DragEvent, type FormEvent } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { FileUp, Upload, X } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Collapse } from '@/components/motion';
import { UPLOAD } from '@/lib/config';
import { ErroApi, mensagemErro } from '@/lib/http';
import * as fmt from '@/lib/formatadores';
import { TIPOS_DOCUMENTO, TRIBUTOS } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { useImportarDocumento, type MetadadosImportacao } from '../api';

const esquema = z.object({
  tipo: z.enum(Object.keys(TIPOS_DOCUMENTO) as [MetadadosImportacao['tipo'], ...MetadadosImportacao['tipo'][]]),
  tributos: z.array(z.enum(Object.keys(TRIBUTOS) as [MetadadosImportacao['tributos'][number], ...MetadadosImportacao['tributos'][number][]]))
    .min(1, 'Marque ao menos um tributo.'),
  periodoInicio: z.string().min(1, 'Informe o início.'),
  periodoFim: z.string().min(1, 'Informe o fim.'),
}).refine((v) => v.periodoInicio <= v.periodoFim, { message: 'O fim deve ser igual ou posterior ao início.', path: ['periodoFim'] });

const extensao = (nome: string) => nome.slice(nome.lastIndexOf('.')).toLowerCase();

function problemaDoArquivo(arquivo: File): string | null {
  if (!UPLOAD.extensoes.includes(extensao(arquivo.name))) return `Tipo não permitido. Use ${UPLOAD.extensoes.join(', ')}.`;
  if (arquivo.size > UPLOAD.limiteBytes) return `Arquivo acima do limite de ${fmt.tamanhoArquivo(UPLOAD.limiteBytes)}.`;
  return null;
}

function mesAnterior() {
  const hoje = new Date();
  const ini = new Date(Date.UTC(hoje.getFullYear(), hoje.getMonth() - 1, 1));
  const fim = new Date(Date.UTC(hoje.getFullYear(), hoje.getMonth(), 0));
  return { periodoInicio: ini.toISOString().slice(0, 10), periodoFim: fim.toISOString().slice(0, 10) };
}

/** Upload por arrastar e soltar (ou seleção) + metadados da importação. Em 409 (duplicado) avisa quem chamou. */
export function ZonaUpload({ clienteId, aoDuplicado }: { clienteId: number; aoDuplicado: (documentoExistenteId: number) => void }) {
  const importar = useImportarDocumento(clienteId);
  const entrada = useRef<HTMLInputElement>(null);
  const idInstrucoes = useId();
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [arrastando, setArrastando] = useState(false);
  const form = useForm<z.infer<typeof esquema>>({
    resolver: zodResolver(esquema),
    defaultValues: { tipo: 'OUTRO', tributos: [], ...mesAnterior() },
  });

  function escolher(f: File | undefined) {
    if (!f) return;
    const problema = problemaDoArquivo(f);
    if (problema) {
      toast.error(problema);
      return;
    }
    setArquivo(f);
  }

  function soltar(e: DragEvent) {
    e.preventDefault();
    setArrastando(false);
    escolher(e.dataTransfer.files[0]);
  }

  const limpar = () => { setArquivo(null); if (entrada.current) entrada.current.value = ''; };

  const enviar = (e: FormEvent) => form.handleSubmit((metadados) => arquivo && importar.mutate({ arquivo, metadados }, {
    onSuccess: () => { toast.success(`${arquivo.name} enviado. O processamento continua em segundo plano.`); limpar(); },
    onError: (e) => {
      if (e instanceof ErroApi && e.status === 409 && e.recursoExistenteId) {
        aoDuplicado(e.recursoExistenteId);
        limpar();
      } else {
        toast.error(mensagemErro(e, 'Não foi possível enviar o arquivo.'));
      }
    },
  }))(e);

  return (
    <div className="grid gap-3">
      <div onDragOver={(e) => { e.preventDefault(); setArrastando(true); }} onDragLeave={() => setArrastando(false)} onDrop={soltar}
           className={cn('flex flex-col items-center gap-2 rounded-lg border border-dashed bg-surface px-6 py-8 text-center transition-colors duration-(--dur-fast)',
             arrastando && 'border-primary bg-accent')}>
        <FileUp className="size-8 text-muted-foreground" aria-hidden />
        <p id={idInstrucoes}>Arraste o arquivo aqui ou</p>
        <Button type="button" variant="outline" onClick={() => entrada.current?.click()} aria-describedby={idInstrucoes}>
          <Upload aria-hidden /> Escolher arquivo
        </Button>
        <p className="text-xs text-muted-foreground">{UPLOAD.extensoes.join(', ')} · até {fmt.tamanhoArquivo(UPLOAD.limiteBytes)}</p>
        <input ref={entrada} type="file" className="sr-only" tabIndex={-1} aria-hidden accept={UPLOAD.extensoes.join(',')}
               onChange={(e) => escolher(e.target.files?.[0])} />
      </div>

      <Collapse aberto={!!arquivo}>
        <Form {...form}>
          <form onSubmit={enviar} className="grid gap-4 rounded-lg border bg-surface p-4" noValidate>
            <div className="flex items-center justify-between gap-2">
              <p className="min-w-0 truncate font-medium">{arquivo?.name} <span className="num text-muted-foreground">({fmt.tamanhoArquivo(arquivo?.size)})</span></p>
              <Button type="button" variant="ghost" size="icon" aria-label="Remover arquivo" onClick={limpar}><X aria-hidden /></Button>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <FormField control={form.control} name="tipo" render={({ field }) => (
                <FormItem>
                  <FormLabel>Tipo do documento</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl><SelectTrigger className="w-full"><SelectValue /></SelectTrigger></FormControl>
                    <SelectContent>
                      {Object.entries(TIPOS_DOCUMENTO).map(([valor, rotulo]) => <SelectItem key={valor} value={valor}>{rotulo}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="periodoInicio" render={({ field }) => (
                <FormItem><FormLabel>Período — início</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
              <FormField control={form.control} name="periodoFim" render={({ field }) => (
                <FormItem><FormLabel>Período — fim</FormLabel><FormControl><Input type="date" {...field} /></FormControl><FormMessage /></FormItem>
              )} />
            </div>
            <FormField control={form.control} name="tributos" render={({ field }) => (
              <FormItem>
                <fieldset>
                  <legend className="mb-2 text-sm font-medium">Tributos do arquivo</legend>
                  <div className="flex flex-wrap gap-x-4 gap-y-2">
                    {Object.entries(TRIBUTOS).map(([valor, rotulo]) => (
                      <label key={valor} className="flex items-center gap-1.5">
                        <input type="checkbox" className="size-4 accent-primary" checked={field.value.includes(valor as never)}
                               onChange={(e) => field.onChange(e.target.checked ? [...field.value, valor] : field.value.filter((t) => t !== valor))} />
                        {rotulo}
                      </label>
                    ))}
                  </div>
                </fieldset>
                <FormMessage />
              </FormItem>
            )} />
            <div className="flex justify-end">
              <Button type="submit" disabled={importar.isPending}>{importar.isPending ? 'Enviando…' : 'Importar'}</Button>
            </div>
          </form>
        </Form>
      </Collapse>
    </div>
  );
}
