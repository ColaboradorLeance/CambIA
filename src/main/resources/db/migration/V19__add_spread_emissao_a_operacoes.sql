-- Achado de negócio (Incremento 56): Spread emissão é um valor ENVIADO na criação da
-- operação (não calculado por fórmula, ao contrário da Spread liquidação do Incremento
-- 55) — mesmo padrão de C/V e PR/CR/VIR: texto livre, guarda "NA" (sentinela) ou um número
-- em formato de texto (ex: "0.020"). Obrigatório (NOT NULL); operações já existentes
-- (todas criadas antes desta coluna existir, sempre PR/CR/VIR = Pronto pela UI) recebem
-- "NA" no backfill.
ALTER TABLE operacoes ADD COLUMN spread_emissao VARCHAR(20) NOT NULL DEFAULT 'NA';
