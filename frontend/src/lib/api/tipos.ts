// Nomes de uso diário para os tipos gerados do OpenAPI (npm run gerar:api). Não escreva contratos à mão aqui.
import type { components } from './schema';

type S = components['schemas'];

export type LoginRequest = S['LoginRequest'];
export type SignupRequest = S['SignupRequest'];
export type Usuario = S['UsuarioResponseDTO'];
export type NovoUsuario = S['NovoUsuarioDTO'];
export type TrocaSenha = S['TrocaSenhaDTO'];
export type Papel = Usuario['papel'];
export type Cliente = S['ClienteResponseDTO'];
export type ClienteRequest = S['ClienteRequestDTO'];
export type Documento = S['DocumentoFiscalResponseDTO'];
export type DocumentoRequest = S['DocumentoFiscalRequestDTO'];
export type StatusDocumento = S['StatusDocumentoDTO'];
export type DocumentoStatus = Documento['status'];
export type Debito = S['DebitoResponseDTO'];
export type Simulacao = S['SimulacaoParcelamento'];
export type EventoAuditoria = S['AuditoriaEventoDTO'];
export type PaginaAuditoria = S['PagedModelAuditoriaEventoDTO'];
export type PaginaDocumentos = S['PagedModelDocumentoFiscalResponseDTO'];
