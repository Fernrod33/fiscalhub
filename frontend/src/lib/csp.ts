/**
 * CSP sem 'unsafe-inline' (deploy/Caddyfile): o proxy gera um nonce por resposta, põe no cabeçalho e no
 * <meta property="csp-nonce"> do index.html. Importado antes de tudo no main.tsx.
 *
 * - <style> criados por bibliotecas (sonner injeta o CSS dos toasts; o react-remove-scroll do Radix injeta o bloqueio de
 *   rolagem com a largura da barra) recebem o nonce na criação. Estilo por propriedade (React, Recharts, motion) é CSSOM
 *   e a CSP não alcança.
 * - zod 4 testa `new Function` para o modo JIT: sem 'unsafe-eval' isso gera violação; o modo sem JIT basta aqui.
 */
import { z } from 'zod';

z.config({ jitless: true });

/** Nonce desta página (o navegador esconde o atributo; a propriedade continua legível). Sem o proxy (Vite), indefinido. */
const lido = document.querySelector<HTMLMetaElement>('meta[property="csp-nonce"]')?.nonce;
export const nonceCsp = lido && !lido.includes('{') ? lido : undefined;

if (nonceCsp) {
  const criar = document.createElement.bind(document);
  document.createElement = ((tag: string, opcoes?: ElementCreationOptions) => {
    const el = criar(tag, opcoes);
    if (tag.toLowerCase() === 'style') el.nonce = nonceCsp;
    return el;
  }) as typeof document.createElement;
}

/** HTML da prévia do relatório (iframe srcdoc, que herda a CSP): os <style> do template recebem o nonce. */
export const comNonce = (html: string) => (nonceCsp ? html.replace(/<style(?=[\s>])/g, `<style nonce="${nonceCsp}"`) : html);
