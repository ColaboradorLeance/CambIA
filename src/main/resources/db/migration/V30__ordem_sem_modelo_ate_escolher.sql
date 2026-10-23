-- Incremento 86: ordem que chega pela API, num banco com mais de um modelo de cálculo
-- ativo, entra SEM modelo — alguém escolhe na plataforma antes de confirmar (regras em
-- docs/decisoes.md, "Ordem via API escolhe o modelo na plataforma").
ALTER TABLE operacoes ALTER COLUMN modelo_calculo_id DROP NOT NULL;
