-- Incremento 78: bug crítico encontrado no teste de carga de 2026-10-08. O Código da
-- Ordem (id_trade, formato "AAAA-NNNNNN") era gerado como "quantidade de ordens do ano
-- + 1" — duas gravações simultâneas liam a mesma contagem, geravam o mesmo número e
-- uma delas morria na constraint de unicidade (69% de falha com 10 gravações
-- concorrentes). Contagem também colidiria pra sempre se alguma ordem fosse apagada.
--
-- Substituído por um contador por ano incrementado ATOMICAMENTE no próprio banco
-- (INSERT ... ON CONFLICT DO UPDATE ... RETURNING trava só a linha daquele ano pelo
-- instante do incremento). Sequence nativa não serve aqui: a numeração reinicia por
-- ano da operação, não do calendário corrente.
CREATE TABLE id_trade_contadores (
	ano    INTEGER PRIMARY KEY,
	ultimo BIGINT  NOT NULL
);

-- Semeia a partir do MAIOR sequencial já usado em cada ano (não da contagem), pra
-- continuar exatamente de onde a numeração existente parou.
INSERT INTO id_trade_contadores (ano, ultimo)
SELECT split_part(id_trade, '-', 1)::INTEGER, MAX(split_part(id_trade, '-', 2)::BIGINT)
FROM operacoes
WHERE id_trade ~ '^[0-9]{4}-[0-9]+$'
GROUP BY split_part(id_trade, '-', 1);
