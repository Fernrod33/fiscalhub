import { useState } from 'react';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { Reveal } from '@/components/motion';
import { useClienteId } from '@/features/clientes/rota';
import { useDocumentos } from '../api';
import { CentralImportacao } from '../components/central-importacao';
import { TabelaDocumentos } from '../components/tabela-documentos';

/** Aba Importações do cliente: central de importação (o CNPJ do arquivo tem que ser o do cliente) e os documentos. */
export default function ImportacoesClientePage() {
  const clienteId = useClienteId();
  const documentos = useDocumentos(clienteId);
  const [duplicado, setDuplicado] = useState<number>();

  return (
    <Reveal className="grid gap-4">
      <Cartao aria-labelledby="titulo-envio">
        <CartaoTitulo id="titulo-envio">Importar arquivos</CartaoTitulo>
        <CentralImportacao clienteId={clienteId} aoDuplicado={setDuplicado} />
      </Cartao>
      <Cartao aria-labelledby="titulo-documentos">
        <CartaoTitulo id="titulo-documentos">Documentos importados</CartaoTitulo>
        <TabelaDocumentos documentos={documentos.data} carregando={documentos.isPending} destacado={duplicado} />
      </Cartao>
    </Reveal>
  );
}
