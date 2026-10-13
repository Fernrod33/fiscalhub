import client from './client';
import type { DocumentoFiscal } from '../types';

export async function getDocumentosEmpresa(empresaId: number): Promise<DocumentoFiscal[]> {
  const res = await client.get<DocumentoFiscal[]>(`/api/empresas/${empresaId}/documentos`);
  return res.data;
}

export async function importarDocumento(empresaId: number, formData: FormData): Promise<DocumentoFiscal> {
  const res = await client.post<DocumentoFiscal>(`/api/empresas/${empresaId}/documentos`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function downloadDocumentoArquivo(empresaId: number, documentoId: number): Promise<Blob> {
  const res = await client.get(`/api/empresas/${empresaId}/documentos/${documentoId}/arquivo`, {
    responseType: 'blob',
  });
  return res.data;
}
