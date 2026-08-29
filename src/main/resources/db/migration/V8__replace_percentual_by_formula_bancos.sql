ALTER TABLE bancos DROP COLUMN percentual_comissao;
ALTER TABLE bancos ADD COLUMN formula_comissao VARCHAR(255) NOT NULL;
