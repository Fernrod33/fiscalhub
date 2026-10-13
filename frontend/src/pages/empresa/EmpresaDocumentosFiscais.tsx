import { useEffect, useMemo, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { downloadDocumentoArquivo, getDocumentosEmpresa, importarDocumento } from '../../api/documentos';
import type { EmpresaAreaOutletContext } from './EmpresaArea';
import './EmpresaArea.css';
import { mensagemErro } from '../../api/client';

type DocumentoFormState = {
  tipo: string;
  esfera: string;
  juridicao: string;
  tributos: string[];
  periodoInicio: string;
  periodoFim: string;
  observacao: string;
  arquivo: File | null;
};

const TRIBUTOS_OPTIONS = ['IRPJ', 'CSLL', 'PIS', 'COFINS', 'IPI', 'ICMS', 'OUTROS'];

export default function EmpresaDocumentosFiscais() {
  const { empresa } = useOutletContext<EmpresaAreaOutletContext>();
  const [documentos, setDocumentos] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DocumentoFormState>({
    tipo: 'DCTF_WEB',
    esfera: 'FEDERAL',
    juridicao: 'UNIAO',
    tributos: ['CSLL'],
    periodoInicio: '',
    periodoFim: '',
    observacao: '',
    arquivo: null,
  });

  const filtroTributos = useMemo(() => form.tributos.join(', '), [form.tributos]);

  async function loadDocumentos() {
    setLoading(true);
    setError('');
    try {
      const data = await getDocumentosEmpresa(empresa.id);
      setDocumentos(data);
    } catch (err) {
      setError(mensagemErro(err, 'Erro ao carregar documentos.'));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { loadDocumentos(); }, [empresa.id]);

  function onFieldChange<K extends keyof DocumentoFormState>(field: K, value: DocumentoFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  function toggleTributo(tributo: string) {
    setForm((prev) => ({
      ...prev,
      tributos: prev.tributos.includes(tributo)
        ? prev.tributos.filter((item) => item !== tributo)
        : [...prev.tributos, tributo],
    }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (!form.arquivo) {
      setError('Selecione um arquivo para importar.');
      return;
    }

    if (!form.periodoInicio || !form.periodoFim) {
      setError('Informe o período inicial e final.');
      return;
    }

    if (form.tributos.length === 0) {
      setError('Selecione pelo menos um tributo relacionado.');
      return;
    }

    const payload = { 
      tipo: form.tipo,
      esfera: form.esfera,
      juridicao: form.juridicao,
      tributos: form.tributos,
      periodoInicio: form.periodoInicio,
      periodoFim: form.periodoFim,
      observacao: form.observacao,
      retificacao: false,
    };

    const formData = new FormData();
    formData.append('arquivo', form.arquivo);
    formData.append('documento', new Blob([JSON.stringify(payload)], { type: 'application/json' }));

    try {
      await importarDocumento(empresa.id, formData);
      setSuccess('Documento importado com sucesso.');
      setShowForm(false);
      setForm({
        tipo: 'DCTF_WEB',
        esfera: 'FEDERAL',
        juridicao: 'UNIAO',
        tributos: ['CSLL'],
        periodoInicio: '',
        periodoFim: '',
        observacao: '',
        arquivo: null,
      });
      await loadDocumentos();
    } catch (err) {
      setError(mensagemErro(err, 'Erro ao importar documento.'));
    }
  }

  async function handleDownload(documentoId: number, nomeArquivo: string) {
    try {
      const blob = await downloadDocumentoArquivo(empresa.id, documentoId);
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = nomeArquivo;
      anchor.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setError(mensagemErro(err, 'Erro ao baixar documento.'));
    }
  }

  return (
    <div>
      <div className="empresa-section">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginBottom: 18 }}>
          <h2>Documentos Fiscais</h2>
          <button className="btn-primary" onClick={() => setShowForm((prev) => !prev)}>
            {showForm ? 'Fechar formulário' : 'Importar documento'}
          </button>
        </div>

        {error && <div className="alert danger">{error}</div>}
        {success && <div className="alert success">{success}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="document-form">
            <div className="form-grid">
              <label>
                <span>Tipo do documento</span>
                <select value={form.tipo} onChange={(e) => onFieldChange('tipo', e.target.value)}>
                  <option value="DCTF_WEB">DCTFWeb</option>
                  <option value="EFD_CONTRIBUICOES">EFD-Contribuições</option>
                  <option value="EFD_ICMS_IPI">EFD ICMS/IPI</option>
                  <option value="ICMS">ICMS</option>
                  <option value="IRPJ">IRPJ</option>
                  <option value="DEMONSTRACAO_CONTABIL">Demonstração Contábil</option>
                  <option value="OUTRO">Outro</option>
                </select>
              </label>

              <label>
                <span>Esfera</span>
                <select value={form.esfera} onChange={(e) => onFieldChange('esfera', e.target.value)}>
                  <option value="FEDERAL">Federal</option>
                  <option value="ESTADUAL">Estadual</option>
                  <option value="CONTABIL">Contábil</option>
                </select>
              </label>

              <label>
                <span>Jurisdição</span>
                <select value={form.juridicao} onChange={(e) => onFieldChange('juridicao', e.target.value)}>
                  <option value="UNIAO">União</option>
                  <option value="PARANA">Paraná</option>
                  <option value="NAO_APLICAVEL">Não aplicável</option>
                </select>
              </label>

              <label>
                <span>Período inicial</span>
                <input type="date" value={form.periodoInicio} onChange={(e) => onFieldChange('periodoInicio', e.target.value)} />
              </label>

              <label>
                <span>Período final</span>
                <input type="date" value={form.periodoFim} onChange={(e) => onFieldChange('periodoFim', e.target.value)} />
              </label>

              <label className="full-width">
                <span>Observação</span>
                <textarea value={form.observacao} onChange={(e) => onFieldChange('observacao', e.target.value)} rows={3} />
              </label>

              <label className="full-width">
                <span>Arquivo</span>
                <input type="file" accept=".pdf,.xml,.csv,.xlsx" onChange={(e) => onFieldChange('arquivo', e.target.files?.[0] || null)} />
              </label>

              <div className="full-width tributos-box">
                <span>Tributos relacionados</span>
                <div className="tributos-options">
                  {TRIBUTOS_OPTIONS.map((tributo) => (
                    <label key={tributo} className="check-option">
                      <input type="checkbox" checked={form.tributos.includes(tributo)} onChange={() => toggleTributo(tributo)} />
                      {tributo}
                    </label>
                  ))}
                </div>
                <small>{filtroTributos || 'Nenhum tributo selecionado'}</small>
              </div>
            </div>

            <div className="form-actions">
              <button type="submit" className="btn-primary">Salvar importação</button>
            </div>
          </form>
        )}

        {loading ? (
          <p className="stub-empty">Carregando documentos…</p>
        ) : documentos.length === 0 ? (
          <p className="stub-empty">Nenhum documento fiscal importado.</p>
        ) : (
          <div className="table-list">
            {documentos.map((doc) => (
              <div key={doc.id} className="document-row">
                <div>
                  <strong>{doc.nomeOriginalArquivo}</strong>
                  <div className="document-meta">
                    <span>{doc.tipo}</span>
                    <span>{doc.esfera}</span>
                    <span>{doc.juridicao}</span>
                    <span>{doc.status}</span>
                  </div>
                </div>
                <div className="document-actions">
                  <span>{doc.tributos?.join(', ') || '—'}</span>
                  <button className="btn-secondary btn-sm" onClick={() => handleDownload(doc.id, doc.nomeOriginalArquivo)}>
                    Baixar
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
