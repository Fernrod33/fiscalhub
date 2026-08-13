import { useState, type FormEvent } from 'react';
import { simularParcelamento } from '../api/simulacoes';
import type { SimulacaoResultado } from '../types';
import './Dashboard.css';
import './Simulacao.css';

export default function Simulacao() {
  const [form, setForm] = useState({
    valorDevido: '',
    numeroParcelas: '12',
    taxaJurosMensal: '1.00',
    dataInicio: new Date().toISOString().split('T')[0],
  });
  const [resultado, setResultado] = useState<SimulacaoResultado | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    setResultado(null);
    try {
      const res = await simularParcelamento({
        valorDevido: parseFloat(form.valorDevido),
        numeroParcelas: parseInt(form.numeroParcelas),
        taxaJurosMensal: parseFloat(form.taxaJurosMensal),
        dataInicio: form.dataInicio,
      });
      setResultado(res);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erro ao realizar simulação. Verifique se o servidor está online.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  function handleClear() {
    setResultado(null);
    setError('');
    setForm({
      valorDevido: '',
      numeroParcelas: '12',
      taxaJurosMensal: '1.00',
      dataInicio: new Date().toISOString().split('T')[0],
    });
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Simulação de Parcelamento</h1>
          <p className="page-subtitle">Calcule parcelas de débitos fiscais com juros.</p>
        </div>
      </div>

      <div className="sim-layout">
        <div className="section-card sim-form-card">
          <h2>Parâmetros</h2>
          <form onSubmit={handleSubmit} className="sim-form">
            <div className="field">
              <label htmlFor="valorDevido">Valor do débito (R$) *</label>
              <input
                id="valorDevido"
                name="valorDevido"
                type="number"
                required
                min="1"
                step="0.01"
                value={form.valorDevido}
                onChange={handleChange}
                placeholder="Ex: 10000.00"
              />
            </div>

            <div className="field">
              <label htmlFor="numeroParcelas">Número de parcelas *</label>
              <select id="numeroParcelas" name="numeroParcelas" value={form.numeroParcelas} onChange={handleChange}>
                {[2,3,4,5,6,8,10,12,18,24,36,48,60].map((n) => (
                  <option key={n} value={n}>{n}x</option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="taxaJurosMensal">Taxa de juros mensal (%) *</label>
              <input
                id="taxaJurosMensal"
                name="taxaJurosMensal"
                type="number"
                required
                min="0"
                step="0.01"
                value={form.taxaJurosMensal}
                onChange={handleChange}
                placeholder="Ex: 1.00"
              />
            </div>

            <div className="field">
              <label htmlFor="dataInicio">Data de início *</label>
              <input
                id="dataInicio"
                name="dataInicio"
                type="date"
                required
                value={form.dataInicio}
                onChange={handleChange}
              />
            </div>

            {error && <div className="alert alert-error">{error}</div>}

            <div className="sim-actions">
              <button type="submit" className="btn-primary" disabled={loading}>
                {loading ? 'Calculando…' : '🧮 Calcular'}
              </button>
              {resultado && (
                <button type="button" className="btn-secondary" onClick={handleClear}>
                  Limpar
                </button>
              )}
            </div>
          </form>
        </div>

        {resultado && (
          <div className="sim-result">
            <div className="section-card">
              <h2>Resumo</h2>
              <div className="summary-grid">
                <SummaryItem label="Valor original" value={fmt(resultado.valorOriginal)} />
                <SummaryItem label="Total de juros" value={fmt(resultado.valorTotalJuros)} highlight="red" />
                <SummaryItem label="Total a pagar" value={fmt(resultado.valorTotalComJuros)} highlight="purple" />
                <SummaryItem label="Parcelas" value={`${resultado.numeroParcelas}x`} />
                <SummaryItem label="Taxa mensal" value={`${resultado.taxaJurosMensal}%`} />
                <SummaryItem
                  label="Valor da parcela"
                  value={fmt(resultado.valorTotalComJuros / resultado.numeroParcelas)}
                />
              </div>
            </div>

            <div className="section-card">
              <h2>Tabela de parcelas</h2>
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Vencimento</th>
                      <th>Principal</th>
                      <th>Juros</th>
                      <th>Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resultado.parcelas.map((p) => (
                      <tr key={p.numero}>
                        <td>{p.numero}</td>
                        <td className="mono">{p.dataVencimento}</td>
                        <td>{fmt(p.valorPrincipal)}</td>
                        <td className="text-red">{fmt(p.valorJuros)}</td>
                        <td><strong>{fmt(p.valorTotal)}</strong></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function SummaryItem({ label, value, highlight }: { label: string; value: string; highlight?: 'red' | 'purple' }) {
  return (
    <div className="summary-item">
      <span className="summary-label">{label}</span>
      <span className={`summary-value${highlight ? ` summary-value--${highlight}` : ''}`}>{value}</span>
    </div>
  );
}

function fmt(val: number) {
  return val.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}
