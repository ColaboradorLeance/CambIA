-- Incremento 85: modelo de cálculo pode ser desativado e reativado. Inativo continua
-- marcado nos bancos e nas ordens que já o usam, mas ordens novas não podem escolhê-lo
-- (regras em docs/decisoes.md, "Ativar e desativar modelo de cálculo").
ALTER TABLE modelos_calculo ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
