# Roadmap de incrementos

Cada incremento segue o ciclo: definir comportamento esperado → tirar dúvidas → escrever testes → implementar o mínimo → refatorar → rodar testes/regressão → atualizar documentação → validar com o usuário antes do próximo.

A ordem respeita dependências (Operação depende de Cliente e Banco existirem) e evita implementar qualquer coisa marcada como pendente em [pendencias.md](pendencias.md) antes da hora.

## 1. Setup do projeto — ✅ concluído
Estrutura base do projeto Spring Boot 4 / Java 21, PostgreSQL via Testcontainers (dev e teste), estrutura de testes JUnit 5. Sem regra de negócio — só infraestrutura mínima validada por um teste de health-check (`GET /actuator/health` retornando `status: UP` com o componente `db` também `UP`). Detalhes em [incremento-1.md](incremento-1.md).

## 2. Cadastro de Cliente (CRUD) — ✅ concluído
Nome, CPF ou CNPJ, e-mail. Sem regras de negócio complexas identificadas ainda (pendência de PF/PJ pode ser tratada depois, sem bloquear este incremento). Detalhes em [incremento-2.md](incremento-2.md).

## 3. Cadastro de Banco (CRUD) — ✅ concluído (fórmula corrigida no Incremento 11)
Nome + fórmula de comissão. ~~Percentual é um número simples~~ — corrigido no Incremento 11: é uma fórmula estilo planilha, não um percentual simples. Detalhes em [incremento-3.md](incremento-3.md) e [incremento-11.md](incremento-11.md).

## 4. Autenticação e perfis (Admin / Usuário) — ✅ concluído
Login sem senha por link mágico (Usuário tem só Nome + E-mail), sessão via token opaco, e proteção dos endpoints existentes exigindo perfil Admin. Permissões finas ficam para um incremento futuro, quando a pendência #9 for resolvida. Detalhes em [incremento-4.md](incremento-4.md). Envio real de e-mail (SMTP) ainda pendente — ver pendência #10.

## 5. Registro de Operação — dados manuais — ✅ concluído
Criar/listar/visualizar uma Operação com os campos manuais já confirmados (Data, Cliente, Banco, C/V, PR/CR/VIR, Moeda, Valor em ME, Spot Asset, Nivelamento, Taxa Final) e o ID do trade gerado automaticamente. **Sem cálculo automático ainda** — os campos calculados ficam de fora deste incremento até o próximo.

C/V, PR/CR/VIR e Moeda foram implementados como texto livre para não travar neste incremento nem inventar um domínio fechado — pendências #2, #3 e #5 continuam em aberto para quando isso precisar ser restringido. Detalhes em [incremento-5.md](incremento-5.md).

## 6. Cálculo automático em tempo real — ✅ concluído
R$, Total Bruto do Câmbio e Comissão Líquida calculados automaticamente (em tempo real, não armazenados) a partir das fórmulas confirmadas em [dominio.md](dominio.md). Quando C/V não é "C" nem "V", Total Bruto e Comissão ficam sem valor em vez de supor uma fórmula. A regra "só calcula quando a operação está completa" fica para o Incremento 7, junto com o campo Status. Detalhes em [incremento-6.md](incremento-6.md).

## 7. Status da operação — ✅ concluído
Campo Status (`EM_ANDAMENTO` / `COMPLETO`, sem restrição de quem pode mudar) e a regra de que os valores calculados só existem quando a operação está "completa". Detalhes em [incremento-7.md](incremento-7.md).

## 8. Front-end: login + CRUD (Cliente, Banco, Usuário) — ✅ concluído
React (Vite) consumindo a API REST. Login por link mágico (com etapa manual de colar o token, já que o SMTP real ainda não está configurado), telas de Cliente, Banco e Usuário. Detalhes em [incremento-8.md](incremento-8.md).

## 9. Docker Compose (backend + frontend + Postgres) — ✅ concluído
`docker compose up -d --build` sobe tudo junto: Postgres real (com volume persistente), backend conectado a ele via variáveis de ambiente, e frontend servido por Nginx. Detalhes em [incremento-9.md](incremento-9.md).

