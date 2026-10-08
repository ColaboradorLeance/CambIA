-- Incremento 72: Spot Asset saiu da tela de Registrar Operação — passa a entrar somente
-- via API (pedido do usuário). Deixa de ser obrigatório em toda operação: agora é
-- obrigatório só quando PR/CR/VIR é "Crédito" (único caso em que entra numa fórmula
-- confirmada, o Custo) — regra validada no backend (OperacaoService.validarSpotAsset),
-- por isso a coluna vira nullable.
ALTER TABLE operacoes ALTER COLUMN spot_asset DROP NOT NULL;
