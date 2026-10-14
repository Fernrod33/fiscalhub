import { useState } from 'react';
import { Info } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { Reveal } from '@/components/motion';
import * as fmt from '@/lib/formatadores';
import { useClienteId } from '@/features/clientes/rota';
import { useDocumentos } from '../api';
import { TabelaDocumentos } from '../components/tabela-documentos';
import { ZonaUpload } from '../components/zona-upload';

/** Aba Importações: envio por arrastar e soltar e lista com status ao vivo. */
export default function ImportacoesClientePage() {
  const clienteId = useClienteId();
  const documentos = useDocumentos(clienteId);
  const [duplicado, setDuplicado] = useState<number>();
  const existente = documentos.data?.find((d) => d.id === duplicado);

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-envio">
        <CartaoTitulo id="titulo-envio">Importar arquivo</CartaoTitulo>
        <ZonaUpload clienteId={clienteId} aoDuplicado={setDuplicado} />
        {duplicado && (
          <Alert className="mt-3">
            <Info aria-hidden />
            <AlertTitle>Este arquivo já foi importado</AlertTitle>
            <AlertDescription>
              {existente
                ? <>É o mesmo conteúdo de <strong>{existente.nomeOriginalArquivo}</strong>, importado em <span className="num">{fmt.dataHora(existente.dataImportacao)}</span>.</>
                : 'O conteúdo é idêntico a um documento já importado para este cliente.'}{' '}
              <a href={`#documento-${duplicado}`} className="font-medium text-primary underline-offset-4 hover:underline"
                 onClick={() => document.getElementById('titulo-documentos')?.scrollIntoView({ behavior: 'smooth' })}>
                Ver o documento existente
              </a>
            </AlertDescription>
          </Alert>
        )}
      </Cartao>
      <Cartao aria-labelledby="titulo-documentos">
        <CartaoTitulo id="titulo-documentos">Documentos importados</CartaoTitulo>
        <TabelaDocumentos clienteId={clienteId} documentos={documentos.data} carregando={documentos.isPending} destacado={duplicado} />
      </Cartao>
    </Reveal>
  );
}
