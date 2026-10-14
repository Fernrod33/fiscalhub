import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/globals.css'
import { aplicarTokensDeMovimento } from './styles/motion'
import '@/features/auth/sessao' // liga o cliente HTTP à sessão antes do primeiro render
import App from './app/app'

aplicarTokensDeMovimento()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
