# Incremento 13a — Auditoria na Operação (pré-requisito do Fechamento Diário)

Status: ✅ concluído

Primeira sub-etapa do Fechamento Diário (ver [fechamento-diario-analise.md](fechamento-diario-analise.md)). Sem isso, não dava para mostrar "quem registrou" no fechamento.

## O que foi feito

- Operação ganhou 4 novos campos: `criadoPorUsuarioId` + `criadoEm` (preenchidos automaticamente na criação) e `completadoPorUsuarioId` + `completadoEm` (preenchidos automaticamente só quando o status vira `COMPLETO`).
- O usuário autenticado é capturado automaticamente via `@AuthenticationPrincipal` (o mesmo mecanismo de sessão que já existia) — **não é um campo que o usuário preenche**, é registrado sozinho pelo sistema.
- A resposta da API (`OperacaoResponse`) agora inclui `criadoPorNome`, `criadoEm`, `completadoPorNome`, `completadoEm` — os nomes já resolvidos (não só o ID), pra não precisar de mais uma consulta no front-end.
- Front-end: a tela de Operações ganhou as colunas "Criado por" e "Completado por" (mostra "—" enquanto não completado).

## Como foi validado

1. **TDD**: teste novo (`registraQuemCriouEQuemCompletouAOperacao`) — cria uma operação como Usuário de teste, confirma `criadoPorNome` preenchido e `completadoPorNome` nulo; completa a operação, confirma `completadoPorNome` preenchido. Red → Green → **regressão completa: 66 testes, 0 falhas**.
2. **Ponta a ponta no navegador**, contra a stack em Docker: criei uma operação logado como Admin — a coluna "Criado por" já mostrou "Admin" antes mesmo de completar, e "Completado por" ficou em branco. Completei a operação — "Completado por" passou a mostrar "Admin" também.

## Atenção: dado de demonstração foi resetado de novo
Mesma situação dos incrementos anteriores que mudaram schema (`percentual → fórmula`, no Incremento 11): como `criado_por_usuario_id` é obrigatório e não existia antes, precisei resetar o volume do Postgres em Docker. Só afeta dado de teste.

## Escopo — o que isso NÃO é
Isso **não é um histórico de auditoria completo** (não registra todas as mudanças de todos os campos ao longo do tempo, só o momento de criação e o momento em que virou "Completo"). Foi o que você pediu especificamente ("quem registrou... quando o status mudou") — se um histórico mais completo for necessário no futuro, é uma conversa separada.

## Próximo passo
Incremento 13b — cálculo do fechamento diário (endpoint agregado) + tela mostrando resumo operacional, resultado financeiro, quebras por dimensão e posição em aberto (as 4 categorias que você escolheu para a v1).
