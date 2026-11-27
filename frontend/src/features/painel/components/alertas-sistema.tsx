import { useQuery } from '@tanstack/react-query';
import { TriangleAlert } from 'lucide-react';
import { Cartao, CartaoTitulo } from '@/components/cartao';
import type { AlertaSistema, LayoutSemLeitura } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { http } from '@/lib/http';

/**
 * Só para quem mantém o sistema (fiscalhub.admin-sistema): para os demais a API responde 403 e nada aparece. Editais
 * com adesão terminando em até 30 dias, índices do mês não importados e os layouts que caíram sem leitura.
 */
export function AlertasSistema() {
  const alertas = useQuery({ queryKey: ['sistema', 'alertas'], queryFn: () => http.get<AlertaSistema[]>('/api/sistema/alertas'), retry: false });
  const layouts = useQuery({
    queryKey: ['sistema', 'layouts'], queryFn: () => http.get<LayoutSemLeitura[]>('/api/sistema/layouts-nao-reconhecidos'),
    retry: false, enabled: alertas.isSuccess,
  });
  if (!alertas.isSuccess || (alertas.data.length === 0 && !layouts.data?.length)) return null;
  return (
    <Cartao aria-labelledby="titulo-alertas-sistema">
      <CartaoTitulo id="titulo-alertas-sistema">Manutenção do sistema</CartaoTitulo>
      {alertas.data.length > 0 && (
        <ul className="grid gap-2">
          {alertas.data.map((a) => (
            <li key={a.mensagem} className="flex gap-2 text-sm">
              <TriangleAlert className="size-4 shrink-0 text-warning" aria-hidden />
              <span>{a.mensagem}{a.prazo && <> (até {fmt.data(a.prazo)})</>}</span>
            </li>
          ))}
        </ul>
      )}
      {!!layouts.data?.length && (
        <details className="mt-3 text-sm">
          <summary className="cursor-pointer font-medium">Documentos sem leitura em todos os escritórios ({layouts.data.reduce((t, l) => t + l.quantidade, 0)})</summary>
          <table className="mt-2 w-full text-left">
            <caption className="sr-only">Layouts não reconhecidos por assinatura</caption>
            <thead><tr><th className="p-1">Tipo</th><th className="p-1">Assinatura (sem dígitos)</th><th className="p-1 text-right">Qtde.</th><th className="p-1">Último</th></tr></thead>
            <tbody>
              {layouts.data.map((l) => (
                <tr key={l.tipo + l.assinatura} className="border-t">
                  <td className="p-1">{l.tipo}</td><td className="p-1">{l.assinatura}</td>
                  <td className="num p-1 text-right">{l.quantidade}</td><td className="p-1 whitespace-nowrap">{fmt.dataHora(l.ultimo)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </Cartao>
  );
}
