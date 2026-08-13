import './EmpresaArea.css';

export default function EmpresaDocumentosContabeis() {
  function handleImportar() {
    alert('Importação de documentos contábeis estará disponível em breve.');
  }

  return (
    <div>
      <div className="empresa-section">
        <h2>Documentos Contábeis</h2>
        <p style={{ fontSize: 14, color: 'var(--text)', marginBottom: 16 }}>
          Documentos contábeis da empresa.
        </p>
        <button className="btn-secondary btn-stub" onClick={handleImportar}>
          Importar documento
        </button>
        <p className="stub-empty">Nenhum documento contábil importado.</p>
        <div className="stub-future">
          <span>Documentos previstos:</span>
          <ul>
            <li>ECD</li>
            <li>Balanço Patrimonial</li>
            <li>DRE</li>
            <li>Balancete</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
