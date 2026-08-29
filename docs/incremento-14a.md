# Incremento 14a — Relatório de Operações filtradas

Status: ✅ concluído

Primeira sub-etapa de Relatórios. Escopo geral do Incremento 14 confirmado com o usuário (ver [decisoes.md](decisoes.md) e [relatorios-analise.md](relatorios-analise.md)): tela interativa com filtros, sem export nesta v1, período pré-definido (não datas livres), visível para qualquer usuário autenticado.

## O que foi feito

- **`Periodo`** (enum: `HOJE`, `SEMANA`, `MES`, `ANO`) e **`PeriodoResolver`** — função pura que resolve um período em um intervalo de datas (`IntervaloDatas`), recebendo "hoje" como parâmetro para ser testável sem depender do relógio real (mesma técnica usada no agendador do Incremento 13d).
- **Convenção de período assumida** (não é uma regra de negócio/financeira, é só um padrão de UI — documentando para o usuário poder corrigir se esperava outra coisa): "semana" = semana corrente completa, segunda a domingo; "mês" = mês corrente completo, dia 1 ao último dia; "ano" = ano corrente completo, 1º de janeiro a 31 de dezembro. Não é "até hoje" (to-date) — é o período inteiro, mesmo que inclua dias futuros (que naturalmente não terão operações ainda).
- **`GET /relatorios/operacoes`** — lista operações dentro do período, com filtros opcionais combináveis: `clienteId`, `bancoId`, `moeda`, `cv`, `status`, `criadoPorUsuarioId`, `completadoPorUsuarioId`. Reaproveita `OperacaoService.toResponse()` — mesmos números da tela de Operações, nunca diverge.
- **Front-end**: nova tela "Relatórios" com filtros (período, cliente, banco, moeda, C/V, status), um resumo (quantidade de operações, total R$, comissão líquida das completas) e a tabela de resultados.

## Como foi validado

1. **TDD**: `PeriodoResolverTests` (4 testes puros, sem Spring, cobrindo os 4 períodos e as fronteiras de mês/ano) e `RelatorioOperacoesControllerTests` (5 testes de integração: período "hoje" isola corretamente, filtro por cliente, filtros combinados de banco+status, período "ano" inclui o ano corrente e exclui anos anteriores, período ausente retorna 400). Red → Green confirmado em cada arquivo antes de implementar.
2. **Regressão completa**: 84 testes, 0 falhas (75 anteriores + 9 novos).
3. **Ponta a ponta no navegador**, contra a stack em Docker: criei um cliente/banco/operação novos via API, naveguei até "Relatórios", conferi que o filtro "Hoje" mostra só a operação do dia, e que trocar para "Este ano" traz também uma operação completa de meses atrás (dado real do banco, sobrevivente de testes anteriores) com o total R$ e a comissão líquida agregados corretamente (R$ 5.000,00 e R$ 70,00).

## O que ainda falta
- **14b. Comparativos e tendências** — série temporal do resultado por dia, comparação entre períodos, acumulado.
- **14c. Rankings por dimensão** — ranking de clientes/bancos por volume ou comissão, distribuição por moeda/C-V, ranking de usuários.
- **14d. Posição/exposição em aberto** — operações "em andamento" há mais de X dias, exposição por cliente/banco.

## Próximo passo
Incremento 14b — comparativos e tendências.
