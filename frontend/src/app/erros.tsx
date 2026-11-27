import { Skeleton } from '@/components/ui/skeleton';
import { Link, isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { FileQuestion, TriangleAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { ErroApi } from '@/lib/http';

function Moldura({ icone: Icone, titulo, children }: { icone: typeof FileQuestion; titulo: string; children: React.ReactNode }) {
  return (
    <div className="grid min-h-[60svh] place-items-center p-6">
      <div className="grid max-w-md justify-items-center gap-3 text-center">
        <span className="grid size-12 place-items-center rounded-full bg-muted text-muted-foreground"><Icone className="size-6" aria-hidden /></span>
        <h1 className="font-serif text-2xl font-semibold">{titulo}</h1>
        {children}
      </div>
    </div>
  );
}

export function NaoEncontrado() {
  return (
    <Moldura icone={FileQuestion} titulo="Página não encontrada">
      <p className="text-muted-foreground">O endereço não existe ou você não tem acesso a ele.</p>
      <Button asChild><Link to="/">Ir para o painel</Link></Button>
    </Moldura>
  );
}

/** ErrorBoundary de cada rota: 404 vira página não encontrada; erro da API mostra o id de correlação para o suporte. */
export function ErroRota() {
  const erro = useRouteError();
  if ((isRouteErrorResponse(erro) && erro.status === 404) || (erro instanceof ErroApi && erro.status === 404)) return <NaoEncontrado />;
  const correlacao = erro instanceof ErroApi ? erro.correlacao : undefined;
  return (
    <Moldura icone={TriangleAlert} titulo="Algo deu errado">
      <p className="text-muted-foreground">
        {erro instanceof ErroApi && erro.status < 500 ? erro.message : 'Não foi possível carregar esta tela.'}
      </p>
      {correlacao && (
        <p className="text-sm">Código para o suporte: <code className="num rounded-sm bg-muted px-1.5 py-0.5 select-all">{correlacao}</code></p>
      )}
      <div className="flex gap-2">
        <Button variant="outline" onClick={() => window.location.reload()}>Tentar de novo</Button>
        <Button asChild><Link to="/">Ir para o painel</Link></Button>
      </div>
    </Moldura>
  );
}

/** Enquanto o código da rota carrega (primeira visita). */
export function CarregandoPagina() {
  return <div className="grid gap-3 p-5" aria-busy="true"><Skeleton className="h-8 w-64" /><Skeleton className="h-40" /></div>;
}
