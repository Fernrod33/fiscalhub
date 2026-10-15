import { Link } from 'react-router-dom';
import { usePermissoes } from '@/features/auth/permissoes';

/** Mapear colunas e de-para reprocessa o documento: ADMIN e CONSULTOR. O assistente vê que está aguardando. */
export function MapearPlanilha({ documentoId, prefixo }: { documentoId: number; prefixo?: string }) {
  const { alterar } = usePermissoes();
  if (!alterar) {
    return <p className="text-muted-foreground">Aguardando um consultor ou administrador mapear esta planilha.</p>;
  }
  return (
    <p>{prefixo}<Link className="font-medium text-primary underline-offset-4 hover:underline" viewTransition
                      to={`/importacoes/${documentoId}/planilha`}>Mapear colunas e período</Link></p>
  );
}
