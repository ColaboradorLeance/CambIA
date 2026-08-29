# Incremento 11 — Comissão por fórmula (não mais percentual fixo)

Status: ✅ concluído (backend + entrada em texto) — construtor visual de arrastar/soltar fica para uma etapa futura

## Por que isso mudou

O usuário corrigiu uma decisão anterior: a "comissão líquida" de um banco **não é um percentual simples** — é o resultado de um cálculo/fórmula que cada banco define à sua maneira, no mesmo estilo de fórmula de planilha que já usam hoje. Exemplo real fornecido:

```
=N4*70%-N4*70%*4,65%
=N7*70%
```
onde `N` é a coluna do Total Bruto do Câmbio. Cada banco tem sua própria fórmula — algumas simples (só um percentual), outras com descontos adicionais.

## O que foi feito

- **`FormulaComissao`** — um parser/avaliador de expressões escrito do zero (sem `eval`/scripting, por segurança, já que o texto vem de entrada do usuário). Suporta a variável `N` (Total Bruto do Câmbio — única variável confirmada com o usuário), operadores `+ - * /`, `%` (percentual) e parênteses, com decimais aceitando vírgula ou ponto (igual ao Excel em português).
- **`Banco`** trocou o campo `percentualComissao` (número) por `formulaComissao` (texto). Migration `V8` substitui a coluna no banco de dados.
- Validação no cadastro/edição do Banco: a fórmula é testada (avaliada com um valor de exemplo) antes de salvar — fórmula com erro de sintaxe é rejeitada com `400`.
- `OperacaoCalculo` agora avalia a fórmula do banco sobre o Total Bruto do Câmbio da operação, em vez de multiplicar por um percentual fixo.
- Front-end: campo de fórmula (texto) na tela de Bancos, com dica de sintaxe. **O construtor visual "arrastar e soltar" que você pediu fica para uma etapa futura** — por ora, o campo é texto livre, como combinamos (entrega em duas etapas).

## Como foi validado

1. **Testes unitários do parser** (`FormulaComissaoTests`) — incluindo a fórmula real que você passou (`N*70%-N*70%*4,65%`), reproduzindo o valor exato da planilha: **R$ 6.144,89** (antes, com percentual aproximado, o sistema calculava R$ 6.144,43 — a diferença que motivou você a corrigir isso).
2. **Regressão completa do backend**: 63 testes, 0 falhas.
3. **Ponta a ponta no navegador**, contra a stack inteira em Docker: cadastrei o banco TLX com a fórmula real, um cliente, e uma operação com os valores reais da planilha (Valor em ME 885.242,40, Nivelamento 5,1960, Taxa Final 5,1856) — ao completar a operação, o sistema calculou **R$ 4.590.512,99 / Total Bruto R$ 9.206,52 / Comissão R$ 6.144,89**, reproduzindo a linha real da planilha **com exatidão total**, pela primeira vez sem nenhuma aproximação.

## Atenção: dado de demonstração foi resetado
Como a mudança trocou uma coluna da tabela `bancos`, precisei apagar o volume do Postgres em Docker (`docker compose down -v`) e recriar os dados de demonstração do zero. Isso só afeta dados de teste — não haveria problema equivalente com dados reais, mas fica registrado que uma mudança de schema como essa exige atenção especial depois que existirem dados de verdade.

## O que ainda falta
- **Construtor visual de arrastar/soltar** para montar a fórmula sem digitar texto — combinado como próxima etapa.
- Só `N` (Total Bruto do Câmbio) é suportado como variável — se surgir necessidade de outra variável (Valor em ME, Taxa Final etc.), precisa ser confirmado antes de adicionar.

## Próximo passo
Construtor visual de fórmula (arrastar/soltar), ou seguir para outra frente do roadmap.
