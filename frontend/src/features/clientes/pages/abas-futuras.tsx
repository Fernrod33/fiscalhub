import { AlertTriangle } from 'lucide-react';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';

/** Abas que dependem de fases futuras: estado vazio que explica o que falta. */
export function PontosAtencaoPage() {
  return (
    <Reveal>
      <EstadoVazio icone={AlertTriangle} titulo="Nenhum ponto de atenção"
                   descricao="Alertas sobre débitos, prazos e inconsistências dos arquivos aparecerão aqui." />
    </Reveal>
  );
}
