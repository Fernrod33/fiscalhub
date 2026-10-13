import { useNavigate, useOutletContext } from 'react-router-dom';
import type { EmpresaAreaOutletContext } from './EmpresaArea';
import './EmpresaArea.css';

export default function EmpresaVisaoGeral() {
  const { empresa } = useOutletContext<EmpresaAreaOutletContext>();
  const navigate = useNavigate();
  const base = `/empresas/${empresa.id}`;

  return (
    <div>
      <div className="empresa-section">
        <h2>Dados da Cliente</h2>
        <div className="info-grid">
          <div className="info-field">
            <label>Razão Social</label>
            <span>{empresa.razaoSocial}</span>
          </div>
          {empresa.nomeFantasia && (
            <div className="info-field">
              <label>Nome Fantasia</label>
              <span>{empresa.nomeFantasia}</span>
            </div>
          )}
          <div className="info-field">
            <label>CNPJ</label>
            <span className="mono">{formatCnpj(empresa.cnpj)}</span>
          </div>
          <div className="info-field">
            <label>Situação</label>
            <span className={`badge ${empresa.ativa ? 'badge-green' : 'badge-gray'}`}>
              {empresa.ativa ? 'Ativa' : 'Inativa'}
            </span>
          </div>
          {empresa.cidade && (
            <div className="info-field">
              <label>Cidade / Estado</label>
              <span>{empresa.cidade} / {empresa.estado}</span>
            </div>
          )}
        </div>
      </div>

      <div className="empresa-section">
        <h2>Documentos Fiscais</h2>
        <p className="stub-empty">Nenhum documento fiscal importado.</p>
        <div style={{ textAlign: 'center' }}>
          <button className="btn-secondary btn-sm" onClick={() => navigate(`${base}/documentos-fiscais`)}>
            Ver documentos
          </button>
        </div>
      </div>

      <div className="empresa-section">
        <h2>Documentos Contábeis</h2>
        <p className="stub-empty">Nenhum documento contábil importado.</p>
        <div style={{ textAlign: 'center' }}>
          <button className="btn-secondary btn-sm" onClick={() => navigate(`${base}/documentos-contabeis`)}>
            Ver documentos
          </button>
        </div>
      </div>

      <div className="empresa-section">
        <h2>Débitos</h2>
        <p className="stub-empty">Nenhum débito registrado.</p>
        <div style={{ textAlign: 'center' }}>
          <button className="btn-secondary btn-sm" onClick={() => navigate(`${base}/debitos`)}>
            Ver débitos
          </button>
        </div>
      </div>

      <div className="empresa-section">
        <h2>Simulações</h2>
        <p className="stub-empty">Nenhuma simulação realizada.</p>
        <div style={{ textAlign: 'center' }}>
          <button className="btn-secondary btn-sm" onClick={() => navigate(`${base}/simulacoes`)}>
            Ver simulações
          </button>
        </div>
      </div>
    </div>
  );
}

function formatCnpj(cnpj: string) {
  const d = cnpj.replace(/\D/g, '');
  if (d.length !== 14) return cnpj;
  return `${d.slice(0,2)}.${d.slice(2,5)}.${d.slice(5,8)}/${d.slice(8,12)}-${d.slice(12)}`;
}
