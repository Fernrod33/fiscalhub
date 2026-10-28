import { describe, expect, it } from 'vitest';
import { cnpjValido } from './cnpj';
import * as fmt from './formatadores';

// Intl usa espaço não separável entre "R$" e o valor
const semNbsp = (s: string) => s.replace(/\u00a0/g, ' ');

describe('formatadores pt-BR', () => {
  it('moeda', () => {
    expect(semNbsp(fmt.moeda(1284937.42))).toBe('R$ 1.284.937,42');
    expect(fmt.moeda(null)).toBe('—');
  });

  it('percentual recebe o valor como na API (1,5 = 1,5%)', () => {
    expect(semNbsp(fmt.percentual(13.8093))).toBe('13,81%');
  });

  it('CNPJ com máscara e valor sem formato preservado', () => {
    expect(fmt.cnpj('11222333000181')).toBe('11.222.333/0001-81');
    expect(fmt.cnpj('123')).toBe('123');
  });

  it('competência e data sem deslocamento de fuso', () => {
    expect(fmt.competencia('2024-03-01')).toBe('03/2024');
    expect(fmt.data('2024-03-01')).toBe('01/03/2024');
  });

  it('tamanho de arquivo', () => {
    expect(fmt.tamanhoArquivo(512)).toBe('512 B');
    expect(fmt.tamanhoArquivo(18_402_311)).toBe('17,5 MB');
  });
});

describe('cnpjValido', () => {
  it('aceita CNPJ válido com ou sem máscara e recusa inválidos', () => {
    expect(cnpjValido('11.222.333/0001-81')).toBe(true);
    expect(cnpjValido('11222333000181')).toBe(true);
    expect(cnpjValido('11222333000182')).toBe(false);
    expect(cnpjValido('11111111111111')).toBe(false);
    expect(cnpjValido('123')).toBe(false);
  });
});

// Os mesmos casos conferidos pelo backend (RenderizadorRelatorioTest): a formatação do PDF é a da tela
import casos from './formatacao.casos.json';

describe('formatação igual à do backend (relatórios)', () => {
  const porFormato: Record<string, (v: string) => string> = {
    moeda: (v) => fmt.moeda(Number(v)), percentual: (v) => fmt.percentual(Number(v)), inteiro: (v) => fmt.inteiro(Number(v)),
    competencia: fmt.competencia, data: fmt.data, cnpj: fmt.cnpj,
  };
  it.each(casos)('$formato $valor', ({ formato, valor, esperado }) => expect(porFormato[formato](valor)).toBe(esperado));
});
