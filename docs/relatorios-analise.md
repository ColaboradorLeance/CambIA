# Análise de dados — Relatórios

Levantamento de todos os tipos de relatório/métrica que dá pra construir com os dados que já existem no sistema hoje (Cliente, Banco, Usuário, Operação, e o próprio Fechamento Diário do Incremento 13), organizados por categoria. Nada aqui está implementado — é para eu e o usuário decidirmos juntos o escopo da v1 antes de escrever qualquer código.

Diferença em relação ao Fechamento Diário: o Fechamento é **um dia, ao vivo, sempre recalculado**. Relatórios é o oposto — **período arbitrário, com filtros, e a razão de existir é justamente comparar ao longo do tempo** (o que foi deliberadamente deixado de fora do Fechamento Diário).

## 1. Relatório de Operações (o mais parecido com "olhar a planilha filtrada")

| Dado | Status | Observação |
|---|---|---|
| Lista de operações num intervalo de datas | ✅ | Já existe `findByData`; precisa de uma versão por intervalo |
| Filtro por Cliente | ✅ | Campo já existe na Operação |
| Filtro por Banco | ✅ | Campo já existe |
| Filtro por Moeda | ✅ | Campo já existe (texto livre) |
| Filtro por C/V (compra/venda) | ✅ | Campo já existe (texto livre) |
| Filtro por Status | ✅ | `EM_ANDAMENTO` / `COMPLETO` |
| Filtro por quem criou/completou | ✅ | Existe desde o Incremento 13a |
| Export dessa lista filtrada (Excel/PDF) | ✅ | Mesma infraestrutura do Incremento 13c |

## 2. Comparativos e tendências (o que ficou de fora do Fechamento Diário)

| Dado | Status | Observação |
|---|---|---|
| Resultado (comissão líquida) por dia, num período — série temporal | ✅ | Reaproveita o cálculo do `FechamentoService`, rodado dia a dia no período |
| Comparação entre dois períodos (ex: este mês vs. mês passado) | ✅ | Duas consultas, uma por período |
| Acumulado do período (MTD, período customizado) | ✅ | Soma simples |
| Média/tendência (média móvel, dia com melhor/pior resultado) | ✅ | Cálculo em cima da série temporal |
| Evolução do volume operado (ME) ao longo do tempo | ✅ | Mesma lógica, outra métrica |

## 3. Relatório por dimensão (ranking / distribuição)

| Dado | Status | Observação |
|---|---|---|
| Ranking de Clientes por volume operado ou comissão gerada | ✅ | Agrupamento já existe no Fechamento (por dia) — aqui seria por período |
| Ranking de Bancos por volume ou comissão | ✅ | Idem |
| Distribuição por Moeda | ✅ | Idem |
| Distribuição por C/V (compra vs. venda) | ✅ | Idem |
| Ranking de Usuários por operações criadas/completadas | ✅ | Existe desde o 13a, nunca foi agregado num relatório ainda |

## 4. Relatório de posição/exposição (aberto)

| Dado | Status | Observação |
|---|---|---|
| Operações "Em andamento" há mais de X dias (envelhecimento) | ✅ | Campo `data` já existe, é só calcular a diferença |
| Exposição em aberto por Cliente ou Banco (não só por Moeda, que já existe no Fechamento) | ✅ | Mesma agregação, outra dimensão |

## 5. Indicadores por perfil (quem vê o quê)

| Dado | Status | Observação |
|---|---|---|
| Painel resumido para Usuário comum (ex: "minhas operações", produtividade pessoal) | ✅ | Filtra por `criadoPorUsuarioId` do usuário logado |
| Painel gerencial para Admin (visão consolidada de todos) | ✅ | Sem filtro de usuário |

## 6. Dados fiscais/contábeis

Mencionados na visão original do sistema, mas **explicitamente fora do escopo por enquanto** (decisão já registrada em `pendencias.md`).

| Dado | Status | Observação |
|---|---|---|
| IOF / IR agregado por período | ⛔ | Campo não existe na Operação — decisão de produto pendente, não é só "somar uma coluna" |

## 7. Formato e distribuição

Já resolvido tecnicamente no Fechamento Diário (13c/13d) — a pergunta aqui é só **quais dessas opções fazem sentido para Relatórios**:

| Opção | Já existe a infraestrutura? |
|---|---|
| Tela interativa (escolher filtros, ver na hora) | Não ainda, mas seria a mais natural para "relatório com filtro" |
| Export sob demanda (PDF/Excel) | ✅ Sim (Apache POI / OpenPDF já integrados) |
| Geração agendada/automática | ✅ Sim (mesmo mecanismo do 13d), mas relatórios de período fixo (ex: "todo dia 1º gera o relatório do mês anterior") fazem menos sentido que fechamento diário, que é sempre "hoje" |

## Perguntas para fechar o escopo da v1

1. **Que relatórios entram na v1?** Sugiro começar pelas seções 1 (Operações filtradas), 2 (comparativos/tendências — é o que ficou pendente do Fechamento Diário) e 3 (rankings por dimensão), deixando 4 e 5 para depois se fizer sentido.
2. **Quem vê o quê?** Usuário comum só vê os próprios números (seção 5) ou todo mundo vê tudo, igual ao Fechamento Diário hoje?
3. **Formato de entrega**: tela interativa com filtros é o pedido central aqui (diferente do Fechamento, que é sempre "hoje")? Export sob demanda também, ou fica só na tela por enquanto?
4. **Período livre ou pré-definido?** O usuário escolhe datas de início/fim livremente, ou só opções fixas (hoje, semana, mês, ano)?
5. **Geração agendada faz sentido para Relatórios**, ou isso é específico do Fechamento Diário (que é sempre "hoje") e Relatórios fica só sob demanda?
