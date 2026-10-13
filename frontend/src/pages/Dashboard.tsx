import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Placeholder: a casca nova da aplicação vem na Fase F. */
export default function Dashboard() {
  const { user } = useAuth();
  return (
    <div className="page">
      <h1>Olá, {user?.nome}</h1>
      <p>Os dados fiscais entram por importação de arquivos. Comece pelos clientes do escritório.</p>
      <Link to="/empresas" className="btn-primary">Ver clientes</Link>
    </div>
  );
}
