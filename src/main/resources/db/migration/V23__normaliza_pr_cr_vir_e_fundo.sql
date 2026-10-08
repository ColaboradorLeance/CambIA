-- Incremento 77 (acabamento da revisão de 2026-10-08): a normalização de PR/CR/VIR
-- (domínio fechado, canônico sem acento) e do Fundo (maiúscula) valia só pra
-- criações/edições novas — linhas históricas gravadas na era do texto livre podiam
-- continuar com "Crédito" acentuado (que escapa do cálculo de Custo) ou fundo
-- minúsculo (que duplica opções nos filtros das telas). Este backfill normaliza o que
-- é reconhecível; valores irreconhecíveis (se existirem) ficam como estão de propósito
-- — melhor visível e esquisito do que um chute silencioso.
UPDATE operacoes SET fundo = UPPER(fundo) WHERE fundo <> UPPER(fundo);

UPDATE operacoes SET pr_cr_vir = 'Pronto'  WHERE pr_cr_vir <> 'Pronto'  AND lower(pr_cr_vir) = 'pronto';
UPDATE operacoes SET pr_cr_vir = 'Credito' WHERE pr_cr_vir <> 'Credito' AND lower(pr_cr_vir) IN ('credito', 'crédito');
UPDATE operacoes SET pr_cr_vir = 'Virtual' WHERE pr_cr_vir <> 'Virtual' AND lower(pr_cr_vir) = 'virtual';

-- Trava no NÍVEL DO DADO (não só no service): qualquer caminho de escrita futuro
-- (novo service, script de carga, import em lote) fica impedido de reintroduzir
-- valores fora do domínio. NOT VALID: só valida escritas NOVAS — não quebra a
-- migração numa base que porventura tenha lixo histórico irreconhecível.
ALTER TABLE operacoes ADD CONSTRAINT chk_operacoes_pr_cr_vir
	CHECK (pr_cr_vir IN ('Pronto', 'Credito', 'Virtual')) NOT VALID;

ALTER TABLE operacoes ADD CONSTRAINT chk_operacoes_fundo
	CHECK (fundo ~ '^[A-Z]$') NOT VALID;
