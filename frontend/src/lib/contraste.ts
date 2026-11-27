/** Contraste WCAG 2.1 entre cores #rrggbb: a mesma conta do backend (IdentidadeEscritorioService.contraste). */
export const CONTRASTE_AA = 4.5;

function luminancia(hex: string): number {
  const n = parseInt(hex.slice(1), 16);
  const canal = (c: number) => { const s = c / 255; return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * canal((n >> 16) & 0xff) + 0.7152 * canal((n >> 8) & 0xff) + 0.0722 * canal(n & 0xff);
}

export function contraste(a: string, b: string): number {
  const [la, lb] = [luminancia(a), luminancia(b)];
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

export const corValida = (v: string) => /^#[0-9a-fA-F]{6}$/.test(v);

/**
 * Ajuste sugerido: escurece (fundo claro) ou clareia (fundo escuro) a cor em passos de 5% até passar no AA.
 * Mantém o matiz; null se a cor já passa.
 */
export function sugerirCor(cor: string, fundo: string): string | null {
  if (contraste(cor, fundo) >= CONTRASTE_AA) return null;
  const alvo = luminancia(fundo) > 0.5 ? 0 : 255;
  const rgb = [1, 3, 5].map((i) => parseInt(cor.slice(i, i + 2), 16));
  for (let t = 0.05; t <= 1; t += 0.05) {
    const hex = '#' + rgb.map((c) => Math.round(c + (alvo - c) * t).toString(16).padStart(2, '0')).join('');
    if (contraste(hex, fundo) >= CONTRASTE_AA) return hex;
  }
  return alvo === 0 ? '#000000' : '#ffffff';
}
