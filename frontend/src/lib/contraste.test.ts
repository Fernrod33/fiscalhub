import { describe, expect, it } from 'vitest';
import { CONTRASTE_AA, contraste, sugerirCor } from './contraste';

describe('contraste WCAG', () => {
  it('mesma conta do backend', () => {
    expect(contraste('#ffffff', '#000000')).toBeCloseTo(21, 2);
    expect(contraste('#0b4f6c', '#ffffff')).toBeGreaterThan(CONTRASTE_AA);
    expect(contraste('#ffd84d', '#ffffff')).toBeLessThan(CONTRASTE_AA);
  });

  it('sugere a cor mais próxima que passa (escurece no branco, clareia no escuro)', () => {
    const noBranco = sugerirCor('#ffd84d', '#ffffff')!;
    expect(contraste(noBranco, '#ffffff')).toBeGreaterThanOrEqual(CONTRASTE_AA);
    const noEscuro = sugerirCor('#0b4f6c', '#111a2b')!;
    expect(contraste(noEscuro, '#111a2b')).toBeGreaterThanOrEqual(CONTRASTE_AA);
    expect(sugerirCor('#0b4f6c', '#ffffff')).toBeNull();
  });
});
