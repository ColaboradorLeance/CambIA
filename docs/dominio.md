# Domínio

Fonte: planilha atual de controle de operações + descrição do processo pelo usuário (dono do produto). Todas as fórmulas abaixo foram confirmadas reproduzindo os valores de linhas reais da planilha — nenhuma foi suposta a partir do nome do campo.

## Entidades

### Cliente
- Nome
- CPF ou CNPJ

O campo E-mail estava na visão inicial do sistema, mas o usuário confirmou que não é necessário e foi removido do cadastro (decisão tomada durante o Incremento 2).

Regras de PF vs. PJ ainda não definidas — ver [pendencias.md](pendencias.md).

### Banco
- Código do Banco — identificador interno do banco (manual, distinto do "Código do banco" opcional que existe na Operação)
- Sigla
- Nome
- Taxa de rebate — percentual manual, cadastrado no banco. **Participa da fórmula de comissão** através da variável `R` (ver Modelo de Cálculo abaixo) — confirmado pelo usuário no Incremento 16
- Cálculo — referência (select) a um Modelo de Cálculo já cadastrado (ver abaixo); o Banco não digita mais a fórmula diretamente, só escolhe entre os modelos existentes (Incremento 15)

### Modelo de Cálculo (cadastro próprio, Incremento 15)
- Nome (rótulo livre, para reconhecer o modelo no select do Banco) — editável a qualquer momento
- Fórmula de comissão — cadastrada pelo Admin, no mesmo estilo de planilha usado antes (ex: `N*70%-N*70%*4,65%`). Antes do Incremento 15 esse campo vivia direto no cadastro do Banco (Incremento 11); passou a ser um cadastro próprio e reutilizável entre bancos.
- **Duas variáveis suportadas** (Incremento 16, antes só `N`): `N` = Total Bruto do Câmbio da Operação; `R` = Taxa de Rebate cadastrada no Banco que está usando este Modelo de Cálculo (o mesmo modelo pode ser reaproveitado por bancos com taxas de rebate diferentes — a fórmula é a mesma, o valor de `R` varia por banco). `R` aceita o mesmo sufixo `%` que `N` (ex: `N*R%` interpreta a taxa de rebate armazenada como percentual).
- Um Modelo de Cálculo em uso por pelo menos um Banco não pode ser removido (bloqueado com `409`).

### Usuário (do sistema)
- Nome
- E-mail (único — é a identidade de login, ver [decisoes.md](decisoes.md))
- Perfil: Admin, Analista ou Consultor (ver [decisoes.md](decisoes.md) — renomeado de "Usuário" para "Analista" e acrescentado "Consultor", visualização apenas, no incremento de edição de Operação/auditoria)

Sem senha — autenticação por link mágico enviado ao e-mail cadastrado (Incremento 4).

### Operação
Ver detalhamento completo abaixo.

## Campos da Operação

### Confirmados (vêm da planilha atual)

