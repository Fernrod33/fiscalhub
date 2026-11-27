import { afterEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Reveal, Stagger, StaggerItem } from '.';

afterEach(() => vi.restoreAllMocks());

// o axe do E2E (reducedMotion: 'reduce') media o contraste no meio do fade: com movimento reduzido não há fade
it('com movimento reduzido, Reveal e Stagger já montam opacos', () => {
  vi.spyOn(window, 'matchMedia').mockImplementation((q) => ({
    matches: true, media: q, addEventListener: () => {}, removeEventListener: () => {},
  }) as unknown as MediaQueryList);
  render(<><Reveal>revelado</Reveal><Stagger as="ul"><StaggerItem as="li">item</StaggerItem></Stagger></>);
  for (const texto of ['revelado', 'item']) {
    expect(screen.getByText(texto).style.opacity).not.toBe('0');
  }
});
