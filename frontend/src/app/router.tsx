import type { ComponentType } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import AppShell from '@/components/layout/app-shell';
import type { HandleRota } from '@/components/layout/trilha';
import { NomeCliente } from '@/features/clientes/components/nome-cliente';
import { ABAS_CLIENTE } from '@/features/clientes/rota';
import { CarregandoPagina, ErroRota, NaoEncontrado } from './erros';
import { RotaProtegida, SoAdmin } from './guardas';

/** Rota com código dividido (lazy) e ErrorBoundary próprio. */
const pagina = (importar: () => Promise<ComponentType>, trilha?: HandleRota['trilha']) => ({
  lazy: async () => ({ Component: await importar() }),
  ErrorBoundary: ErroRota,
  HydrateFallback: CarregandoPagina,
  handle: trilha ? { trilha } : undefined,
});

const padrao = <T extends { default: ComponentType }>(p: Promise<T>) => p.then((m) => m.default);
const futuras = () => import('@/features/clientes/pages/abas-futuras');

const ABAS: Record<(typeof ABAS_CLIENTE)[number][0], () => Promise<ComponentType>> = {
  'visao-geral': () => padrao(import('@/features/clientes/pages/visao-geral-page')),
  importacoes: () => padrao(import('@/features/documentos/pages/importacoes-cliente-page')),
  demonstracoes: () => futuras().then((m) => m.DemonstracoesPage),
  tributos: () => padrao(import('@/features/debitos/pages/tributos-page')),
  capacidade: () => futuras().then((m) => m.CapacidadePage),
  simulacoes: () => padrao(import('@/features/simulacoes/pages/simulacoes-page')),
  'pontos-de-atencao': () => futuras().then((m) => m.PontosAtencaoPage),
  relatorios: () => futuras().then((m) => m.RelatoriosPage),
};

export const router = createBrowserRouter([
  { path: '/login', ...pagina(() => padrao(import('@/features/auth/pages/login-page'))) },
  { path: '/cadastro', ...pagina(() => padrao(import('@/features/auth/pages/cadastro-page'))) },
  // Só em desenvolvimento: em produção o ramo some do bundle
  ...(import.meta.env.DEV ? [{ path: '/laboratorio-design', ...pagina(() => padrao(import('@/laboratorio/LaboratorioDesign'))) }] : []),
  {
    element: <RotaProtegida />,
    ErrorBoundary: ErroRota,
    HydrateFallback: CarregandoPagina,
    children: [
      { path: '/trocar-senha', ...pagina(() => padrao(import('@/features/auth/pages/trocar-senha-page'))) },
      {
        element: <AppShell />,
        ErrorBoundary: ErroRota,
        children: [
          { index: true, ...pagina(() => padrao(import('@/features/painel/pages/painel-page')), 'Painel') },
          {
            path: 'clientes',
            handle: { trilha: 'Clientes' },
            children: [
              { index: true, ...pagina(() => padrao(import('@/features/clientes/pages/clientes-page'))) },
              {
                path: ':clienteId',
                ...pagina(() => padrao(import('@/features/clientes/pages/cliente-layout')), (p) => <NomeCliente id={Number(p.clienteId)} />),
                children: [
                  { index: true, element: <Navigate to="visao-geral" replace /> },
                  ...ABAS_CLIENTE.map(([caminho, rotulo]) => ({ path: caminho, ...pagina(ABAS[caminho], rotulo) })),
                ],
              },
            ],
          },
          { path: 'importacoes', ...pagina(() => padrao(import('@/features/documentos/pages/importacoes-page')), 'Importações') },
          {
            path: 'configuracoes',
            element: <SoAdmin />,
            handle: { trilha: 'Configurações' },
            children: [
              { index: true, element: <Navigate to="usuarios" replace /> },
              { path: 'usuarios', ...pagina(() => padrao(import('@/features/usuarios/pages/usuarios-page')), 'Usuários') },
              { path: 'auditoria', ...pagina(() => padrao(import('@/features/auditoria/pages/auditoria-page')), 'Auditoria') },
            ],
          },
          { path: '*', element: <NaoEncontrado /> },
        ],
      },
    ],
  },
]);
