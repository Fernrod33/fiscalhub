import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/globals.css'
import { aplicarTokensDeMovimento } from './styles/motion'
import App from './App.tsx'

aplicarTokensDeMovimento()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
