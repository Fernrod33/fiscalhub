import { AnimatePresence, motion } from 'motion/react';
import { Check, Clock, X } from 'lucide-react';
import type { StatusDocumento } from '@/lib/api/tipos';
import * as fmt from '@/lib/formatadores';
import { ETAPAS } from '@/lib/rotulos';
import { cn } from '@/lib/utils';
import { curva, duracao, segundos } from '@/styles/motion';
import { useMovimentoReduzido } from '@/components/motion/use-movimento-reduzido';

/**
 * Etapas do processamento (recebido → lendo → validando → gravando → pronto) com o percentual de leitura.
 * O trecho entre etapas enche com scaleX (só transform); com movimento reduzido, troca sem animação.
 */
export function LinhaDoTempo({ status, enviando }: { status?: StatusDocumento; enviando?: number }) {
  const reduzido = useMovimentoReduzido();
  const atual = status ? ETAPAS.findIndex((e) => e.etapa === status.etapa) : -1;
  const falhou = status?.status === 'ERRO';
  const aguardando = status?.status === 'AGUARDANDO_CLIENTE' || status?.status === 'AGUARDANDO_MAPEAMENTO';
  const concluido = status?.etapa === 'CONCLUIDO' && !falhou;
  const transicao = { duration: reduzido ? 0 : segundos(duracao.slow), ease: curva.standard };

  const descricao = enviando !== undefined && !status
    ? `Enviando ${fmt.progresso(enviando)}`
    : falhou ? `Falhou na etapa ${ETAPAS[Math.max(atual, 0)].rotulo.toLowerCase()}`
      : aguardando ? 'Aguardando você'
        : concluido ? 'Pronto'
          : status ? `${ETAPAS[atual].rotulo}${status.etapa === 'LENDO' ? ` ${fmt.progresso(status.progresso / 100)}` : ''}` : 'Na fila';

  return (
    <div className="grid gap-1.5">
      <ol className="flex items-center" aria-label="Etapas do processamento">
        {ETAPAS.map((e, i) => {
          const feita = concluido || i < atual || (i === atual && aguardando);
          const corrente = i === atual && !concluido && !aguardando;
          const rotulo = i === ETAPAS.length - 1 && aguardando ? 'Aguardando você' : e.rotulo;
          return (
            <li key={e.etapa} className={cn('flex items-center', i > 0 && 'flex-1')}
                aria-current={corrente ? 'step' : undefined}>
              {i > 0 && (
                <span className="mx-1 h-0.5 flex-1 overflow-hidden rounded-full bg-muted" aria-hidden>
                  <motion.span className={cn('block h-full origin-left', falhou && i > atual ? 'bg-muted' : 'bg-primary')}
                               initial={false} animate={{ scaleX: i <= atual || concluido ? 1 : 0 }} transition={transicao} />
                </span>
              )}
              <span title={rotulo}
                    className={cn('grid size-5 shrink-0 place-items-center rounded-full border text-[0.625rem] transition-colors duration-(--dur-base)',
                      feita && 'border-primary bg-primary text-primary-foreground',
                      corrente && !falhou && 'border-primary text-primary',
                      corrente && falhou && 'border-danger bg-danger text-danger-foreground',
                      !feita && !corrente && 'text-muted-foreground')}>
                <AnimatePresence mode="popLayout" initial={false}>
                  <motion.span key={feita ? 'ok' : corrente && falhou ? 'x' : 'n'} className="grid place-items-center"
                               initial={reduzido ? false : { opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }}
                               exit={{ opacity: 0 }} transition={transicao}>
                    {feita ? <Check className="size-3" aria-hidden />
                      : corrente && falhou ? <X className="size-3" aria-hidden />
                        : i === ETAPAS.length - 1 && aguardando ? <Clock className="size-3" aria-hidden /> : i + 1}
                  </motion.span>
                </AnimatePresence>
              </span>
              <span className="sr-only">{rotulo}{feita ? ' (concluída)' : corrente ? ' (em andamento)' : ''}</span>
            </li>
          );
        })}
      </ol>
      <p className={cn('num text-xs', falhou ? 'text-danger' : 'text-muted-foreground')} role="status" aria-live="polite">{descricao}</p>
    </div>
  );
}
