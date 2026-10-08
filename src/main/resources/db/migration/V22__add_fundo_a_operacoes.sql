-- Incremento 75: Fundo deixou de ser 100% derivado do tipo da ordem. Regra nova (pedido
-- do usuário): Pronto continua exigindo "P"; qualquer outro tipo aceita QUALQUER LETRA,
-- enviada na requisição. Como o valor não é mais derivável, passa a ser persistido.
-- Backfill com a derivação antiga (Incremento 70), que era o único valor possível até
-- aqui: "P" pra Pronto, "M" pros demais.
ALTER TABLE operacoes ADD COLUMN fundo VARCHAR(1);
UPDATE operacoes SET fundo = CASE WHEN lower(pr_cr_vir) = 'pronto' THEN 'P' ELSE 'M' END;
ALTER TABLE operacoes ALTER COLUMN fundo SET NOT NULL;
