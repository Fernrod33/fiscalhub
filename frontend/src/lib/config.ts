/** Configuração do frontend (variáveis VITE_*), com os mesmos padrões do backend. */
export const UPLOAD = {
  /** Mesma lista do DocumentoFiscalService (TXT do SPED, PDF, CSV, XLSX). */
  extensoes: ['.txt', '.pdf', '.csv', '.xlsx'],
  /** Mesmo limite de fiscalhub.upload.max-size (padrão 500 MB). */
  limiteBytes: Number(import.meta.env.VITE_UPLOAD_MAX_BYTES ?? 500 * 1024 * 1024),
};
