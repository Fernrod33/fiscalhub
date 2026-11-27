import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useMovimentoReduzido } from './use-movimento-reduzido';

function simularPreferencia(inicial: boolean) {
  let ouvinte: (() => void) | null = null;
  const mq = {
    matches: inicial,
    addEventListener: (_: string, f: () => void) => { ouvinte = f; },
    removeEventListener: () => { ouvinte = null; },
  };
  vi.spyOn(window, 'matchMedia').mockImplementation(() => mq as unknown as MediaQueryList);
  return (valor: boolean) => act(() => { mq.matches = valor; ouvinte?.(); });
}

afterEach(() => vi.restoreAllMocks());

describe('useMovimentoReduzido', () => {
  it('reflete a preferência do sistema e reage à mudança', () => {
    const mudar = simularPreferencia(false);
    const { result } = renderHook(() => useMovimentoReduzido());
    expect(result.current).toBe(false);

    mudar(true);
    expect(result.current).toBe(true);
  });
});
