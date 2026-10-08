-- Incremento 71: Código da operação é digitado na criação da operação (só números,
-- guardado como texto pra preservar zeros à esquerda, mesmo padrão do Código do Banco).
-- Obrigatório quando o tipo da ordem (PR/CR/VIR) é "Crédito"; opcional nos demais —
-- regra validada no backend (OperacaoService.validarCodigoOperacao), por isso a coluna
-- é nullable: operações existentes (inclusive Crédito criadas antes deste campo existir)
-- ficam sem código até serem editadas.
ALTER TABLE operacoes ADD COLUMN codigo_operacao VARCHAR(20);
