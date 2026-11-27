import { useSessao } from './sessao';

/** O que o papel do usuário permite na interface (o backend valida de novo com @PreAuthorize). */
export function usePermissoes() {
  const papel = useSessao()?.papel;
  return {
    /** Cadastrar, alterar e excluir clientes, documentos e simulações: ADMIN e CONSULTOR. */
    alterar: papel === 'ADMIN' || papel === 'CONSULTOR',
    /** Usuários e auditoria. */
    admin: papel === 'ADMIN',
  };
}
