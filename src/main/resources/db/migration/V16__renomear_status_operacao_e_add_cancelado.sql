-- Status da Operação renomeado a pedido do usuário: EM_ANDAMENTO -> ANDAMENTO,
-- COMPLETO -> CONFIRMADO, e um novo status CANCELADO passa a existir (só alcançável a
-- partir de ANDAMENTO; ANDAMENTO/CONFIRMADO/CANCELADO são os únicos três valores válidos).
UPDATE operacoes SET status = 'ANDAMENTO' WHERE status = 'EM_ANDAMENTO';
UPDATE operacoes SET status = 'CONFIRMADO' WHERE status = 'COMPLETO';

-- O tipo de evento de auditoria "COMPLETADA" acompanha o mesmo renome (agora CONFIRMADA),
-- e ganha "CANCELADA" para registrar quando uma ordem é cancelada.
UPDATE operacoes_eventos SET tipo = 'CONFIRMADA' WHERE tipo = 'COMPLETADA';
