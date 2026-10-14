import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CAMPOS, completo as camposCompletos, letra, type Colunas } from '../campos';
import { cn } from '@/lib/utils';

const NENHUMA = 'nenhuma';

/** Passo 1: escolher a linha do cabeçalho na prévia e qual coluna é cada campo. */
export function PassoColunas({ linhas, linhaCabecalho, colunas, aoMudarCabecalho, aoMudarColunas, aoContinuar }: {
  linhas: string[][];
  linhaCabecalho: number;
  colunas: Colunas;
  aoMudarCabecalho: (linha: number) => void;
  aoMudarColunas: (colunas: Colunas) => void;
  aoContinuar: () => void;
}) {
  const largura = Math.max(1, ...linhas.map((l) => l.length));
  const cabecalho = linhas[linhaCabecalho - 1] ?? [];
  const completo = camposCompletos(colunas);

  return (
    <div className="grid gap-4">
      <fieldset className="grid gap-2">
        <legend className="mb-1 font-medium">Linha do cabeçalho: marque a linha com os nomes das colunas</legend>
        <div className="max-h-80 overflow-auto rounded-md border">
          <table className="w-full text-xs">
            <thead className="sticky top-0 bg-muted">
              <tr>
                <th scope="col" className="w-10 px-2 py-1 text-left font-medium">Linha</th>
                {Array.from({ length: largura }, (_, i) => <th key={i} scope="col" className="px-2 py-1 text-left font-medium">{letra(i)}</th>)}
              </tr>
            </thead>
            <tbody>
              {linhas.map((l, i) => (
                <tr key={i} className={cn('border-t', i + 1 === linhaCabecalho && 'bg-accent font-semibold')}>
                  <td className="px-2 py-1">
                    <label className="flex items-center gap-1.5">
                      <input type="radio" name="linha-cabecalho" className="accent-primary" checked={i + 1 === linhaCabecalho}
                             onChange={() => aoMudarCabecalho(i + 1)} />
                      <span className="num">{i + 1}</span>
                    </label>
                  </td>
                  {Array.from({ length: largura }, (_, c) => <td key={c} className="max-w-48 truncate whitespace-nowrap px-2 py-1">{l[c]}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </fieldset>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {CAMPOS.map(({ campo, rotulo, obrigatorio }) => (
          <div key={campo} className="grid gap-1.5">
            <Label htmlFor={`coluna-${campo}`}>{rotulo}{obrigatorio && <span aria-hidden> *</span>}</Label>
            <Select value={colunas[campo] === undefined ? NENHUMA : String(colunas[campo])}
                    onValueChange={(v) => aoMudarColunas({ ...colunas, [campo]: v === NENHUMA ? undefined : Number(v) })}>
              <SelectTrigger id={`coluna-${campo}`} className="w-full" aria-required={obrigatorio}><SelectValue /></SelectTrigger>
              <SelectContent>
                {!obrigatorio && <SelectItem value={NENHUMA}>Não há</SelectItem>}
                {Array.from({ length: largura }, (_, i) => (
                  <SelectItem key={i} value={String(i)}>{letra(i)}{cabecalho[i] ? ` · ${cabecalho[i]}` : ''}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        ))}
      </div>
      <p className="text-xs text-muted-foreground">
        Sem colunas D/C, o indicador vem do próprio valor (ex.: “1.000,00 C”) ou do sinal (credor negativo).
      </p>
      <div className="flex justify-end">
        <Button disabled={!completo} onClick={aoContinuar}>Continuar</Button>
      </div>
    </div>
  );
}
