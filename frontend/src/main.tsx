import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/globals.css'
import { aplicarTokensDeMovimento } from './styles/motion'
import { carregarSessao } from '@/features/auth/sessao'
import App from './app/app'

aplicarTokensDeMovimento()
await carregarSessao() // os guards de rota já nascem sabendo se há sessão

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
