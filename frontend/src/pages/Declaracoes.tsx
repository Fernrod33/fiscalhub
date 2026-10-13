import { useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '../context/AuthContext';
import { getMinhasEmpresas } from '../api/empresas';
import {
  getDeclaracoesByEmpresa,
  createDeclaracao,
  updateDeclaracao,
  deleteDeclaracao,
} from '../api/declaracoes';
import type { Empresa, Declaracao, TipoDeclaracao, StatusDeclaracao } from '../types';
import './Dashboard.css';
import './Declaracoes.css';

const TIPOS: TipoDeclaracao[] = ['ICMS', 'ISS', 'PIS', 'COFINS', 'IRPJ', 'CSLL', 'OUTRO'];
const STATUS_OPTIONS: StatusDeclaracao[] = ['PENDENTE', 'ENVIADA', 'PROCESSANDO', 'APROVADA', 'REJEITADA', 'PAGO'];

const EMPTY_FORM = {
  tipo: 'ICMS' as TipoDeclaracao,
  periodoInicio: '',
  periodoFim: '',
  valorTotal: '',
  valorImposto: '',
  status: 'PENDENTE' as StatusDeclaracao,
  dataVencimento: '',
  dataPagamento: '',
  observacoes: '',
};

export default function Declaracoes() {
  const { user } = useAuth();
  const [empresas, setEmpresas] = useState<Empresa[]>([]);
  const [selectedEmpresaId, setSelectedEmpresaId] = useState<number | null>(null);
  const [declaracoes, setDeclaracoes] = useState<Declaracao[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editTarget, setEditTarget] = useState<Declaracao | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  useEffect(() => {
    if (!user) return;
    getMinhasEmpresas()
      .then((emps) => {
        setEmpresas(emps);
        if (emps.length > 0) setSelectedEmpresaId(emps[0].id);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [user]);

  useEffect(() => {
    if (!selectedEmpresaId) { setDeclaracoes([]); return; }
    setLoading(true);
    getDeclaracoesByEmpresa(selectedEmpresaId)
      .then(setDeclaracoes)
      .catch(() => setDeclaracoes([]))
      .finally(() => setLoading(false));
  }, [selectedEmpresaId]);

  function openCreate() {
    setEditTarget(null);
    setForm(EMPTY_FORM);
    setError('');
    setShowModal(true);
  }

  function openEdit(d: Declaracao) {
    setEditTarget(d);
    setForm({
      tipo: d.tipo,
      periodoInicio: d.periodoInicio,
      periodoFim: d.periodoFim,
      valorTotal: String(d.valorTotal),
      valorImposto: String(d.valorImposto),
      status: d.status,
      dataVencimento: d.dataVencimento,
      dataPagamento: d.dataPagamento ?? '',
      observacoes: d.observacoes ?? '',
    });
    setError('');
    setShowModal(true);
  }

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!user || !selectedEmpresaId) return;
    setError('');
    setSaving(true);

    const payload = {
      tipo: form.tipo,
      periodoInicio: form.periodoInicio,
      periodoFim: form.periodoFim,
      valorTotal: parseFloat(form.valorTotal),
      valorImposto: parseFloat(form.valorImposto),
      status: form.status,
      dataVencimento: form.dataVencimento,
      dataPagamento: form.dataPagamento || null,
      observacoes: form.observacoes,
    };

    try {
      if (editTarget) {
        const updated = await updateDeclaracao(editTarget.id, payload);
        setDeclaracoes((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
        setSuccessMsg('Declaração atualizada!');
      } else {
        const created = await createDeclaracao(selectedEmpresaId, payload as Omit<Declaracao, 'id' | 'dataCriacao' | 'dataAtualizacao'>);
        setDeclaracoes((prev) => [created, ...prev]);
        setSuccessMsg('Declaração criada com sucesso!');
      }
      setShowModal(false);
      setTimeout(() => setSuccessMsg(''), 3000);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erro ao salvar declaração.';
      setError(msg);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: number) {
    if (!confirm('Deseja remover esta declaração?')) return;
    try {
      await deleteDeclaracao(id);
      setDeclaracoes((prev) => prev.filter((d) => d.id !== id));
    } catch {
      alert('Erro ao remover declaração.');
    }
  }

  const STATUS_CLASS: Record<string, string> = {
    PENDENTE: 'badge-yellow',
    ENVIADA: 'badge-blue',
    PROCESSANDO: 'badge-blue',
    APROVADA: 'badge-green',
    PAGO: 'badge-green',
    REJEITADA: 'badge-red',
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Declarações</h1>
          <p className="page-subtitle">Gerencie as declarações fiscais das suas empresas.</p>
        </div>
        <button className="btn-primary" onClick={openCreate} disabled={!selectedEmpresaId}>
          + Nova Declaração
        </button>
      </div>

      {successMsg && <div className="alert alert-success">{successMsg}</div>}

      {/* Company selector */}
      {empresas.length > 0 && (
        <div className="empresa-tabs">
          {empresas.map((emp) => (
            <button
              key={emp.id}
              className={`tab-btn${selectedEmpresaId === emp.id ? ' active' : ''}`}
              onClick={() => setSelectedEmpresaId(emp.id)}
            >
              {emp.nomeFantasia || emp.razaoSocial}
            </button>
          ))}
        </div>
      )}

      {empresas.length === 0 && !loading ? (
        <div className="empty-page">
          <span className="empty-icon">🏢</span>
          <p>Cadastre uma empresa primeiro para adicionar declarações.</p>
        </div>
      ) : loading ? (
        <div className="loading-state">Carregando declarações…</div>
      ) : declaracoes.length === 0 ? (
        <div className="empty-page">
          <span className="empty-icon">📋</span>
          <p>Nenhuma declaração para esta empresa.</p>
          <button className="btn-primary" onClick={openCreate}>Adicionar declaração</button>
        </div>
      ) : (
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Tipo</th>
                <th>Período</th>
                <th>Valor Total</th>
                <th>Imposto</th>
                <th>Vencimento</th>
                <th>Status</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {declaracoes.map((d) => (
                <tr key={d.id}>
                  <td><strong>{d.tipo}</strong></td>
                  <td className="mono">{d.periodoInicio} → {d.periodoFim}</td>
                  <td>{fmt(d.valorTotal)}</td>
                  <td>{fmt(d.valorImposto)}</td>
                  <td className="mono">{d.dataVencimento}</td>
                  <td><span className={`badge ${STATUS_CLASS[d.status] ?? 'badge-gray'}`}>{d.status}</span></td>
                  <td className="actions-cell">
                    <button className="btn-icon btn-edit" onClick={() => openEdit(d)} title="Editar">✏️</button>
                    <button className="btn-icon btn-danger" onClick={() => handleDelete(d.id)} title="Remover">🗑</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal modal--lg" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editTarget ? 'Editar Declaração' : 'Nova Declaração'}</h2>
              <button className="modal-close" onClick={() => setShowModal(false)}>✕</button>
            </div>
            <form onSubmit={handleSubmit} className="modal-body">
              {error && <div className="alert alert-error">{error}</div>}

              <div className="field-row">
                <div className="field">
                  <label>Tipo *</label>
                  <select name="tipo" value={form.tipo} onChange={handleChange}>
                    {TIPOS.map((t) => <option key={t}>{t}</option>)}
                  </select>
                </div>
                <div className="field">
                  <label>Status *</label>
                  <select name="status" value={form.status} onChange={handleChange}>
                    {STATUS_OPTIONS.map((s) => <option key={s}>{s}</option>)}
                  </select>
                </div>
              </div>

              <div className="field-row">
                <div className="field">
                  <label>Período início *</label>
                  <input type="date" name="periodoInicio" required value={form.periodoInicio} onChange={handleChange} />
                </div>
                <div className="field">
                  <label>Período fim *</label>
                  <input type="date" name="periodoFim" required value={form.periodoFim} onChange={handleChange} />
                </div>
                <div className="field">
                  <label>Data vencimento *</label>
                  <input type="date" name="dataVencimento" required value={form.dataVencimento} onChange={handleChange} />
                </div>
              </div>

              <div className="field-row">
                <div className="field">
                  <label>Valor Total (R$) *</label>
                  <input type="number" name="valorTotal" required step="0.01" min="0" value={form.valorTotal} onChange={handleChange} placeholder="0,00" />
                </div>
                <div className="field">
                  <label>Valor Imposto (R$) *</label>
                  <input type="number" name="valorImposto" required step="0.01" min="0" value={form.valorImposto} onChange={handleChange} placeholder="0,00" />
                </div>
                <div className="field">
                  <label>Data pagamento</label>
                  <input type="date" name="dataPagamento" value={form.dataPagamento} onChange={handleChange} />
                </div>
              </div>

              <div className="field">
                <label>Observações</label>
                <textarea name="observacoes" rows={3} value={form.observacoes} onChange={handleChange} />
              </div>

              <div className="modal-footer">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
                <button type="submit" className="btn-primary" disabled={saving}>
                  {saving ? 'Salvando…' : editTarget ? 'Salvar' : 'Criar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function fmt(val: number) {
  return val.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}
