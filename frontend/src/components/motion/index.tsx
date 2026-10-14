/**
 * Componentes de movimento reutilizáveis (pacote motion). Só animam transform e opacity
 * (exceto Collapse, para blocos pequenos). Com movimento reduzido, viram fade curto ou nada.
 */
import { useEffect, type ReactNode } from 'react';
import { animate, AnimatePresence, motion, useMotionValue, useTransform, type Variants } from 'motion/react';
import { curva, duracao, segundos } from '@/styles/motion';
import { inteiro } from '@/lib/formatadores';
import { useMovimentoReduzido } from './use-movimento-reduzido';

const DESLOCAMENTO = 8;

function variantes(reduzido: boolean): Variants {
  return reduzido
    ? { oculto: { opacity: 0 }, visivel: { opacity: 1, transition: { duration: segundos(duracao.fast) } } }
    : {
      oculto: { opacity: 0, y: DESLOCAMENTO },
      visivel: { opacity: 1, y: 0, transition: { duration: segundos(duracao.slow), ease: curva.standard } },
    };
}

/** Entrada com fade e deslocamento. */
export function Reveal({ children, className }: { children: ReactNode; className?: string }) {
  const reduzido = useMovimentoReduzido();
  return (
    <motion.div className={className} initial="oculto" animate="visivel" variants={variantes(reduzido)}>
      {children}
    </motion.div>
  );
}

/** Cascata: os filhos <StaggerItem> entram um após o outro. */
export function Stagger({ children, className, as = 'div' }: { children: ReactNode; className?: string; as?: 'div' | 'ul' }) {
  const reduzido = useMovimentoReduzido();
  const Componente = as === 'ul' ? motion.ul : motion.div;
  return (
    <Componente className={className} initial="oculto" animate="visivel"
                variants={{ visivel: { transition: { staggerChildren: reduzido ? 0 : 0.05 } } }}>
      {children}
    </Componente>
  );
}

export function StaggerItem({ children, className, as = 'div' }: { children: ReactNode; className?: string; as?: 'div' | 'li' }) {
  const reduzido = useMovimentoReduzido();
  const Componente = as === 'li' ? motion.li : motion.div;
  return <Componente className={className} variants={variantes(reduzido)}>{children}</Componente>;
}

/** Contagem do valor anterior até o novo, sempre pelo formatador central. */
export function AnimatedNumber({ valor, formatar = inteiro, className }: {
  valor: number;
  formatar?: (n: number) => string;
  className?: string;
}) {
  const reduzido = useMovimentoReduzido();
  const atual = useMotionValue(reduzido ? valor : 0);
  const texto = useTransform(atual, formatar);
  useEffect(() => {
    if (reduzido) {
      atual.set(valor);
      return;
    }
    const controle = animate(atual, valor, { duration: 0.8, ease: curva.standard });
    return () => controle.stop();
  }, [atual, valor, reduzido]);
  return (
    <span className={className} data-numerico>
      <motion.span aria-hidden>{texto}</motion.span>
      <span className="sr-only">{formatar(valor)}</span>
    </span>
  );
}

/** Expandir/recolher (ex.: memória de cálculo). Para blocos pequenos: anima a altura. */
export function Collapse({ aberto, children, id }: { aberto: boolean; children: ReactNode; id?: string }) {
  const reduzido = useMovimentoReduzido();
  return (
    <AnimatePresence initial={false}>
      {aberto && (
        <motion.div id={id} key="conteudo" style={{ overflow: 'hidden' }}
                    initial={reduzido ? { opacity: 0 } : { height: 0, opacity: 0 }}
                    animate={reduzido ? { opacity: 1 } : { height: 'auto', opacity: 1 }}
                    exit={reduzido ? { opacity: 0 } : { height: 0, opacity: 0 }}
                    transition={{ duration: segundos(reduzido ? duracao.fast : duracao.base), ease: curva.standard }}>
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
