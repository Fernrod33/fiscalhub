import { useCliente } from '../api';

/** Razão social do cliente (breadcrumb); usa o cache do TanStack Query. */
export function NomeCliente({ id }: { id: number }) {
  return <>{useCliente(id).data?.razaoSocial ?? 'Cliente'}</>;
}
