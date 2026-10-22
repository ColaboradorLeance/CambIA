-- Incremento 80 (desempenho, achado do teste de carga de 2026-10-08): a tabela operacoes
-- só tinha a chave primária e o índice único de id_trade. Toda consulta por status, data,
-- cliente ou banco era varredura completa — irrelevante com dezenas de ordens, linear
-- (e crescente pra sempre) com o histórico real.

-- Aba "Confirmadas" paginada: filtra por status e ordena pela conclusão mais recente.
-- Esta ordem de colunas atende WHERE status = ? ORDER BY completado_em DESC, id DESC
-- direto pelo índice, sem ordenar em memória — o LIMIT da página para cedo. Também serve
-- "Em andamento" e a Posição em aberto (WHERE status = 'ANDAMENTO').
CREATE INDEX idx_operacoes_status_completado_em ON operacoes (status, completado_em DESC, id DESC);

-- Relatórios (data BETWEEN), Fechamento Diário (data = ?) e Comparativos (um dia por vez).
CREATE INDEX idx_operacoes_data ON operacoes (data);

-- Filtros por cliente/banco/usuário (resolvidos em ids antes da consulta principal) e as
-- chaves estrangeiras — sem índice, remover um cliente/banco/usuário varre operacoes inteira.
CREATE INDEX idx_operacoes_cliente_id ON operacoes (cliente_id);
CREATE INDEX idx_operacoes_banco_id ON operacoes (banco_id);
CREATE INDEX idx_operacoes_criado_por ON operacoes (criado_por_usuario_id);
CREATE INDEX idx_operacoes_completado_por ON operacoes (completado_por_usuario_id);
