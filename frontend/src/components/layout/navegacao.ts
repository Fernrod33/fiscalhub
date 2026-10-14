import { Building2, LayoutDashboard, ScrollText, Upload, Users, type LucideIcon } from 'lucide-react';

export type ItemNavegacao = { rotulo: string; caminho: string; icone: LucideIcon; soAdmin?: boolean };

/** Itens do menu lateral e da paleta de comandos (fonte única). */
export const NAVEGACAO: ItemNavegacao[] = [
  { rotulo: 'Painel', caminho: '/', icone: LayoutDashboard },
  { rotulo: 'Clientes', caminho: '/clientes', icone: Building2 },
  { rotulo: 'Importações', caminho: '/importacoes', icone: Upload },
];

export const CONFIGURACOES: ItemNavegacao[] = [
  { rotulo: 'Usuários', caminho: '/configuracoes/usuarios', icone: Users, soAdmin: true },
  { rotulo: 'Auditoria', caminho: '/configuracoes/auditoria', icone: ScrollText, soAdmin: true },
];