## 10. Tela de Operação — ✅ versão funcional concluída, layout final pendente
Primeira versão da tela, com os campos manuais já confirmados, calculando e exibindo R$/Total Bruto/Comissão, com botão para completar a operação. Layout em duas colunas / agrupamento visual da referência do cliente e os 13 campos novos **continuam bloqueados** pelas pendências #7 e #8 (imagem da tela e os 13 campos novos). Detalhes em [incremento-10.md](incremento-10.md).

## 11. Comissão por fórmula (não mais percentual fixo) — ✅ concluído
Banco passa a ter uma fórmula de comissão (estilo planilha, ex: `N*70%-N*70%*4,65%`) em vez de um percentual simples — reproduz com exatidão os valores reais da planilha original. Detalhes em [incremento-11.md](incremento-11.md).

## 12. Construtor visual de fórmula (arrastar/soltar) — ✅ concluído
Componente de arrastar blocos (`N`, operadores, `%`, parênteses, números) para montar a fórmula de comissão, com teste ao vivo do resultado antes de salvar, e decomposição automática da fórmula existente em blocos ao editar. Detalhes em [incremento-12.md](incremento-12.md).

## 13. Fechamento diário — ✅ concluído
Escopo confirmado e análise completa em [fechamento-diario-analise.md](fechamento-diario-analise.md). Dividido em sub-etapas:
- **13a. Auditoria na Operação (pré-requisito) — ✅ concluído.** Quem criou e quem completou cada operação, registrado automaticamente. Detalhes em [incremento-13a.md](incremento-13a.md).
- **13b. Cálculo do fechamento diário + tela — ✅ concluído.** Resumo operacional, resultado financeiro, quebras por dimensão, posição em aberto — calculado no backend (reaproveitado depois pelo export e pelo job agendado). Detalhes em [incremento-13b.md](incremento-13b.md).
- **13c. Export em PDF e Excel — ✅ concluído.** Reaproveita o cálculo do 13b (Apache POI para Excel, OpenPDF para PDF). Detalhes em [incremento-13c.md](incremento-13c.md).
- **13d. Configuração do horário + job agendado + histórico de fechamentos — ✅ concluído.** Horário configurável pelo Admin, job que gera PDF+Excel automaticamente uma vez por dia, e tela de histórico com download. Detalhes em [incremento-13d.md](incremento-13d.md).

## 14. Relatórios — ✅ concluído
Escopo confirmado e análise completa em [relatorios-analise.md](relatorios-analise.md). Tela interativa com filtros (sem export nesta v1), período pré-definido (hoje/semana/mês/ano), visível para qualquer usuário autenticado (mesma regra do Fechamento Diário). Dividido em sub-etapas:
- **14a. Relatório de Operações filtradas — ✅ concluído.** Lista de operações num período, com filtros por cliente, banco, moeda, C/V, status, quem criou/completou. Detalhes em [incremento-14a.md](incremento-14a.md).
- **14b. Comparativos e tendências — ✅ concluído.** Resultado por dia no período (série temporal), comparação com o período anterior equivalente (com variação percentual), acumulado, média diária, melhor/pior dia. Detalhes em [incremento-14b.md](incremento-14b.md).
- **14c. Rankings por dimensão — ✅ concluído.** Ranking de clientes/bancos/moeda/tipo por comissão líquida, ranking de usuários por operações criadas e por operações completadas. Detalhes em [incremento-14c.md](incremento-14c.md).
- **14d. Posição/exposição em aberto — ✅ concluído.** Operações "em andamento" com envelhecimento (dias em aberto), exposição por cliente e por banco (segmentada por moeda). Detalhes em [incremento-14d.md](incremento-14d.md).

## Mais adiante (fora do escopo próximo, ordem ainda não definida)
- Cálculo de IOF/IR (se voltar a ser necessário)
- Permissões finas por perfil
- Export (PDF/Excel) e geração agendada de Relatórios, se fizer sentido depois de validar a v1
