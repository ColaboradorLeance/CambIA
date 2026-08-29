# Fechamento Diário — Análise de Dados

Este documento é uma etapa de **descoberta**, não uma especificação fechada. Lista, de forma exaustiva, os tipos de dado que um fechamento diário de uma operação de câmbio costuma conter — cruzando com o que o sistema já tem disponível hoje (Incrementos 1–12) e o que exigiria dado novo. Nada aqui foi implementado ou decidido; serve para você escolher o que entra na primeira versão.

## Como ler este documento
Cada item tem uma marcação:
- ✅ **Disponível hoje** — dá para calcular só com o que já existe no banco de dados.
- 🟡 **Disponível parcialmente** — o dado existe, mas falta alguma peça (ex: um filtro, uma agregação nova).
- ⛔ **Precisa de dado novo** — não existe nenhum campo hoje que sustente isso; exigiria uma migration/campo novo, e possivelmente uma nova rodada de descoberta com você.

---

## 1. Resumo operacional do dia

O que aconteceu, em números brutos.

| Dado | Status | Observação |
|---|---|---|
| Quantidade total de operações registradas na data | ✅ | Filtra `Operação.data = dia escolhido` |
| Quantidade por status (Em andamento / Completo) | ✅ | Já temos o campo `status` |
| Quantidade por tipo (Compra / Venda, campo C/V) | ✅ | Campo já existe (texto livre) |
| Quantidade por moeda (USD, EUR, etc.) | ✅ | Campo `moeda` já existe |
| Quantidade por banco | ✅ | Via `bancoId` |
| Quantidade por cliente | ✅ | Via `clienteId` |
| Lista detalhada de todas as operações do dia (uma linha por operação) | ✅ | É basicamente a tabela que já existe na tela, filtrada por data |
| Operações criadas no dia mas ainda "Em andamento" (pendentes de fechar) | ✅ | Filtra por `data` + `status = EM_ANDAMENTO` |

## 2. Resultado financeiro do dia

O "quanto ganhamos hoje" — provavelmente o coração do fechamento.

| Dado | Status | Observação |
|---|---|---|
| Soma do Valor em ME do dia (volume transacionado, por moeda) | ✅ | Soma agrupada por `moeda` — cuidado: não dá pra somar USD + EUR juntos sem definir uma moeda de referência |
| Soma do R$ do dia (total transacionado em reais) | ✅ | Soma de `reais`, só operações `COMPLETO` (as `EM_ANDAMENTO` não têm valor) |
| Soma do Total Bruto do Câmbio do dia | ✅ | "Receita bruta" do dia, antes da comissão |
| Soma da Comissão Líquida do dia | ✅ | Esse é, na prática, **o lucro do dia** |
| Ticket médio (valor médio por operação, em R$ ou em ME) | ✅ | Média simples sobre as operações completas do dia |
| Maior e menor operação do dia (por Valor em ME ou R$) | ✅ | Max/min sobre o mesmo conjunto |
| Margem média do dia (Comissão Líquida ÷ Total Bruto, ou ÷ R$) | ✅ | Cálculo derivado, sem necessidade de campo novo |

## 3. Quebras por dimensão (o mesmo resultado, fatiado)

Útil para responder "quem/o quê gerou o resultado".

| Dado | Status | Observação |
|---|---|---|
| Resultado (volume, bruto, comissão) por **Banco** | ✅ | Group by `bancoId` |
| Resultado por **Cliente** | ✅ | Group by `clienteId` |
| Resultado por **Moeda** | ✅ | Group by `moeda` |
| Resultado por **Tipo de operação** (Compra vs Venda) | ✅ | Group by `cv` |
| Resultado por **Usuário que operou** | ⛔ | **Não existe hoje** — a Operação não guarda quem a criou/completou. Precisaria de um campo de auditoria (ver seção 5). |
| Ranking dos clientes/bancos mais ativos do dia | ✅ | Deriva das quebras acima |

## 4. Posição / exposição em aberto

Olhando não só "o que fechou hoje", mas "o que ainda está em risco".

| Dado | Status | Observação |
|---|---|---|
| Total de operações "Em andamento" acumuladas (não só do dia) | ✅ | Filtra só por `status`, sem filtro de data |
| Exposição em aberto por moeda (soma do Valor em ME das operações em andamento) | ✅ | Mesma lógica, agrupada por moeda |
| Operações em andamento há mais de X dias (risco de operação "esquecida") | 🟡 | O campo `data` existe, mas é a data da operação, não necessariamente "há quantos dias está parada" — dá pra calcular, mas é bom confirmar se `data` é a data de referência certa para isso |

