import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, ImageUp, TriangleAlert } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { Reveal } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import type { Identidade, IdentidadeRequest } from '@/lib/api/tipos';
import { CONTRASTE_AA, contraste, corValida, sugerirCor } from '@/lib/contraste';
import { http, mensagemErro } from '@/lib/http';
import { CHAVE_IDENTIDADE, useIdentidade } from '@/features/relatorios/api';

/** Fundos em que a cor principal aparece: o papel do relatório e a superfície do tema escuro do app. */
const FUNDOS = [{ nome: 'branco (relatório)', cor: '#ffffff', obrigatorio: true }, { nome: 'escuro (tema escuro)', cor: '#111a2b', obrigatorio: false }];
const LOGO_MAX = 1024 * 1024;
const VAZIO: IdentidadeRequest = { nomeExibicao: '', corPrincipal: '#0b4f6c', rodape: '', responsavelNome: '', responsavelRegistro: '' };

/** Configurações → Escritório (só ADMIN): a identidade nos relatórios entregues aos clientes, com prévia ao vivo. */
export default function IdentidadePage() {
  const identidade = useIdentidade();
  if (identidade.isPending) return <Skeleton className="h-96" />;
  if (!identidade.data) return <p role="alert" className="text-danger">Não foi possível carregar a identidade do escritório.</p>;
  return <FormularioIdentidade inicial={identidade.data} />;
}

