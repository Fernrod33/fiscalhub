import { AlertOctagon, AlertTriangle, Info, CircleAlert } from 'lucide-react';
import type { Tom } from '@/components/selo';
import type { PontoAtencao } from '@/lib/api/tipos';

export const SEVERIDADES: Record<PontoAtencao['severidade'], { rotulo: string; tom: Tom; Icone: typeof Info }> = {
  CRITICA: { rotulo: 'Crítica', tom: 'danger', Icone: AlertOctagon },
  ALTA: { rotulo: 'Alta', tom: 'warning', Icone: AlertTriangle },
  MEDIA: { rotulo: 'Média', tom: 'info', Icone: CircleAlert },
  INFO: { rotulo: 'Informativa', tom: 'info', Icone: Info },
};

export const CATEGORIAS: Record<PontoAtencao['categoria'], string> = {
  CONFORMIDADE: 'Conformidade', RISCO: 'Risco', OPORTUNIDADE: 'Oportunidade', MONITORAMENTO: 'Monitoramento',
};

export const ESTADOS: Record<string, string> = { NOVO: 'Novo', EM_ANALISE: 'Em análise', RESOLVIDO: 'Resolvido', DESCARTADO: 'Descartado' };
