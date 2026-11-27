import { useSyncExternalStore } from 'react';

const CONSULTA = '(prefers-reduced-motion: reduce)';

function assinar(aoMudar: () => void) {
  const mq = window.matchMedia(CONSULTA);
  mq.addEventListener('change', aoMudar);
  return () => mq.removeEventListener('change', aoMudar);
}

/** Preferência do sistema por menos movimento (a mesma media query que o CSS usa). */
export function useMovimentoReduzido(): boolean {
  return useSyncExternalStore(assinar, () => window.matchMedia(CONSULTA).matches, () => false);
}
