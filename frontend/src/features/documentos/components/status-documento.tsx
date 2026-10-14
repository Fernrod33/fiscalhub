import { AnimatePresence, motion } from 'motion/react';
import type { DocumentoStatus } from '@/lib/api/tipos';
import { STATUS_DOCUMENTO } from '@/lib/rotulos';
import { duracao, segundos } from '@/styles/motion';
import { useMovimentoReduzido } from '@/components/motion/use-movimento-reduzido';
import { cn } from '@/lib/utils';

const TONS = {
  info: 'bg-info-bg text-info',
  warning: 'bg-warning-bg text-warning',
  success: 'bg-success-bg text-success',
  danger: 'bg-danger-bg text-danger',
};

/** Selo de status: troca com fade quando o processamento avança; pulsa enquanto processa. */
export function StatusDocumento({ status }: { status: DocumentoStatus }) {
  const reduzido = useMovimentoReduzido();
  const { rotulo, tom } = STATUS_DOCUMENTO[status];
  return (
    <span className="inline-grid whitespace-nowrap" role="status" aria-live="polite">
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span key={status} className={cn('inline-flex items-center gap-1.5 rounded-sm px-2 py-0.5 text-xs font-semibold', TONS[tom])}
                     initial={reduzido ? false : { opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                     transition={{ duration: segundos(duracao.base) }}>
          {(status === 'PROCESSANDO' || status === 'RECEBIDO') && (
            <span className="size-1.5 animate-pulse rounded-full bg-current" aria-hidden />
          )}
          {rotulo}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
