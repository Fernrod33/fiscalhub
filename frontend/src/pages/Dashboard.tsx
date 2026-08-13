import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getMinhasEmpresas } from '../api/empresas';
import { getDeclaracoesByEmpresa } from '../api/declaracoes';
import type { Empresa, Declaracao } from '../types';
import './Dashboard.css';

interface Stats {
  totalEmpresas: number;
  totalDeclaracoes: number;
  declaracoesPendentes: number;
  valorTotalImposto: number;
}

export default function Dashboard() {
  const { user } = useAuth();
  const [stats, setStats] = useState<Stats>({
    totalEmpresas: 0,
    totalDeclaracoes: 0,
    declaracoesPendentes: 0,
    valorTotalImposto: 0,
  });
  const [recentDeclaracoes, setRecentDeclaracoes] = useState<Declaracao[]>([]);
  const [empresas, setEmpresas] = useState<Empresa[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) return;

    async function fetchData() {
      try {
        const emps = await getMinhasEmpresas();
        setEmpresas(emps);

        const allDeclaracoes: Declaracao[] = [];
        await Promise.all(
          emps.map(async (emp) => {
            try {
              const decls = await getDeclaracoesByEmpresa(emp.id);
              allDeclaracoes.push(...decls);
            } catch {
              // empresa pode não ter declarações
            }
          })
        );

        const pendentes = allDeclaracoes.filter((d) => d.status === 'PENDENTE').length;
        const totalImposto = allDeclaracoes.reduce((sum, d) => sum + (d.valorImposto ?? 0), 0);

        setStats({
          totalEmpresas: emps.length,
          totalDeclaracoes: allDeclaracoes.length,
          declaracoesPendentes: pendentes,
          valorTotalImposto: totalImposto,
        });

        const sorted = [...allDeclaracoes].sort(
          (a, b) => new Date(b.dataCriacao).getTime() - new Date(a.dataCriacao).getTime()
        );
        setRecentDeclaracoes(sorted.slice(0, 5));
      } catch {
        // silently fail – backend may be offline
      } finally {
        setLoading(false);
      }
    }

    fetchData();
  }, [user]);

  const cards = [
    { label: 'Empresas', value: stats.totalEmpresas, icon: '🏢', color: 'blue', to: '/empresas' },
    { label: 'Declarações', value: stats.totalDeclaracoes, icon: '📋', color: 'green', to: '/declaracoes' },
    { label: 'Pendentes', value: stats.declaracoesPendentes, icon: '⏳', color: 'yellow', to: '/declaracoes' },
    {
      label: 'Total em Impostos',
      value: stats.valorTotalImposto.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
      icon: '💰',
      color: 'purple',
      to: '/declaracoes',
    },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="page-subtitle">
            Bem-vindo, <strong>{user?.nome}</strong>! Aqui está um resumo da sua situação fiscal.
          </p>
        </div>
        <Link to="/empresas" className="btn-primary">
          + Nova Empresa
        </Link>
      </div>

      {loading ? (
        <div className="loading-state">Carregando dados…</div>
      ) : (
        <>
          <div className="stats-grid">
            {cards.map((card) => (
              <Link key={card.label} to={card.to} className={`stat-card stat-card--${card.color}`}>
                <div className="stat-icon">{card.icon}</div>
                <div className="stat-body">
                  <span className="stat-value">{card.value}</span>
                  <span className="stat-label">{card.label}</span>
                </div>
              </Link>
            ))}
          </div>

          <div className="dashboard-sections">
            <section className="section-card">
              <div className="section-header">
                <h2>Empresas cadastradas</h2>
                <Link to="/empresas" className="link-sm">Ver todas →</Link>
              </div>
              {empresas.length === 0 ? (
                <div className="empty-state">
                  <p>Nenhuma empresa cadastrada.</p>
                  <Link to="/empresas" className="btn-primary">Cadastrar empresa</Link>
                </div>
              ) : (
                <ul className="item-list">
                  {empresas.slice(0, 4).map((emp) => (
                    <li key={emp.id} className="item-row">
                      <div className="item-icon">🏢</div>
                      <div className="item-info">
                        <span className="item-title">{emp.razaoSocial}</span>
                        <span className="item-sub">CNPJ: {emp.cnpj} · {emp.cidade}/{emp.estado}</span>
                      </div>
                      <span className={`badge ${emp.ativa ? 'badge-green' : 'badge-gray'}`}>
                        {emp.ativa ? 'Ativa' : 'Inativa'}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section className="section-card">
              <div className="section-header">
                <h2>Declarações recentes</h2>
                <Link to="/declaracoes" className="link-sm">Ver todas →</Link>
              </div>
              {recentDeclaracoes.length === 0 ? (
                <div className="empty-state">
                  <p>Nenhuma declaração encontrada.</p>
                  <Link to="/declaracoes" className="btn-primary">Adicionar declaração</Link>
                </div>
              ) : (
                <ul className="item-list">
                  {recentDeclaracoes.map((d) => (
                    <li key={d.id} className="item-row">
                      <div className="item-icon">📋</div>
                      <div className="item-info">
                        <span className="item-title">{d.tipo}</span>
                        <span className="item-sub">
                          Venc: {d.dataVencimento} · {d.valorImposto.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                        </span>
                      </div>
                      <StatusBadge status={d.status} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, string> = {
    PENDENTE: 'badge-yellow',
    ENVIADA: 'badge-blue',
    PROCESSANDO: 'badge-blue',
    APROVADA: 'badge-green',
    PAGO: 'badge-green',
    REJEITADA: 'badge-red',
  };
  return <span className={`badge ${map[status] ?? 'badge-gray'}`}>{status}</span>;
}
