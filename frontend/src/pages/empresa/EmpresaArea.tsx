import { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom';
import { getCliente } from '../../api/clientes';
import type { Cliente } from '../../types';
import '../Dashboard.css';
import './EmpresaArea.css';

const TABS = [
  { path: 'visao-geral', label: 'Visão Geral' },
  { path: 'dados', label: 'Dados Cadastrais' },
  { path: 'documentos-fiscais', label: 'Documentos Fiscais' },
  { path: 'documentos-contabeis', label: 'Documentos Contábeis' },
  { path: 'debitos', label: 'Débitos' },
  { path: 'simulacoes', label: 'Simulações' },
];

export type EmpresaAreaOutletContext = { empresa: Cliente; reload: () => void };

export default function EmpresaArea() {
  const { empresaId } = useParams<{ empresaId: string }>();
  const navigate = useNavigate();
  const [empresa, setEmpresa] = useState<Cliente | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  async function load() {
    if (!empresaId) return;
    setLoading(true);
    setLoadError('');
    try {
      const data = await getCliente(Number(empresaId));
      setEmpresa(data);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 404) {
        setLoadError('Cliente não encontrada.');
      } else if (status === 403) {
        setLoadError('Você não tem permissão para acessar esta empresa.');
      } else {
        setLoadError('Erro ao carregar os dados da empresa.');
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [empresaId]);

  if (loading) return <div className="page loading-state">Carregando empresa…</div>;

  if (loadError) {
    return (
      <div className="page">
        <div className="empresa-error">{loadError}</div>
        <div style={{ textAlign: 'center' }}>
          <button className="btn-secondary" onClick={() => navigate('/empresas')}>
            ← Minhas Empresas
          </button>
        </div>
      </div>
    );
  }

  if (!empresa) return null;

  return (
    <div className="page empresa-area">
      <div className="empresa-header">
        <div className="empresa-header-info">
          <h1 className="empresa-header-nome">{empresa.razaoSocial}</h1>
          <span className="empresa-header-cnpj mono">CNPJ: {formatCnpj(empresa.cnpj)}</span>
        </div>
        <button className="btn-secondary btn-sm" onClick={() => navigate('/empresas')}>
          ← Minhas Empresas
        </button>
      </div>

      <nav className="empresa-tabs">
        {TABS.map(({ path, label }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) => `empresa-tab${isActive ? ' active' : ''}`}
          >
            {label}
          </NavLink>
        ))}
      </nav>

      <Outlet context={{ empresa, reload: load } satisfies EmpresaAreaOutletContext} />
    </div>
  );
}

function formatCnpj(cnpj: string) {
  const d = cnpj.replace(/\D/g, '');
  if (d.length !== 14) return cnpj;
  return `${d.slice(0,2)}.${d.slice(2,5)}.${d.slice(5,8)}/${d.slice(8,12)}-${d.slice(12)}`;
}