| Campo | Tipo | Origem |
|---|---|---|
| Data | manual, só na criação | operador. **Não pode ser editada depois que a ordem é registrada** (confirmado pelo usuário) — trocar a operação de dia exige criar uma nova |
| ID do trade | automático | gerado pelo sistema (chave interna, ver decisoes.md) |
| Código do banco | manual, opcional | texto livre, referência que vem do banco. Campo continua existindo na API (sempre opcional), mas foi removido da tela de Registrar Operação a pedido do usuário — não é mais coletado pela UI |
| Cliente | manual | referência ao cadastro de Cliente |
| Banco | manual | referência ao cadastro de Banco |
| C/V (Compra/Venda) | manual | valores conhecidos: `C`, `V`. Implementado como texto livre desde o Incremento 5 (sem validar domínio fechado) — significado completo (ex: existe `NA`?) ainda não confirmado, ver pendências |
| PR/CR/VIR | automático (Incremento 18) | valores conhecidos: `Pronto`, `Crédito`, `Virtual`. Campo continua existindo na API e aceitando os três valores, mas deixou de ser coletado na tela de Registrar Operação a pedido do usuário — toda operação nova é criada com `Pronto`. Operações já existentes com `Crédito`/`Virtual` mantêm seu valor original mesmo ao serem editadas (não são regravadas). Significado de negócio de cada valor ainda não confirmado, ver pendências |
| Moeda | manual | moeda estrangeira da operação. Implementado como texto livre desde o Incremento 5 (ex: "USD") — sistema deve suportar múltiplas moedas, BRL é sempre a moeda de liquidação; modelagem definitiva (lista fixa vs. cadastro) ainda pendente |
| Valor em ME | manual | valor monetário na moeda estrangeira da operação |
| Spot Asset | manual | cotação livre de mercado. **Não entra em nenhuma fórmula confirmada** — parece ser só referência/benchmark. Aceita até **4 casas decimais** (confirmado pelo usuário) |
| Nivelamento | manual | preço de custo da moeda para a casa ("margem de outras empresas", segundo o usuário). Aceita até **4 casas decimais** (confirmado pelo usuário) |
| Taxa Final | manual | taxa efetivamente cobrada do cliente |
| R$ | **calculado** (Incremento 6) | ver fórmula abaixo — sempre calculado |
| Total Bruto do Câmbio | **calculado** (Incremento 6) | ver fórmula abaixo — só quando C/V é "C" ou "V" |
| Valor Absoluto | **calculado** (Incremento 40) | ver fórmula abaixo — não depende de C/V (calculado mesmo quando C/V é desconhecido) |
| Comissão Líquida | **calculado** (Incremento 6, com fórmula real desde o Incremento 11) | ver fórmula abaixo — só quando Total Bruto foi calculado e o Banco tem uma fórmula cadastrada |
| Status da transação | manual (Admin/Analista; Consultor só visualiza) | três valores possíveis (Incremento 39): `ANDAMENTO` (padrão ao criar), `CONFIRMADO` e `CANCELADO` — só se sai de `ANDAMENTO` pra um dos dois, nunca o contrário e nunca de um pro outro. Regra "só calcula quando confirmado" (`ANDAMENTO`/`CANCELADO` nunca têm valor calculado) |
| Criado por / Criado em | **automático** (Incremento 13a) | capturado do usuário autenticado no momento da criação, não digitado |
| Completado por / Completado em | **automático** (Incremento 13a) | capturado do usuário autenticado no momento em que o status vira `CONFIRMADO`; fica vazio enquanto `ANDAMENTO`/`CANCELADO` |

### Novos, identificados na tela de referência do cliente (ainda sem fórmula/origem/regra definida)

Estes campos foram citados pelo usuário a partir de uma tela atualmente usada pelo cliente, mas a imagem em si ainda não foi recebida. Nenhuma fórmula foi suposta.

- Data da Liquidação
- Valor Moeda — possível duplicata de "Valor em ME" (não confirmado)
- Cotação Nivelamento — possível duplicata de "Nivelamento" (não confirmado)
- Cotação Final — possível duplicata de "Taxa Final" (não confirmado)
- Valor Histórico em Aberto
- Despesa do Banqueiro
- Despesa do Banqueiro em Real
- Valor IOF Despesa Banqueiro
- Valor IR Despesa Banqueiro
- Valor Total Despesa Banqueiro
- Total Ajuste NDF
- Valor Recebido Líquido Real

### Fora do escopo do cálculo por enquanto
- IOF / IR (campo original da visão inicial do sistema) — o usuário confirmou que não é necessário para o cálculo no momento.

### Checagem contra a visão inicial do sistema

A visão inicial do projeto citava os campos: Tipo da operação, Cliente, Banco, Tipo/moeda, Valor monetário desejado, Nivelamento, Spot Asset, Taxa de câmbio, Taxa × valor, Comissão líquida, Status da transação, Data, ID do trade, Total em reais, IOF ou IR, Reais final.

Cruzando com o que foi confirmado depois via planilha:

| Campo da visão inicial | Mapeamento |
|---|---|
| Tipo da operação | = C/V |
| Valor monetário desejado | = Valor em ME |
| Taxa de câmbio | = Taxa Final |
| **Taxa × valor** | = R$ (o nome bate literalmente com a fórmula confirmada `Valor em ME × Taxa Final`) |
| Comissão líquida | = Comissão Líquida |
| Status da transação | = Status da transação (lista pendente) |
| ID do trade | = ID do trade (decidido) |
| IOF ou IR | fora do escopo por enquanto |
| Tipo/moeda | = Moeda da operação. **Confirmado: o sistema deve suportar múltiplas moedas estrangeiras**, não apenas USD (BRL é sempre a moeda de liquidação). Modelagem exata do cadastro de moedas (lista fixa vs. administrável) ainda em aberto — ver pendências. |
| **Total em reais** | **não mapeado** — pode ser sinônimo de "R$", ou um campo calculado diferente; não confirmado |
| **Reais final** | **não mapeado** — pode ser o mesmo conceito de "Valor Recebido Líquido Real" (campo novo da tela) ou algo distinto; não confirmado |

Os três campos em negrito foram adicionados às pendências.

