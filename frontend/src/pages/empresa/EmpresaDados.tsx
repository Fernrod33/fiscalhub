import { useState, type FormEvent } from 'react';
import { useOutletContext } from 'react-router-dom';
import { updateCliente } from '../../api/clientes';
import type { EmpresaAreaOutletContext } from './EmpresaArea';
import './EmpresaArea.css';
import { mensagemErro } from '../../api/client';

const ESTADOS_BR = [
  'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG',
  'PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO',
];

export default function EmpresaDados() {
  const { empresa, reload } = useOutletContext<EmpresaAreaOutletContext>();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({ ...empresa });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

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

  function startEdit() {
    setForm({ ...empresa });
    setError('');
    setSuccess('');
    setEditing(true);
  }

  function cancelEdit() {
    setEditing(false);
    setError('');
  }

  function isCnpjValido(cnpj: string) {
    const digits = cnpj.replace(/\D/g, '');
    if (digits.length !== 14 || /^(\d)\1+$/.test(digits)) return false;

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
    const cep = form.cep ? String(form.cep).replace(/\D/g, '') : '';
    if (cep && cep.length !== 8) {
      return 'CEP deve possuir exatamente 8 números.';
    }

    const cnpj = form.cnpj ? String(form.cnpj).replace(/\D/g, '') : '';
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
    const validationError = validateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSaving(true);
    setError('');
    try {
      const payload = {
        ...form,
        cnpj: String(form.cnpj ?? '').replace(/\D/g, ''),
        cep: String(form.cep ?? '').replace(/\D/g, ''),
      };
      await updateCliente(empresa.id, payload);
      setSuccess('Dados atualizados com sucesso!');
      setEditing(false);
      reload();
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: unknown) {
      setError(mensagemErro(err, 'Erro ao atualizar os dados.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      {success && <div className="alert alert-success" style={{ marginBottom: 16 }}>{success}</div>}

      <div className="empresa-section">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h2 style={{ margin: 0, border: 'none', padding: 0 }}>Identificação</h2>
          {!editing && (
            <button className="btn-secondary btn-sm" onClick={startEdit}>Editar</button>
          )}
        </div>

        {!editing ? (
          <div className="info-grid">
            <div className="info-field">
              <label>Razão Social</label>
              <span>{empresa.razaoSocial}</span>
            </div>
            <div className="info-field">
              <label>Nome Fantasia</label>
              <span>{empresa.nomeFantasia || '—'}</span>
            </div>
            <div className="info-field">
              <label>CNPJ</label>
              <span className="mono">{formatCnpj(empresa.cnpj)}</span>
            </div>
            <div className="info-field">
              <label>Inscrição Estadual</label>
              <span>{empresa.inscricaoEstadual || '—'}</span>
            </div>
            <div className="info-field">
              <label>Inscrição Municipal</label>
              <span>{empresa.inscricaoMunicipal || '—'}</span>
            </div>
            <div className="info-field">
              <label>Telefone</label>
              <span>{empresa.telefone || '—'}</span>
            </div>
            <div className="info-field">
              <label>Situação</label>
              <span className={`badge ${empresa.ativa ? 'badge-green' : 'badge-gray'}`}>
                {empresa.ativa ? 'Ativa' : 'Inativa'}
              </span>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            {error && <div className="alert alert-error" style={{ marginBottom: 12 }}>{error}</div>}
            <div className="field-row">
              <div className="field">
                <label>Razão Social *</label>
                <input name="razaoSocial" required value={form.razaoSocial} onChange={handleChange} />
              </div>
              <div className="field">
                <label>Nome Fantasia</label>
                <input name="nomeFantasia" value={form.nomeFantasia ?? ''} onChange={handleChange} />
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
                  aria-invalid={form.cnpj && !isCnpjValido(String(form.cnpj)) ? 'true' : 'false'}
                  style={{
                    borderColor: form.cnpj && !isCnpjValido(String(form.cnpj)) ? '#dc2626' : undefined,
                    boxShadow: form.cnpj && !isCnpjValido(String(form.cnpj)) ? '0 0 0 1px #dc2626' : undefined,
                  }}
                />
              </div>
              <div className="field">
                <label>Telefone</label>
                <input name="telefone" value={form.telefone ?? ''} onChange={handleChange} />
              </div>
            </div>
            <div className="field-row">
              <div className="field">
                <label>Inscrição Estadual</label>
                <input name="inscricaoEstadual" value={form.inscricaoEstadual ?? ''} onChange={handleChange} />
              </div>
              <div className="field">
                <label>Inscrição Municipal</label>
                <input name="inscricaoMunicipal" value={form.inscricaoMunicipal ?? ''} onChange={handleChange} />
              </div>
            </div>
            <label className="checkbox-label" style={{ marginBottom: 16 }}>
              <input type="checkbox" name="ativa" checked={form.ativa ?? true} onChange={handleChange} />
              Cliente ativa
            </label>
            <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
              <button type="button" className="btn-secondary" onClick={cancelEdit}>Cancelar</button>
              <button type="submit" className="btn-primary" disabled={saving}>
                {saving ? 'Salvando…' : 'Salvar Alterações'}
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="empresa-section">
        <h2>Endereço</h2>
        {!editing ? (
          <div className="info-grid">
            <div className="info-field">
              <label>Endereço</label>
              <span>{empresa.endereco || '—'}</span>
            </div>
            <div className="info-field">
              <label>Cidade</label>
              <span>{empresa.cidade || '—'}</span>
            </div>
            <div className="info-field">
              <label>Estado</label>
              <span>{empresa.estado || '—'}</span>
            </div>
            <div className="info-field">
              <label>CEP</label>
              <span className="mono">{empresa.cep || '—'}</span>
            </div>
          </div>
        ) : (
          <div className="field-row">
            <div className="field" style={{ flex: 2 }}>
              <label>Endereço</label>
              <input name="endereco" value={form.endereco ?? ''} onChange={handleChange} />
            </div>
            <div className="field">
              <label>Cidade</label>
              <input name="cidade" value={form.cidade ?? ''} onChange={handleChange} />
            </div>
            <div className="field field--sm">
              <label>Estado</label>
              <select name="estado" value={form.estado ?? 'SP'} onChange={handleChange}>
                {ESTADOS_BR.map((uf) => <option key={uf}>{uf}</option>)}
              </select>
            </div>
            <div className="field field--sm">
              <label>CEP</label>
              <input
                name="cep"
                maxLength={8}
                value={form.cep ?? ''}
                onChange={handleChange}
                style={{
                  borderColor: form.cep && String(form.cep).length !== 8 ? '#dc2626' : undefined,
                  boxShadow: form.cep && String(form.cep).length !== 8 ? '0 0 0 1px #dc2626' : undefined,
                }}
              />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function formatCnpj(cnpj: string) {
  const d = cnpj.replace(/\D/g, '');
  if (d.length !== 14) return cnpj;
  return `${d.slice(0,2)}.${d.slice(2,5)}.${d.slice(5,8)}/${d.slice(8,12)}-${d.slice(12)}`;
}
