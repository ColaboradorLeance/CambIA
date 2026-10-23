-- Incremento 84: um banco pode ter mais de um modelo de cálculo, e a ordem passa a guardar
-- qual deles a Comissão Líquida usa (regras em docs/decisoes.md, "Banco com mais de um
-- modelo de cálculo").

-- 1) Relação N:N banco ↔ modelo de cálculo, semeada com o cálculo único que cada banco tem hoje.
CREATE TABLE banco_calculos (
    banco_id BIGINT NOT NULL REFERENCES bancos(id) ON DELETE CASCADE,
    modelo_calculo_id BIGINT NOT NULL REFERENCES modelos_calculo(id),
    PRIMARY KEY (banco_id, modelo_calculo_id)
);
-- "este cálculo está em uso por algum banco?" (bloqueio de remoção do modelo)
CREATE INDEX idx_banco_calculos_modelo ON banco_calculos (modelo_calculo_id);

INSERT INTO banco_calculos (banco_id, modelo_calculo_id)
SELECT id, modelo_calculo_id FROM bancos;

-- 2) A ordem guarda o cálculo escolhido. Ordens existentes ficam com o cálculo que o banco
-- delas tem hoje (decisão do PO): nenhuma comissão já calculada muda com esta migração.
ALTER TABLE operacoes ADD COLUMN modelo_calculo_id BIGINT REFERENCES modelos_calculo(id);

UPDATE operacoes o
SET modelo_calculo_id = b.modelo_calculo_id
FROM bancos b
WHERE b.id = o.banco_id;

ALTER TABLE operacoes ALTER COLUMN modelo_calculo_id SET NOT NULL;
-- "este cálculo está em uso por alguma ordem?" (bloqueio de remoção do modelo)
CREATE INDEX idx_operacoes_modelo_calculo ON operacoes (modelo_calculo_id);

-- 3) A coluna única do banco sai: a fonte da verdade agora é banco_calculos.
ALTER TABLE bancos DROP COLUMN modelo_calculo_id;