export function FormularioIdentidade({ inicial }: { inicial: Identidade }) {
  const qc = useQueryClient();
  const [dados, setDados] = useState<IdentidadeRequest>(() => ({
    nomeExibicao: inicial.nomeExibicao ?? '', corPrincipal: inicial.corPrincipal ?? VAZIO.corPrincipal, rodape: inicial.rodape ?? '',
    responsavelNome: inicial.responsavelNome ?? '', responsavelRegistro: inicial.responsavelRegistro ?? '',
  }));
  const campo = (k: keyof IdentidadeRequest) => ({
    id: `identidade-${k}`, value: dados[k], onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setDados((d) => ({ ...d, [k]: e.target.value })),
  });
  const cor = dados.corPrincipal;
  const valida = corValida(cor);
  const reprovada = !valida || contraste(cor, FUNDOS[0].cor) < CONTRASTE_AA;

  const salvar = useMutation({
    mutationFn: () => http.put<Identidade>('/api/escritorio/identidade', dados),
    onSuccess: (i) => { qc.setQueryData(CHAVE_IDENTIDADE, i); toast.success('Identidade do escritório salva.'); },
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível salvar.')),
  });
  const enviarLogo = useMutation({
    mutationFn: (arquivo: File) => {
      const corpo = new FormData();
      corpo.append('arquivo', arquivo);
      return http.post<Identidade>('/api/escritorio/identidade/logo', corpo);
    },
    onSuccess: (i) => { qc.setQueryData(CHAVE_IDENTIDADE, i); qc.invalidateQueries({ queryKey: ['escritorio', 'logo'] }); toast.success('Logotipo atualizado.'); },
    onError: (e) => toast.error(mensagemErro(e, 'Não foi possível enviar o logotipo.')),
  });
  const aoEscolherLogo = (arquivo?: File) => {
    if (!arquivo) return;
    if (!['image/png', 'image/jpeg'].includes(arquivo.type)) return toast.error('Use PNG ou JPEG (SVG não é aceito).');
    if (arquivo.size > LOGO_MAX) return toast.error('O logotipo deve ter até 1 MB.');
    enviarLogo.mutate(arquivo);
  };
  const logo = useLogo(inicial.temLogo);
  const nome = dados.nomeExibicao.trim() || inicial.nome;

  return (
    <Reveal className="grid gap-4">
      <CabecalhoPagina titulo="Escritório" subtitulo="Identidade nos relatórios entregues aos clientes" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Cartao aria-labelledby="titulo-identidade">
          <CartaoTitulo id="titulo-identidade">Identidade</CartaoTitulo>
          <form className="grid gap-4" onSubmit={(e) => { e.preventDefault(); if (!reprovada) salvar.mutate(); }}>
            <div className="grid gap-1.5">
              <Label htmlFor="identidade-nomeExibicao">Nome de exibição</Label>
              <Input {...campo('nomeExibicao')} maxLength={150} placeholder={inicial.nome} />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="identidade-logo">Logotipo</Label>
              <div className="flex items-center gap-3">
                <input id="identidade-logo" type="file" accept="image/png,image/jpeg" className="sr-only"
                       onChange={(e) => { aoEscolherLogo(e.target.files?.[0]); e.target.value = ''; }} />
                <Button type="button" variant="outline" size="sm" asChild>
                  <label htmlFor="identidade-logo" className="cursor-pointer"><ImageUp aria-hidden /> {enviarLogo.isPending ? 'Enviando…' : 'Escolher arquivo'}</label>
                </Button>
                <span className="text-sm text-muted-foreground">PNG ou JPEG, até 1 MB; redimensionado no servidor.</span>
              </div>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="identidade-corPrincipal">Cor principal</Label>
              <div className="flex items-center gap-2">
                <input type="color" aria-label="Escolher cor" value={valida ? cor : '#000000'}
                       onChange={(e) => setDados((d) => ({ ...d, corPrincipal: e.target.value }))} className="h-9 w-12 rounded border bg-background" />
                <Input {...campo('corPrincipal')} className="num w-32" maxLength={7} aria-describedby="contraste-cor" />
              </div>
              <ul id="contraste-cor" className="grid gap-1 text-sm" aria-live="polite">
                {FUNDOS.map((f) => {
                  const razao = valida ? contraste(cor, f.cor) : 0;
                  const passa = razao >= CONTRASTE_AA;
                  const sugestao = valida ? sugerirCor(cor, f.cor) : null;
                  return (
                    <li key={f.cor} className="flex flex-wrap items-center gap-2">
                      {passa ? <Check aria-hidden className="size-4 text-success" /> : <TriangleAlert aria-hidden className="size-4 text-warning" />}
                      <span>
                        Sobre {f.nome}: <span className="num">{valida ? razao.toFixed(2).replace('.', ',') : '—'}:1</span>{' '}
                        {passa ? '(AA)' : `(abaixo do AA, mínimo 4,5:1${f.obrigatorio ? '' : ' — só aviso'})`}
                      </span>
                      {sugestao && (
                        <Button type="button" variant="link" size="sm" className="h-auto p-0"
                                onClick={() => setDados((d) => ({ ...d, corPrincipal: sugestao }))}>
                          Usar {sugestao}
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="identidade-rodape">Texto do rodapé</Label>
              <textarea {...campo('rodape')} maxLength={500} rows={2} placeholder="Endereço, registro na OAB/CRC"
                        className="rounded-md border bg-background px-3 py-2 text-sm" />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="grid gap-1.5">
                <Label htmlFor="identidade-responsavelNome">Responsável técnico</Label>
                <Input {...campo('responsavelNome')} maxLength={150} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="identidade-responsavelRegistro">Registro (OAB/CRC)</Label>
                <Input {...campo('responsavelRegistro')} maxLength={60} />
              </div>
            </div>
            <div>
              <Button type="submit" disabled={reprovada || salvar.isPending}>{salvar.isPending ? 'Salvando…' : 'Salvar'}</Button>
              {reprovada && <p className="mt-1 text-sm text-danger">A cor precisa de contraste AA (4,5:1) sobre o branco do relatório.</p>}
            </div>
          </form>
        </Cartao>

        <Cartao aria-labelledby="titulo-previa-identidade">
          <CartaoTitulo id="titulo-previa-identidade">Prévia no relatório</CartaoTitulo>
          {/* o relatório é impresso em branco: a prévia não segue o tema do app */}
          <div className="grid gap-6 rounded-md border bg-white p-6 text-[#0f1c2e]" aria-label="Prévia do cabeçalho e do rodapé">
            <div className="grid gap-4">
              <div className="h-2 rounded-sm" style={{ background: valida ? cor : '#0b4f6c' }} />
              {logo ? <img src={logo} alt="Logotipo do escritório" className="max-h-16 max-w-56 object-contain" />
                : <p className="text-sm text-[#556378]">{nome}</p>}
              <p className="font-serif text-2xl font-semibold" style={{ color: valida ? cor : undefined }}>Relatório de tributos apurados, declarados e pagos</p>
              {dados.responsavelNome.trim() && (
                <p className="text-sm">Responsável técnico: {dados.responsavelNome}{dados.responsavelRegistro.trim() && ` — ${dados.responsavelRegistro}`}</p>
              )}
            </div>
            <div className="flex flex-wrap justify-between gap-2 border-t border-[#d7dee7] pt-2 text-xs text-[#556378]">
              <span>{nome}{dados.rodape.trim() && ` · ${dados.rodape}`}</span>
              <span className="num">Relatório nº 000123 · Página 1 de 4</span>
            </div>
          </div>
        </Cartao>
      </div>
    </Reveal>
  );
}

/** Logotipo gravado (PNG) como object URL; atualiza depois de um upload. */
function useLogo(temLogo: boolean): string | undefined {
  const { data } = useQuery({
    queryKey: ['escritorio', 'logo'],
    queryFn: async () => URL.createObjectURL((await http.baixar('/api/escritorio/identidade/logo')).blob),
    enabled: temLogo,
  });
  useEffect(() => () => { if (data) URL.revokeObjectURL(data); }, [data]);
  return data;
}
