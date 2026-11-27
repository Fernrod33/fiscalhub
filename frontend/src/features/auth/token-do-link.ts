import { useEffect, useState } from 'react';

/**
 * Token do link do e-mail (fragmento #): lido uma vez e apagado da barra de endereços, para não ficar no histórico
 * nem ser copiado junto com o endereço.
 */
export function useTokenDoLink() {
  const [token] = useState(() => window.location.hash.slice(1));
  useEffect(() => {
    if (window.location.hash) window.history.replaceState(window.history.state, '', window.location.pathname);
  }, []);
  return token;
}
