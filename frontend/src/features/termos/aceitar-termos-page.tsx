import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Landmark } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { Usuario } from '@/lib/api/tipos';
import { http, mensagemErro } from '@/lib/http';
import { definirSessao, sair } from '@/features/auth/sessao';
import { TextoLegal } from './texto-legal';

/** Primeiro acesso (ou versão nova dos textos): ler e aceitar os Termos de Uso e a Política de Privacidade. */
export default function AceitarTermosPage() {
  const navigate = useNavigate();
  const [lido, setLido] = useState(false);
  const aceitar = useMutation({
    mutationFn: () => http.post<Usuario>('/api/usuarios/me/termos'),
    onSuccess: (u) => { definirSessao(u); navigate('/', { replace: true }); },
  });
  return (
    <main className="grid min-h-svh place-items-center bg-background p-4">
      <div className="w-full max-w-3xl">
        <div className="mb-6 flex items-center justify-center gap-2 text-primary">
          <Landmark className="size-6" aria-hidden />
          <span className="text-lg font-semibold tracking-tight text-foreground">FiscalHub</span>
        </div>
        <div className="grid gap-4 rounded-lg border bg-surface p-6 shadow-sm">
          <h1 className="font-serif text-2xl font-semibold tracking-tight">Termos de Uso e Política de Privacidade</h1>
          <Tabs defaultValue="termos">
            <TabsList>
              <TabsTrigger value="termos">Termos de Uso</TabsTrigger>
              <TabsTrigger value="privacidade">Política de Privacidade</TabsTrigger>
            </TabsList>
            {/* área rolável com foco pelo teclado */}
            <TabsContent value="termos" className="max-h-[50svh] overflow-y-auto pr-2" tabIndex={0}><TextoLegal arquivo="termos-de-uso.md" /></TabsContent>
            <TabsContent value="privacidade" className="max-h-[50svh] overflow-y-auto pr-2" tabIndex={0}><TextoLegal arquivo="politica-de-privacidade.md" /></TabsContent>
          </Tabs>
          <div className="flex items-start gap-2">
            <input id="li-os-termos" type="checkbox" className="mt-1 size-4 accent-primary" checked={lido} onChange={(e) => setLido(e.target.checked)} />
            <label htmlFor="li-os-termos">Li e aceito os Termos de Uso e a Política de Privacidade.</label>
          </div>
          {aceitar.isError && <p role="alert" className="text-sm text-danger">{mensagemErro(aceitar.error, 'Não foi possível registrar o aceite.')}</p>}
          <div className="flex justify-between gap-2">
            <Button variant="outline" onClick={sair}>Sair</Button>
            <Button disabled={!lido || aceitar.isPending} onClick={() => aceitar.mutate()}>{aceitar.isPending ? 'Registrando…' : 'Aceitar e continuar'}</Button>
          </div>
        </div>
      </div>
    </main>
  );
}
