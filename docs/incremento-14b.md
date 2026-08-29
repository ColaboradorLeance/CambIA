# Incremento 14b — Comparativos e tendências

Status: ✅ concluído

Segunda sub-etapa de Relatórios. Cobre exatamente o que tinha ficado de fora do Fechamento Diário (ver seção 6 de [fechamento-diario-analise.md](fechamento-diario-analise.md)).

## O que foi feito

- **`PeriodoResolver.anterior(periodo, hoje)`** — calcula o intervalo do período anterior equivalente (ontem, semana passada, mês calendário anterior, ano calendário anterior), reaproveitando o mesmo `resolver()` do Incremento 14a com uma data de referência deslocada.
- **`FechamentoService.calcularResultadoDoDia(data)`** — novo método que reaproveita a mesma lógica de cálculo do Fechamento Diário (`calcularResultado`), mas sem recalcular a posição em aberto (que não é por dia) a cada iteração — evita trabalho repetido ao montar uma série de até 366 pontos.
- **`GET /relatorios/comparativo?periodo=`** — para o período pedido, devolve:
  - Série temporal diária (`data`, `totalReais`, `totalComissaoLiquida` de cada dia do período);
  - Acumulado do período (soma da série);
  - Média diária de comissão líquida;
  - Melhor e pior dia (por comissão líquida, considerando só dias com pelo menos uma operação completa — dias vazios não competem por "pior dia");
  - Comparação com o período anterior equivalente e variação percentual (fica `null`, não `Infinity` ou erro, quando o período anterior não teve comissão nenhuma).
- Renomeei o controller de `RelatorioOperacoesController` para `RelatorioController`, já que agora ele hospeda mais de um tipo de relatório sob `/relatorios/**` (e vai crescer mais nas próximas sub-etapas).
- **Front-end**: nova tela "Comparativos" com seletor de período, cartão do período atual (total, comissão, média, melhor/pior dia), cartão de comparação com o período anterior (com variação percentual), e uma tabela de série temporal mostrando só os dias com movimento (evita uma tabela de 365 linhas vazias ao olhar "Este ano").

## Como foi validado

1. **TDD**: 4 novos testes puros em `PeriodoResolverTests` (período anterior de cada um dos 4 períodos, incluindo a fronteira de mês/ano) e 5 testes de integração em `RelatorioComparativoControllerTests` (série de um único dia acumula e tira média corretamente, comparação com período anterior calcula a variação percentual certa, variação fica `null` quando o período anterior está zerado, dias com operação não completada ficam de fora do melhor/pior dia, período ausente retorna 400). Red → Green confirmado antes de implementar.
2. **Regressão completa**: 93 testes, 0 falhas (84 anteriores + 9 novos).
3. **Performance**: período "Este ano" (366 dias) na stack real em Docker respondeu em ~0,66s — sem necessidade de otimização adicional por enquanto.
4. **Ponta a ponta no navegador**, contra a stack em Docker: conferi "Este ano" (mostrando corretamente o único dia com operação completa como melhor e pior dia, com o período anterior de 2025 zerado) e "Este mês" (mostrando 0,00 no mês atual e -100,00% de variação em relação a julho, que teve a operação de R$ 70,00 de comissão).

## O que ainda falta
- **14c. Rankings por dimensão** — ranking de clientes/bancos por volume ou comissão, distribuição por moeda/C-V, ranking de usuários.
- **14d. Posição/exposição em aberto** — operações "em andamento" há mais de X dias, exposição por cliente/banco.

## Próximo passo
Incremento 14c — rankings por dimensão.
