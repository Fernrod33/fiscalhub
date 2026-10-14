import { AnimatePresence, motion } from 'motion/react';
import type { DocumentoStatus } from '@/lib/api/tipos';
import { STATUS_DOCUMENTO } from '@/lib/rotulos';
import { duracao, segundos } from '@/styles/motion';
import { useMovimentoReduzido } from '@/components/motion/use-movimento-reduzido';
import { Selo } from '@/components/selo';

/** Selo de status: troca com fade quando o processamento avança; pulsa enquanto processa. */
export function StatusDocumento({ status }: { status: DocumentoStatus }) {
  const reduzido = useMovimentoReduzido();
  const { rotulo, tom } = STATUS_DOCUMENTO[status];
  return (
    <span className="inline-grid whitespace-nowrap" role="status" aria-live="polite">
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span key={status} className="inline-flex"
                     initial={reduzido ? false : { opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
                     transition={{ duration: segundos(duracao.base) }}>
          <Selo tom={tom}>
            {(status === 'PROCESSANDO' || status === 'RECEBIDO') && (
              <span className="size-1.5 animate-pulse rounded-full bg-current" aria-hidden />
            )}
            {rotulo}
          </Selo>
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
