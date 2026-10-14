import { Fragment, type ReactNode } from 'react';
import { Link, useMatches, type Params } from 'react-router-dom';
import {
  Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';

/** handle.trilha de cada rota: texto fixo ou função dos parâmetros (ex.: nome do cliente). */
export type HandleRota = { trilha?: ReactNode | ((params: Params) => ReactNode) };

/** Breadcrumbs a partir das rotas ativas. Abaixo de 1024px mostra só a tela atual, numa linha. */
export function Trilha() {
  const itens = useMatches()
    .filter((m) => (m.handle as HandleRota | undefined)?.trilha)
    .map((m) => {
      const { trilha } = m.handle as HandleRota;
      return { caminho: m.pathname, rotulo: typeof trilha === 'function' ? trilha(m.params) : trilha };
    });
  if (itens.length === 0) return null;

  return (
    <Breadcrumb>
      <BreadcrumbList className="flex-nowrap">
        {itens.map((item, i) => (
          <Fragment key={item.caminho}>
            {i > 0 && <BreadcrumbSeparator className="max-lg:hidden" />}
            <BreadcrumbItem className={i === itens.length - 1 ? 'min-w-0' : 'whitespace-nowrap max-lg:hidden'}>
              {i === itens.length - 1
                ? <BreadcrumbPage className="truncate">{item.rotulo}</BreadcrumbPage>
                : <BreadcrumbLink asChild><Link to={item.caminho} viewTransition>{item.rotulo}</Link></BreadcrumbLink>}
            </BreadcrumbItem>
          </Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
