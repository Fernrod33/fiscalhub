import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Layout.css';

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: '▦' },
  { to: '/empresas', label: 'Clientes', icon: '🏢' },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-icon">⚡</span>
          <span className="brand-name">FiscalHub</span>
        </div>

        <nav className="sidebar-nav">
          {NAV_ITEMS.map(({ to, label, icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
              <span className="nav-icon">{icon}</span>
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-info">
            <div className="user-avatar">{user?.nome?.[0]?.toUpperCase() ?? 'U'}</div>
            <div className="user-details">
              <span className="user-name">{user?.nome}</span>
              <span className="user-email">{user?.email}</span>
            </div>
          </div>
          <button className="logout-btn" onClick={handleLogout} title="Sair">
            ⏻
          </button>
        </div>
      </aside>

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
