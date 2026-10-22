import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.jsx'
import { AuthProvider } from './auth/AuthContext.jsx'
import { APP_VERSION } from './version'

// Versão também no título da aba: aparece em qualquer tela (inclusive o login) e em
// capturas de tela que chegam pro suporte.
document.title = `CambIA · v${APP_VERSION}`

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
