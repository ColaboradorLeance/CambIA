# Incremento 6 — Cálculo automático em tempo real

Status: ✅ concluído

## O que foi feito

- Classe `OperacaoCalculo`, pura (sem Spring, sem banco), implementando exatamente as fórmulas confirmadas em [dominio.md](dominio.md):
  - `R$ = Valor em ME × Taxa Final` — sempre calculado, independente do C/V.
  - `Total Bruto do Câmbio = Valor em ME × (Nivelamento − Taxa Final)` se C/V = "V", ou `× (Taxa Final − Nivelamento)` se C/V = "C".
  - `Comissão Líquida = Total Bruto do Câmbio × percentual de comissão do Banco`.
- **Quando C/V não é "C" nem "V"** (valor livre, já que o domínio desse campo continua em aberto — pendência #2): `Total Bruto do Câmbio` e `Comissão Líquida` ficam `null` em vez de supor uma fórmula. `R$` continua sendo calculado normalmente, pois não depende do C/V.
- Os três campos calculados aparecem na resposta da API (`GET /operacoes/{id}`, `GET /operacoes`, e no retorno do `POST /operacoes`) — calculados **em tempo real a cada leitura**, não armazenados no banco. Isso garante que nunca ficam desatualizados em relação aos dados de entrada.
- `BancoRepository` ganhou uma consulta (`findPercentualComissaoById`) para buscar só o percentual do banco sem expor a entidade `Banco` para o pacote de Operação — mantém o encapsulamento.

## Por que os valores não são gravados no banco
Decisão técnica: como são sempre derivados de outros campos já armazenados (Valor em ME, Nivelamento, Taxa Final, e o percentual do Banco), calculá-los sob demanda evita qualquer risco de inconsistência (ex: alguém corrigir o percentual do banco depois e os valores antigos ficarem errados). Bate com a decisão já registrada de "calculado automaticamente, em tempo real".

## Como o TDD foi aplicado

1. **Testes unitários da fórmula primeiro** (`OperacaoCalculoTests`, sem Spring — rodam em milissegundos): venda, compra, C/V desconhecido, banco sem percentual, e os dois exemplos **reais da planilha** (TLX e BZA) usados na fase de descoberta, verificando os valores exatos com precisão de centavos.
2. **Red** — rodei antes de criar `OperacaoCalculo`/`ValoresCalculados`: erro de compilação (classes não existiam).
3. **Green** — implementei as duas classes; os 6 testes unitários passaram de primeira, incluindo os dois exemplos reais batendo exatamente (`R$ 4.590.512,99` e `R$ 12.267,26`, `Total Bruto R$ 9.206,52` e `R$ 12,22`).
4. Conectei a fórmula ao fluxo da Operação (`OperacaoService.toResponse`) e adicionei 2 novos casos aos testes de integração já existentes (`OperacaoControllerTests`): valores calculados aparecendo na resposta real da API, e o caso de C/V desconhecido não quebrando nada.
5. **Regressão**: suíte completa, **53 testes, 0 falhas**.
6. Validação manual com os dados reais da TLX: `reais` e `totalBrutoCambio` bateram exatamente com a planilha; `comissaoLiquida` ficou próxima mas não idêntica ao valor histórico, porque usei um percentual de banco aproximado (0,6674) — a fórmula está correta, a pequena diferença é só da precisão do percentual cadastrado (o percentual exato de cada banco real ainda precisa ser confirmado quando os bancos forem cadastrados de verdade).

## O que NÃO está incluído
- A regra "só calcula quando a operação está completa" (documentada em dominio.md) **não foi aplicada ainda**, porque o campo Status não existe (Incremento 7, bloqueado pela pendência #1). Por enquanto, toda operação criada já mostra os valores calculados imediatamente.
- IOF/IR continuam fora do cálculo (fora do escopo do MVP).

## Como testar você mesmo
```
./mvnw spring-boot:test-run
```
Crie um Cliente, um Banco e uma Operação (ver [incremento-5.md](incremento-5.md)) e observe os campos `reais`, `totalBrutoCambio` e `comissaoLiquida` já calculados na resposta.

## Próximo passo
Incremento 7 — Status da operação. **Continua bloqueado pela pendência #1** (lista completa de status e transições) — preciso dessa resposta antes de implementar.