## 5. Auditoria e rastreabilidade

Quem fez o quê, e quando — normalmente esperado num fechamento para fins de controle interno/compliance, mesmo que não tenha sido pedido explicitamente ainda.

| Dado | Status | Observação |
|---|---|---|
| Quem registrou cada operação | ⛔ | Não existe campo `criadoPor` na Operação hoje |
| Quem mudou o status para "Completo" | ⛔ | Não existe campo nem histórico de mudança de status |
| Horário exato de criação/conclusão (não só a data) | ⛔ | `Operação.data` é uma data de negócio (a data da operação em si), não um timestamp de sistema |
| Histórico de alterações de uma operação (auditoria completa) | ⛔ | Não existe — hoje uma operação só é criada e tem o status mudado, sem histórico |

Esses quatro itens juntos formam uma decisão de arquitetura relevante: **se você quiser qualquer coisa de auditoria no fechamento, precisamos adicionar rastreamento de autoria/timestamp na Operação primeiro** (um incremento à parte, provavelmente).

## 6. Comparativos e tendências

Aqui a linha entre "Fechamento diário" e "Relatórios" (que é um item separado no roadmap) começa a ficar tênue — vale decidirmos onde ela fica.

| Dado | Status | Observação |
|---|---|---|
| Comparação do resultado de hoje vs. ontem | ✅ | É só rodar a mesma consulta em duas datas — mas é mais "relatório" que "fechamento" |
| Acumulado do mês (MTD — month to date) | ✅ | Soma com filtro de intervalo de datas |
| Média móvel / tendência dos últimos N dias | ✅ | Possível, mais interpretativo |
| Comparação entre bancos ao longo do tempo | ✅ | Mesma lógica, com mais dimensões |

## 7. Dados fiscais/contábeis

Mencionados na visão original do sistema, mas **explicitamente fora do escopo por enquanto** (decisão já registrada).

| Dado | Status | Observação |
|---|---|---|
| IOF do dia | ⛔ (fora de escopo) | Pendência conhecida — ver `pendencias.md` |
| IR do dia | ⛔ (fora de escopo) | Idem |
| Total para lançamento contábil | ⛔ | Depende dos itens acima |

## 8. Formato e distribuição (não é "dado", mas afeta o que faz sentido incluir)

Perguntas que não são sobre *quais dados*, mas sobre *como o fechamento chega até alguém*:
- É uma **tela** dentro do sistema (o usuário abre e olha), um **export** (PDF/Excel/CSV), ou os dois?
- É gerado **sob demanda** (o usuário escolhe uma data e clica em "gerar") ou **automático** (roda sozinho todo fim de dia, ex: às 18h)?
- Se for automático, precisa ser **enviado** para alguém (e-mail, por exemplo) ou só fica disponível para consulta?
- Existe a necessidade de **"fechar" o dia de verdade** — ou seja, uma vez fechado, os números daquele dia ficam congelados/travados (mesmo que uma operação daquele dia mude de status depois)? Isso é uma decisão de negócio importante: hoje, se eu recalculasse o fechamento de ontem, o resultado poderia mudar se uma operação de ontem virasse "Completo" só hoje.

---

## Perguntas para fechar o escopo da primeira versão

Não preciso de resposta para tudo de uma vez — mas essas são as decisões que mais mudam o tamanho do trabalho:

1. **Das seções 1–4, quais entram na primeira versão?** (Meu palpite: 1, 2 e 3 já dão um fechamento diário útil sozinhas; 4 é um bônus natural.)
2. **Auditoria (seção 5)** — precisa mesmo, ou fica para depois? Se precisar, é um pré-requisito técnico (adicionar autoria/timestamp na Operação) antes do fechamento em si.
3. **Comparativos (seção 6)** — isso é "Fechamento diário" pra você, ou fica reservado para o item futuro "Relatórios"? Prefiro não misturar os dois escopos sem essa definição.
4. **Formato**: tela, export, os dois? Manual ou automático?
5. **"Fechar" o dia de verdade** (travar os números) é necessário, ou o fechamento pode ser sempre "ao vivo" (recalculado toda vez que alguém olha)?
