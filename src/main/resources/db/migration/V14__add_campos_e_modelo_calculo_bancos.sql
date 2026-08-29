ALTER TABLE bancos ADD COLUMN codigo_banco VARCHAR(20);
ALTER TABLE bancos ADD COLUMN sigla VARCHAR(20);
ALTER TABLE bancos ADD COLUMN taxa_rebate NUMERIC(9,4);
ALTER TABLE bancos ADD COLUMN modelo_calculo_id BIGINT REFERENCES modelos_calculo(id);

UPDATE bancos SET
    codigo_banco = COALESCE(codigo_banco, 'N/D'),
    sigla = COALESCE(sigla, UPPER(LEFT(nome, 10))),
    taxa_rebate = COALESCE(taxa_rebate, 0);

UPDATE bancos b
SET modelo_calculo_id = (SELECT m.id FROM modelos_calculo m WHERE m.formula = b.formula_comissao)
WHERE modelo_calculo_id IS NULL;

ALTER TABLE bancos ALTER COLUMN codigo_banco SET NOT NULL;
ALTER TABLE bancos ALTER COLUMN sigla SET NOT NULL;
ALTER TABLE bancos ALTER COLUMN taxa_rebate SET NOT NULL;
ALTER TABLE bancos ALTER COLUMN modelo_calculo_id SET NOT NULL;
ALTER TABLE bancos DROP COLUMN formula_comissao;
