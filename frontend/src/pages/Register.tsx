import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Auth.css';
import { mensagemErro } from '../api/client';

export default function Register() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ nome: '', email: '', senha: '', cpf: '', telefone: '', nomeEscritorio: '', documentoEscritorio: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await signup(form);
      navigate('/login', { state: { registered: true } });
    } catch (err: unknown) {
      setError(mensagemErro(err, 'Erro ao cadastrar. Tente novamente.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="brand-icon-lg">⚡</span>
          <h1>FiscalHub</h1>
          <p>Crie sua conta</p>
        </div>

        <form onSubmit={handleSubmit} className="auth-form">
          <h2>Cadastro</h2>

          {error && <div className="alert alert-error">{error}</div>}

          <div className="field">
            <label htmlFor="nomeEscritorio">Nome do escritório</label>
            <input
              id="nomeEscritorio"
              name="nomeEscritorio"
              type="text"
              required
              value={form.nomeEscritorio}
              onChange={handleChange}
              placeholder="Silva Advocacia Tributária"
            />
          </div>

          <div className="field">
            <label htmlFor="documentoEscritorio">CNPJ ou CPF do escritório (opcional)</label>
            <input
              id="documentoEscritorio"
              name="documentoEscritorio"
              type="text"
              value={form.documentoEscritorio}
              onChange={handleChange}
              placeholder="00.000.000/0000-00"
            />
          </div>

          <div className="field">
            <label htmlFor="nome">Nome completo</label>
            <input
              id="nome"
              name="nome"
              type="text"
              required
              value={form.nome}
              onChange={handleChange}
              placeholder="João Silva"
            />
          </div>

          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              required
              value={form.email}
              onChange={handleChange}
              placeholder="seu@email.com"
            />
          </div>

          <div className="field">
            <label htmlFor="senha">Senha</label>
            <input
              id="senha"
              name="senha"
              type="password"
              autoComplete="new-password"
              required
              minLength={6}
              value={form.senha}
              onChange={handleChange}
              placeholder="Mínimo 6 caracteres"
            />
          </div>

          <div className="field-row">
            <div className="field">
              <label htmlFor="cpf">CPF</label>
              <input
                id="cpf"
                name="cpf"
                type="text"
                required
                maxLength={11}
                value={form.cpf}
                onChange={handleChange}
                placeholder="00000000000"
              />
            </div>
            <div className="field">
              <label htmlFor="telefone">Telefone</label>
              <input
                id="telefone"
                name="telefone"
                type="text"
                required
                value={form.telefone}
                onChange={handleChange}
                placeholder="11999999999"
              />
            </div>
          </div>

          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? 'Cadastrando…' : 'Criar conta'}
          </button>
        </form>

        <p className="auth-footer">
          Já tem uma conta?{' '}
          <Link to="/login">Entrar</Link>
        </p>
      </div>
    </div>
  );
}
