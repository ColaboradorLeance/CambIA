# CambIA

Sistema para registrar e controlar operações de compra e venda de moedas internacionais (câmbio), substituindo o controle atual feito em planilha.

Uso interno, 10 a 50 usuários.

## Documentação do projeto

- [docs/dominio.md](docs/dominio.md) — entidades, campos, o que é manual vs. calculado, fórmulas confirmadas e regras de negócio
- [docs/decisoes.md](docs/decisoes.md) — decisões arquiteturais e de produto já fechadas
- [docs/pendencias.md](docs/pendencias.md) — decisões de negócio ainda pendentes (não inventar, perguntar ao dono do produto antes de implementar)
- [docs/roadmap.md](docs/roadmap.md) — incrementos planejados

## Como trabalhar neste projeto

- **Fase atual: núcleo do backend completo (Cliente, Banco, Usuário, Autenticação, Operação com cálculo e status) + front-end inicial (login e CRUD de Cliente/Banco/Usuário). Ver [docs/roadmap.md](docs/roadmap.md) para o que falta.**
- Desenvolvimento incremental com **TDD** (backend) / verificação manual real em navegador (front-end): para cada incremento, definir comportamento esperado → tirar dúvidas de negócio → escrever testes (ou, no front-end, rodar de verdade e corrigir o que quebrar) → implementar o mínimo para passar → refatorar → rodar testes de regressão → atualizar documentação → o usuário valida o incremento antes do próximo.
- **Nunca inventar regra de negócio ou fórmula financeira.** Se um cálculo ou comportamento não está confirmado em `docs/dominio.md`, ele é uma pendência (`docs/pendencias.md`) — perguntar ao usuário antes de implementar, não supor.
- O usuário atua como PO/PM do projeto. Antes de implementar algo nas áreas ainda marcadas como pendentes, perguntar.
- Manter esta documentação (domínio, decisões, pendências, roadmap) atualizada conforme o projeto evolui — ela é a fonte de contexto entre sessões.

## Stack técnica

- Backend: Java 21 / Spring Boot 4, PostgreSQL, Flyway, Testcontainers (dev/teste)
- Front-end: React (Vite), em `frontend/`, consumindo a API REST
- Hospedagem: servidor próprio (on-premise)
