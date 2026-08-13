import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getMinhasEmpresas, createEmpresa, updateEmpresa, deleteEmpresa } from '../api/empresas';
import type { Empresa } from '../types';
import './Dashboard.css';
import './Empresas.css';

const ESTADOS_BR = [
  'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
  'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO',
];

const EMPTY_FORM = {
  razaoSocial: '', nomeFantasia: '', cnpj: '', inscricaoEstadual: '',
  inscricaoMunicipal: '', endereco: '', cidade: '', estado: 'SP',
  cep: '', telefone: '', ativa: true,
};

type FormState = typeof EMPTY_FORM;

export default function Empresas() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [empresas, setEmpresas] = useState<Empresa[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  async function fetchEmpresas() {
    if (!user) return;
    try {
      const data = await getMinhasEmpresas();
      setEmpresas(data);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { fetchEmpresas(); }, [user]);

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) {
    const { name, value, type } = e.target;
    const checked = type === 'checkbox' ? (e.target as HTMLInputElement).checked : undefined;

    if (name === 'cep') {
      const digits = value.replace(/\D/g, '').slice(0, 8);
      setForm((prev) => ({ ...prev, cep: digits }));
      return;
    }

    if (name === 'cnpj') {
      const digits = value.replace(/\D/g, '').slice(0, 14);
      setForm((prev) => ({ ...prev, cnpj: digits }));
      return;
    }

    setForm((prev) => ({ ...prev, [name]: type === 'checkbox' ? checked : value }));
  }

  function openCreate() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setError('');
    setShowModal(true);
  }

  function openEdit(emp: Empresa) {
    setEditingId(emp.id);
    setForm({
      razaoSocial: emp.razaoSocial ?? '',
      nomeFantasia: emp.nomeFantasia ?? '',
      cnpj: emp.cnpj ?? '',
      inscricaoEstadual: emp.inscricaoEstadual ?? '',
      inscricaoMunicipal: emp.inscricaoMunicipal ?? '',
      endereco: emp.endereco ?? '',
      cidade: emp.cidade ?? '',
      estado: emp.estado ?? 'SP',
      cep: emp.cep ?? '',
      telefone: emp.telefone ?? '',
      ativa: emp.ativa ?? true,
    });
    setError('');
    setShowModal(true);
  }

  function isCnpjValido(cnpj: string) {
    const digits = cnpj.replace(/\D/g, '');
    if (digits.length !== 14 || /^\d$/.test(digits)) return false;
    if (/^(\d)\1+$/.test(digits)) return false;

    const pesos1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    const pesos2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    let sum = 0;
    for (let i = 0; i < 12; i++) {
      sum += Number(digits[i]) * pesos1[i];
    }
    let d1 = sum % 11;
    d1 = d1 < 2 ? 0 : 11 - d1;
    if (Number(digits[12]) !== d1) return false;

    sum = 0;
    for (let i = 0; i < 13; i++) {
      sum += Number(digits[i]) * pesos2[i];
    }
    let d2 = sum % 11;
    d2 = d2 < 2 ? 0 : 11 - d2;
    return Number(digits[13]) === d2;
  }

  function validateForm() {
    const cep = form.cep.replace(/\D/g, '');
    if (cep && cep.length !== 8) {
      return 'CEP deve possuir exatamente 8 números.';
    }

    const cnpj = form.cnpj.replace(/\D/g, '');
    if (!cnpj) {
      return 'CNPJ é obrigatório.';
    }
    if (cnpj.length !== 14) {
      return 'CNPJ deve possuir 14 números.';
    }
    if (!isCnpjValido(cnpj)) {
      return 'CNPJ inválido.';
    }
    return '';
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!user) return;

    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    const payload = {
      ...form,
      cnpj: form.cnpj.replace(/\D/g, ''),
      cep: form.cep.replace(/\D/g, ''),
    };

    setError('');
    setSaving(true);
    try {
      if (editingId !== null) {
        await updateEmpresa(editingId, payload);
        setSuccessMsg('Empresa atualizada com sucesso!');
      } else {
        await createEmpresa(payload);
        setSuccessMsg('Empresa cadastrada com sucesso!');
      }
      setShowModal(false);
      setForm(EMPTY_FORM);
      setEditingId(null);
      await fetchEmpresas();
      setTimeout(() => setSuccessMsg(''), 3000);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erro ao salvar empresa.';
      setError(msg);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: number) {
    if (!confirm('Deseja remover esta empresa?')) return;
    try {
      await deleteEmpresa(id);
      setEmpresas((prev) => prev.filter((e) => e.id !== id));
    } catch {
      alert('Erro ao remover empresa.');
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Minhas Empresas</h1>
          <p className="page-subtitle">Gerencie as empresas vinculadas à sua conta.</p>
        </div>
        <button className="btn-primary" onClick={openCreate}>+ Nova Empresa</button>
      </div>

      {successMsg && <div className="alert alert-success">{successMsg}</div>}

      {loading ? (
        <div className="loading-state">Carregando empresas…</div>
      ) : empresas.length === 0 ? (
        <div className="empty-page">
          <span className="empty-icon">🏢</span>
          <p>Nenhuma empresa cadastrada ainda.</p>
          <button className="btn-primary" onClick={openCreate}>Cadastrar primeira empresa</button>
        </div>
      ) : (
        <div className="empresa-cards">
          {empresas.map((emp) => (
            <div key={emp.id} className="empresa-card">
              <div className="empresa-card-info">
                <strong className="empresa-card-nome">{emp.razaoSocial}</strong>
                {emp.nomeFantasia && (
                  <span className="empresa-card-fantasia">{emp.nomeFantasia}</span>
                )}
                <span className="empresa-card-cnpj mono">CNPJ: {formatCnpj(emp.cnpj)}</span>
                {emp.cidade && emp.estado && (
                  <span className="empresa-card-local">{emp.cidade} / {emp.estado}</span>
                )}
              </div>
              <div className="empresa-card-actions">
                <span className={`badge ${emp.ativa ? 'badge-green' : 'badge-gray'}`}>
                  {emp.ativa ? 'Ativa' : 'Inativa'}
                </span>
                <button
                  className="btn-primary btn-sm"
                  onClick={() => navigate(`/empresas/${emp.id}`)}
                >
                  Acessar Empresa
                </button>
                <button
                  className="btn-secondary btn-sm"
                  onClick={() => openEdit(emp)}
                >
                  Editar
                </button>
                <button
                  className="btn-icon btn-danger"
                  onClick={() => handleDelete(emp.id)}
                  title="Remover"
                >
                  🗑
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{editingId !== null ? 'Editar Empresa' : 'Nova Empresa'}</h2>
              <button className="modal-close" onClick={() => setShowModal(false)}>✕</button>
            </div>

            <form onSubmit={handleSubmit} className="modal-body">
              {error && <div className="alert alert-error">{error}</div>}

              <div className="field-row">
                <div className="field">
                  <label>Razão Social *</label>
                  <input name="razaoSocial" required value={form.razaoSocial} onChange={handleChange} />
                </div>
                <div className="field">
                  <label>Nome Fantasia</label>
                  <input name="nomeFantasia" value={form.nomeFantasia} onChange={handleChange} />
                </div>
              </div>

              <div className="field-row">
                <div className="field">
                  <label>CNPJ *</label>
                  <input
                    name="cnpj"
                    required
                    maxLength={14}
                    value={form.cnpj}
                    onChange={handleChange}
                    placeholder="00000000000000"
                    aria-invalid={form.cnpj && !isCnpjValido(form.cnpj) ? 'true' : 'false'}
                    style={{
                      borderColor: form.cnpj && !isCnpjValido(form.cnpj) ? '#dc2626' : undefined,
                      boxShadow: form.cnpj && !isCnpjValido(form.cnpj) ? '0 0 0 1px #dc2626' : undefined,
                    }}
                  />
                </div>
                <div className="field">
                  <label>Telefone</label>
                  <input name="telefone" value={form.telefone} onChange={handleChange} />
                </div>
              </div>

              <div className="field-row">
                <div className="field">
                  <label>Inscrição Estadual</label>
                  <input name="inscricaoEstadual" value={form.inscricaoEstadual} onChange={handleChange} />
                </div>
                <div className="field">
                  <label>Inscrição Municipal</label>
                  <input name="inscricaoMunicipal" value={form.inscricaoMunicipal} onChange={handleChange} />
                </div>
              </div>

              <div className="field">
                <label>Endereço</label>
                <input name="endereco" value={form.endereco} onChange={handleChange} />
              </div>

              <div className="field-row">
                <div className="field">
                  <label>Cidade</label>
                  <input name="cidade" value={form.cidade} onChange={handleChange} />
                </div>
                <div className="field field--sm">
                  <label>Estado</label>
                  <select name="estado" value={form.estado} onChange={handleChange}>
                    {ESTADOS_BR.map((uf) => <option key={uf}>{uf}</option>)}
                  </select>
                </div>
                <div className="field field--sm">
                  <label>CEP</label>
                  <input
                    name="cep"
                    maxLength={8}
                    value={form.cep}
                    onChange={handleChange}
                    placeholder="00000000"
                    style={{
                      borderColor: form.cep && form.cep.length !== 8 ? '#dc2626' : undefined,
                      boxShadow: form.cep && form.cep.length !== 8 ? '0 0 0 1px #dc2626' : undefined,
                    }}
                  />
                </div>
              </div>

              <label className="checkbox-label">
                <input type="checkbox" name="ativa" checked={form.ativa} onChange={handleChange} />
                Empresa ativa
              </label>

              <div className="modal-footer">
                <button type="button" className="btn-secondary" onClick={() => setShowModal(false)}>Cancelar</button>
                <button type="submit" className="btn-primary" disabled={saving}>
                  {saving ? 'Salvando…' : editingId !== null ? 'Salvar Alterações' : 'Cadastrar'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

function formatCnpj(cnpj: string) {
  const d = cnpj.replace(/\D/g, '');
  if (d.length !== 14) return cnpj;
  return `${d.slice(0,2)}.${d.slice(2,5)}.${d.slice(5,8)}/${d.slice(8,12)}-${d.slice(12)}`;
}
