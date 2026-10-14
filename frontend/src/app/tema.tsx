import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { lerPreferencia, salvarPreferencia } from '@/lib/preferencias';

export type Tema = 'claro' | 'escuro' | 'sistema';

const TemaContext = createContext<{ tema: Tema; escuro: boolean; setTema: (t: Tema) => void } | null>(null);

const consulta = () => window.matchMedia('(prefers-color-scheme: dark)');

/** Tema claro/escuro/sistema: aplica a classe .dark no <html> e lembra a escolha no navegador. */
export function TemaProvider({ children }: { children: ReactNode }) {
  const [tema, setTemaEstado] = useState<Tema>(() => lerPreferencia<Tema>('tema', 'sistema'));
  const [sistemaEscuro, setSistemaEscuro] = useState(() => consulta().matches);

  useEffect(() => {
    const mq = consulta();
    const aoMudar = () => setSistemaEscuro(mq.matches);
    mq.addEventListener('change', aoMudar);
    return () => mq.removeEventListener('change', aoMudar);
  }, []);

  const escuro = tema === 'escuro' || (tema === 'sistema' && sistemaEscuro);
  useEffect(() => {
    document.documentElement.classList.toggle('dark', escuro);
  }, [escuro]);

  const setTema = (t: Tema) => {
    setTemaEstado(t);
    salvarPreferencia('tema', t);
  };

  return <TemaContext.Provider value={{ tema, escuro, setTema }}>{children}</TemaContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTema() {
  const ctx = useContext(TemaContext);
  if (!ctx) throw new Error('useTema precisa do TemaProvider');
  return ctx;
}
