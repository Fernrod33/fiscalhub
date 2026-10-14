import { AlertTriangle, BookOpen, FileBarChart, Gauge } from 'lucide-react';
import { EstadoVazio } from '@/components/estado-vazio';
import { Reveal } from '@/components/motion';

/** Abas que dependem de fases futuras: estado vazio que explica o que falta. */
export function DemonstracoesPage() {
  return (
    <Reveal>
      <EstadoVazio icone={BookOpen} titulo="Importe a ECD"
                   descricao="O balanço e a DRE aparecem aqui depois da importação da Escrituração Contábil Digital." />
    </Reveal>
  );
}

export function CapacidadePage() {
  return (
    <Reveal>
      <EstadoVazio icone={Gauge} titulo="Capacidade de pagamento"
                   descricao="Calculada a partir das demonstrações importadas, em breve." />
    </Reveal>
  );
}

export function PontosAtencaoPage() {
  return (
    <Reveal>
      <EstadoVazio icone={AlertTriangle} titulo="Nenhum ponto de atenção"
                   descricao="Alertas sobre débitos, prazos e inconsistências dos arquivos aparecerão aqui." />
    </Reveal>
  );
}

export function RelatoriosPage() {
  return (
    <Reveal>
      <EstadoVazio icone={FileBarChart} titulo="Relatórios"
                   descricao="Relatórios de tributos pagos e de débitos ficam disponíveis depois das importações." />
    </Reveal>
  );
}
