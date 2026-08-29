# Incremento 8 — Front-end: login + CRUD (Cliente, Banco, Usuário)

Status: ✅ concluído

## Decisões tomadas nesta etapa
- Front-end em **React** (Vite), projeto separado em `frontend/`, consumindo a API REST do backend.
- Escopo inicial: **login (link mágico) + CRUD de Cliente, Banco e Usuário**. A tela de Operação fica para depois (ainda bloqueada pela imagem de referência e pelos 13 campos novos).
- Autenticação no navegador: `sessionToken` guardado no `localStorage`, enviado em todo request via header `Authorization: Bearer <token>`. Sem isso, nenhuma tela conseguiria autenticar — foi construído junto com o CRUD, não como algo à parte.
- Navegação (`Clientes`, `Bancos`, `Usuários`) só aparece para quem está logado como `ADMIN`, refletindo a regra já existente no backend.

## O que foi feito

- Projeto Vite + React em `frontend/`, com `react-router-dom` para as rotas.
- `src/api/client.js` — cliente HTTP fino sobre `fetch`, injeta o token, trata `401` (limpa sessão e redireciona para `/login`) e erros de validação do backend.
- `src/auth/` — contexto de autenticação (`AuthContext`), tela de login (`LoginPage`) e guarda de rota (`RequireAuth`).
- `src/pages/` — `ClientesPage`, `BancosPage`, `UsuariosPage`, cada uma com listagem, criação, edição e remoção, consumindo os endpoints já existentes.
- Backend: adicionado **CORS** (`SecurityConfig`) liberando `http://localhost:5173` (servidor de desenvolvimento do Vite) para os métodos e headers usados pela API.

## Login sem e-mail real (ainda)
Como o envio de e-mail (SMTP) continua pendente (ver [pendencias.md](pendencias.md)), a tela de login tem uma segunda etapa onde o token é colado manualmente — com um aviso explícito de que isso é temporário, só para desenvolvimento. Quando o SMTP for configurado, essa etapa deixa de ser necessária (o usuário só clica no link recebido por e-mail).

## Como foi validado
Como é front-end, "TDD" aqui significou: montar a tela, rodar de verdade num navegador (não só ler o código), e corrigir o que quebrar. Usei um driver Playwright para automatizar essa verificação, já que não há um navegador interativo neste ambiente:

1. Subi backend (`spring-boot:test-run`) e frontend (`npm run dev`) reais, não mocks.
2. **Bug real encontrado e corrigido**: `POST /auth/magic-link` retorna `202` sem corpo, mas o cliente de API tentava sempre fazer `.json()` na resposta — quebrava com "Unexpected end of JSON input". Corrigido para só fazer parse se houver corpo de fato.
3. Depois da correção, testei de ponta a ponta num Chromium real: login completo (solicitar link → pegar token do log do backend → entrar), criar Cliente/Banco/Usuário, editar Cliente, remover Cliente — tudo funcionou, sem erros no console do navegador.
4. Corrigido um pequeno problema visual (rótulo "E-mail" colado no campo no formulário de login).

## Como testar você mesmo
```
# Terminal 1
./mvnw spring-boot:test-run

# Terminal 2
cd frontend
npm install
npm run dev
```
Acesse `http://localhost:5173`. Crie o primeiro admin via `curl` (ver [incremento-4.md](incremento-4.md)) antes de logar pela tela.

## O que NÃO está incluído
- Tela de Operação — continua bloqueada pelas mesmas pendências de sempre (imagem de referência, 13 campos novos).
- Envio real de e-mail — a etapa manual de colar o token é temporária.
- Testes automatizados de front-end (unitários/E2E) não foram configurados como parte do projeto — a verificação foi feita manualmente (via script Playwright) para este incremento, mas não ficou como suíte permanente.
- Estilização é mínima, funcional — sem preocupação visual além de ser usável.

## Próximo passo
Depende de você: enviar a imagem da tela de Operação para destravar o front-end dela, ou seguir com Fechamento diário / Relatórios (ambos ainda sem escopo definido).
