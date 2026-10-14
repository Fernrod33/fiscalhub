import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { Providers } from './app/providers';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Empresas from './pages/Empresas';
import EmpresaArea from './pages/empresa/EmpresaArea';
import EmpresaVisaoGeral from './pages/empresa/EmpresaVisaoGeral';
import EmpresaDados from './pages/empresa/EmpresaDados';
import EmpresaDocumentosFiscais from './pages/empresa/EmpresaDocumentosFiscais';
import EmpresaDocumentosContabeis from './pages/empresa/EmpresaDocumentosContabeis';
import EmpresaDebitos from './pages/empresa/EmpresaDebitos';
import EmpresaSimulacoes from './pages/empresa/EmpresaSimulacoes';

// Só em desenvolvimento: em produção o import.meta.env.DEV é false e o chunk do laboratório nem é gerado.
const LaboratorioDesign = import.meta.env.DEV ? lazy(() => import('./laboratorio/LaboratorioDesign')) : null;

export default function App() {
  return (
    <Providers>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          {LaboratorioDesign && (
            <Route path="/laboratorio-design" element={<Suspense><LaboratorioDesign /></Suspense>} />
          )}
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/empresas" element={<Empresas />} />
              <Route path="/empresas/:empresaId" element={<EmpresaArea />}>
                <Route index element={<Navigate to="visao-geral" replace />} />
                <Route path="visao-geral" element={<EmpresaVisaoGeral />} />
                <Route path="dados" element={<EmpresaDados />} />
                <Route path="documentos-fiscais" element={<EmpresaDocumentosFiscais />} />
                <Route path="documentos-contabeis" element={<EmpresaDocumentosContabeis />} />
                <Route path="debitos" element={<EmpresaDebitos />} />
                <Route path="simulacoes" element={<EmpresaSimulacoes />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
    </Providers>
  );
}
