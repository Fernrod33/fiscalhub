import { Link } from 'react-router-dom';
import { FileText, Inbox } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { EstadoVazio } from '@/components/estado-vazio';
import { AnimatedNumber, Reveal, Stagger, StaggerItem } from '@/components/motion';
import { CabecalhoPagina } from '@/components/layout/cabecalho-pagina';
import { TextoTruncado } from '@/components/texto-truncado';
import * as fmt from '@/lib/formatadores';
import type { DocumentoStatus } from '@/lib/api/tipos';
import { STATUS_FINAL } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { useSessao } from '@/features/auth/sessao';
import { useClientes } from '@/features/clientes/api';
import { useDocumentosDoEscritorio, useResumoDocumentos } from '@/features/documentos/api';
import { StatusDocumento } from '@/features/documentos/components/status-documento';

/** Painel do escritório: totais e últimos documentos importados. */
export default function PainelPage() {
  const nome = useSessao()?.nome?.split(' ')[0];
  const clientes = useClientes();
  const resumo = useResumoDocumentos();
  const ultimos = useDocumentosDoEscritorio({ pagina: 0, tamanho: 8 });
  const carregando = clientes.isPending || resumo.isPending;
  const porStatus = Object.entries(resumo.data ?? {});
  const conta = (f: (s: string) => boolean) => porStatus.filter(([s]) => f(s)).reduce((total, [, n]) => total + (n ?? 0), 0);
  const kpis = [
    { rotulo: 'Clientes', valor: clientes.data?.length ?? 0 },
    { rotulo: 'Importações', valor: conta(() => true) },
    { rotulo: 'Em processamento', valor: conta((s) => !STATUS_FINAL.includes(s as DocumentoStatus)) },
    { rotulo: 'Processadas', valor: conta((s) => s.startsWith('PROCESSADO')) },
    { rotulo: 'Com erro', valor: conta((s) => s === 'ERRO'), perigo: true },
  ];

  return (
    <div className="grid gap-5">
      <CabecalhoPagina titulo={nome ? `Olá, ${nome}` : 'Painel'} subtitulo="Visão do escritório" />
      {carregando
        ? <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">{kpis.map((k) => <Skeleton key={k.rotulo} className="h-20" />)}</div>
        : (
          <Stagger as="ul" className="grid grid-cols-2 gap-3 lg:grid-cols-5">
            {kpis.map((k) => (
              <StaggerItem as="li" key={k.rotulo} className="rounded-lg border bg-surface p-3 shadow-sm">
                <p className="text-muted-foreground">{k.rotulo}</p>
                <AnimatedNumber valor={k.valor} className={cn('text-2xl font-semibold', k.perigo && k.valor > 0 && 'text-danger')} />
              </StaggerItem>
            ))}
          </Stagger>
        )}
      <Reveal>
        <Cartao aria-labelledby="titulo-ultimos">
          <CartaoTitulo id="titulo-ultimos">Últimas importações</CartaoTitulo>
          {ultimos.isPending
            ? <div className="grid gap-2">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-8" />)}</div>
            : !ultimos.data?.content.length
              ? <EstadoVazio icone={Inbox} titulo="Nenhuma importação ainda" descricao="Abra um cliente e envie os arquivos dele na aba Importações." />
              : (
                <Stagger as="ul" className="divide-y">
                  {ultimos.data.content.map((d) => (
                    <StaggerItem as="li" key={d.id} className="flex items-center gap-3 py-2">
                      <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                      <div className="min-w-0 flex-1">
                        <TextoTruncado texto={d.nomeOriginalArquivo} />
                        <Link viewTransition to={`/clientes/${d.clienteId}/importacoes`} className="text-xs text-muted-foreground hover:text-foreground hover:underline">
                          {d.clienteRazaoSocial}
                        </Link>
                      </div>
                      <span className="num whitespace-nowrap text-muted-foreground">{fmt.dataHora(d.dataImportacao)}</span>
                      <StatusDocumento status={d.status} />
                    </StaggerItem>
                  ))}
                </Stagger>
              )}
        </Cartao>
      </Reveal>
    </div>
  );
}
