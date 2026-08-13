import './EmpresaArea.css';

export default function EmpresaDebitos() {
  return (
    <div>
      <div className="empresa-section">
        <h2>Débitos</h2>
        <p className="stub-empty">Nenhum débito registrado.</p>
        <p style={{ fontSize: 13, color: 'var(--text)', textAlign: 'center', marginTop: 8 }}>
          A integração de débitos com documentos e simulações de parcelamento estará disponível em breve.
        </p>
      </div>
    </div>
  );
}
