# Incremento 14c — Rankings por dimensão

Status: ✅ concluído

Terceira sub-etapa de Relatórios.

## O que foi feito

- **`GET /relatorios/rankings?periodo=`** — para o período pedido, agrupa todas as operações (não só as completas — mesma regra do Fechamento Diário, onde a quantidade conta tudo mas R$/comissão só existem para operações completas) por cliente, banco, moeda e tipo (C/V), reaproveitando `FechamentoService.calcularQuebras()` e `agrupar()` (tornados package-private para isso — sem duplicar a lógica de agrupamento que já existia para o Fechamento Diário).
- **Ranking de usuários**: duas quebras novas, por quem **criou** a operação e por quem **completou** — a segunda exclui operações ainda "em andamento" (que não têm completador).
- **Ordenação**: diferente das "quebras" do Fechamento Diário (que preservam a ordem de aparição, porque ali é só uma listagem do dia), aqui cada lista vem **ordenada por comissão líquida, do maior para o menor** — faz sentido chamar de "ranking".
- Ajuste em `TestAuthSupport` (só nos testes): adicionei uma sobrecarga que aceita um nome customizado, necessária para simular dois usuários diferentes criando/completando operações no mesmo teste.

## Como foi validado

1. **TDD**: 3 testes de integração (`RelatorioRankingControllerTests`) — ranking por cliente/banco/moeda/tipo ordenado corretamente por comissão; ranking de usuários com criador e completador diferentes (uma operação criada por "Ana" e completada por "Beto" conta a comissão para o Beto no ranking de completador, e Ana nunca aparece nesse ranking porque não completou nada); período ausente retorna 400. Red → Green confirmado.
2. **Regressão completa**: 96 testes, 0 falhas (93 anteriores + 3 novos).
3. **Ponta a ponta no navegador**, contra a stack em Docker: tela "Rankings" com todas as seis quebras (cliente, banco, moeda, tipo, criador, completador), usando dados reais acumulados de incrementos anteriores, corretamente ordenados por comissão líquida decrescente.

## O que ainda falta
- **14d. Posição/exposição em aberto** — operações "em andamento" há mais de X dias, exposição por cliente/banco (a exposição por moeda já existe no Fechamento Diário).

## Próximo passo
Incremento 14d — posição/exposição em aberto. Última sub-etapa de Relatórios.
