import './EmpresaArea.css';

export default function EmpresaDocumentosFiscais() {
  function handleImportar() {
    alert('Importação de documentos fiscais estará disponível em breve.');
  }

  return (
    <div>
      <div className="empresa-section">
        <h2>Documentos Fiscais</h2>
        <p style={{ fontSize: 14, color: 'var(--text)', marginBottom: 16 }}>
          Documentos fiscais da empresa.
        </p>
        <button className="btn-secondary btn-stub" onClick={handleImportar}>
          Importar documento
        </button>
        <p className="stub-empty">Nenhum documento fiscal importado.</p>
        <div className="stub-future">
          <span>Futuramente serão suportados documentos como:</span>
          <ul>
            <li>DCTFWeb / MIT</li>
            <li>ECF</li>
            <li>EFD-Contribuições</li>
            <li>PGDAS-D</li>
            <li>Documentos da Receita Estadual do Paraná</li>
            <li>Outros documentos fiscais</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
