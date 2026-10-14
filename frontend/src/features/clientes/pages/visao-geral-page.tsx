import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Pencil, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import { AnimatedNumber, Reveal, Stagger, StaggerItem } from '@/components/motion';
import type { DocumentoStatus } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { STATUS_DOCUMENTO } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { usePermissoes } from '@/features/auth/permissoes';
import { useDocumentos } from '@/features/documentos/api';
import { useCliente } from '../api';
import { ClienteDialog } from '../components/cliente-dialog';
import { useClienteId } from '../rota';

export default function VisaoGeralPage() {
  const id = useClienteId();
  const cliente = useCliente(id);
  const documentos = useDocumentos(id);
  const { alterar } = usePermissoes();
  const [editando, setEditando] = useState(false);
  const c = cliente.data;

  const porStatus = (Object.keys(STATUS_DOCUMENTO) as DocumentoStatus[]).map((s) => ({
    status: s, total: documentos.data?.filter((d) => d.status === s).length ?? 0,
  }));

  const dados: [string, string][] = c ? [
    ['Razão social', c.razaoSocial], ['Nome fantasia', c.nomeFantasia || '—'], ['CNPJ', fmt.cnpj(c.cnpj)],
    ['Inscrição estadual', c.inscricaoEstadual || '—'], ['Inscrição municipal', c.inscricaoMunicipal || '—'],
    ['Endereço', [c.endereco, c.cidade, c.estado].filter(Boolean).join(' — ') || '—'], ['CEP', c.cep || '—'],
    ['Telefone', c.telefone || '—'], ['Situação', c.ativa ? 'Ativo' : 'Inativo'],
  ] : [];

  return (
    <Reveal className="grid gap-4 lg:grid-cols-2">
      <Cartao aria-labelledby="titulo-dados">
        <CartaoTitulo id="titulo-dados" acoes={alterar && c && (
          <Button variant="outline" size="sm" onClick={() => setEditando(true)}><Pencil aria-hidden /> Editar</Button>)}>
          Dados cadastrais
        </CartaoTitulo>
        {cliente.isPending
          ? <div className="grid gap-2">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-4 w-2/3" />)}</div>
          : (
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
              {dados.map(([rotulo, valor]) => (
                <div key={rotulo} className="contents">
                  <dt className="text-muted-foreground">{rotulo}</dt>
                  <dd className="num min-w-0 break-words">{valor}</dd>
                </div>
              ))}
            </dl>
          )}
      </Cartao>

      <Cartao aria-labelledby="titulo-importacoes">
        <CartaoTitulo id="titulo-importacoes" acoes={
          <Button variant="outline" size="sm" asChild><Link to="../importacoes" viewTransition><Upload aria-hidden /> Importar</Link></Button>}>
          Importações
        </CartaoTitulo>
        {documentos.isPending
          ? <div className="grid grid-cols-2 gap-2">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-14" />)}</div>
          : (
            <Stagger as="ul" className="grid grid-cols-2 gap-2">
              {porStatus.map(({ status, total }) => (
                <StaggerItem as="li" key={status} className="rounded-md border p-3">
                  <p className="text-muted-foreground">{STATUS_DOCUMENTO[status].rotulo}</p>
                  {/* cor só quando indica estado: erro > 0 */}
                  <AnimatedNumber valor={total} className={cn('text-xl font-semibold', status === 'ERRO' && total > 0 && 'text-danger')} />
                </StaggerItem>
              ))}
            </Stagger>
          )}
      </Cartao>

      {c && <ClienteDialog cliente={c} aberto={editando} aoMudar={setEditando} />}
    </Reveal>
  );
}