## Fórmulas confirmadas

Confirmadas reproduzindo múltiplas linhas reais da planilha (bancos TLX e BZA, operações de compra e de venda), com precisão de centavos.

```
R$ = Valor em ME × Taxa Final
```

```
Total Bruto do Câmbio = Valor em ME × (Nivelamento − Taxa Final)   [se C/V = Venda]
Total Bruto do Câmbio = Valor em ME × (Taxa Final − Nivelamento)   [se C/V = Compra]
```
Ou seja: a diferença entre o custo (Nivelamento) e a taxa cobrada (Taxa Final) é sempre calculada a favor da casa, independente da direção da operação.

```
Valor Absoluto = |Taxa Final − Nivelamento| × Valor em ME   (2 casas decimais)
```
Fórmula pedida pelo usuário (Incremento 40): o módulo do spread vezes o valor em ME, sem depender do C/V — dá o mesmo número que o Total Bruto do Câmbio, mas sempre positivo e mesmo quando C/V é desconhecido (caso em que Total Bruto fica `null`).

```
Comissão Líquida = fórmula de comissão do Banco aplicada ao Total Bruto do Câmbio
```

A fórmula é texto livre no estilo planilha, com `N` representando o Total Bruto do Câmbio (única variável suportada). Exemplos reais fornecidos pelo usuário:
- TLX: `N*70%-N*70%*4,65%` → reproduz exatamente R$ 6.144,89 no exemplo real da planilha
- BZA: `N*70%` (sem desconto adicional)

Cada banco tem sua própria fórmula, cadastrada pelo Admin (ver Incremento 11 — decisão que substituiu a suposição anterior de "percentual simples").

### Quando os campos calculados são preenchidos
R$, Total Bruto do Câmbio, Valor Absoluto e Comissão Líquida só têm valor quando a operação está com status "completo" (fechada). Enquanto está "em andamento", esses campos ficam zerados/vazios — confirmado observando linhas de teste na planilha.

### Decisão de produto confirmada
No novo sistema, R$, Total Bruto do Câmbio, Valor Absoluto e Comissão Líquida devem ser **calculados automaticamente e exibidos em tempo real** (não digitados manualmente, como é feito hoje na planilha).

## Regras de negócio confirmadas

1. A casa sempre embolsa a diferença a seu favor entre o custo (Nivelamento) e a taxa cobrada do cliente (Taxa Final), seja a operação de compra ou de venda.
2. Cada banco define sua própria fórmula de comissão (não necessariamente um percentual simples) aplicada sobre o Total Bruto do Câmbio, cadastrada pelo Admin no cadastro do banco.
3. Os valores calculados da operação (R$, Total Bruto, Valor Absoluto, Comissão Líquida) só existem quando a ordem está com status "confirmado" (Incremento 39; renomeado de "completo").
4. Spot Asset é uma cotação de referência de mercado e não participa de nenhum cálculo confirmado até agora.
5. IOF/IR não entra no cálculo por enquanto (fora do escopo do MVP).

## Perfis de acesso (confirmado no Incremento 17)

- **Admin**: acesso à área de cadastro (Clientes, Bancos, Modelos de Cálculo) e é o único que pode cadastrar/alterar/remover Usuários (perfis de acesso). Tudo que Analista pode fazer também.
- **Analista** (renomeado de "Usuário"): pode fazer tudo no sistema, **exceto** criar, alterar ou remover Usuários — isso inclui gerar/editar/completar Operações, consultar relatórios, ver o Painel, Fechamento e seu histórico, e também gerenciar Clientes/Bancos/Modelos de Cálculo (não é mais só leitura, desde o Incremento 17).
- **Consultor**: acesso restrito **só às telas de Operações** — Operações (ordens em andamento), Listagem de Ordens (ordens confirmadas, Incremento 27) e Histórico de Operações — não vê Painel, Relatórios, Fechamento nem nenhum Cadastro (Clientes/Bancos/Modelos de Cálculo/Usuários).

## Edição e histórico de Operação

- Uma Operação só pode ser editada enquanto estiver **Em andamento** — depois de completada, fica travada (edição retorna `409`).
- Podem editar: Admin e Analista (Consultor não). Todos os campos manuais podem ser alterados na edição.
- Toda criação, edição e conclusão gera um evento de auditoria (quem fez, quando e, na edição, os valores anteriores, incluindo os nomes de Cliente e Banco capturados no momento do evento) — consultável na tela de Histórico de Operações, aberta ao Consultor também (é a exceção dentro do seu acesso restrito).
