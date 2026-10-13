import client from './client';
import type { DocumentoFiscal } from '../types';

export async function getDocumentosCliente(clienteId: number): Promise<DocumentoFiscal[]> {
  const res = await client.get<DocumentoFiscal[]>(`/api/clientes/${clienteId}/documentos`);
  return res.data;
}

export async function importarDocumento(clienteId: number, formData: FormData): Promise<DocumentoFiscal> {
  const res = await client.post<DocumentoFiscal>(`/api/clientes/${clienteId}/documentos`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function downloadDocumentoArquivo(clienteId: number, documentoId: number): Promise<Blob> {
  const res = await client.get(`/api/clientes/${clienteId}/documentos/${documentoId}/arquivo`, {
    responseType: 'blob',
  });
  return res.data;
}
