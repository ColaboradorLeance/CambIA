-- Incremento 81: chaves de API por usuário (ver docs/chaves-api-usuario-plano.md, decisões
-- R1–R3 e K1–K11 em docs/decisoes.md). Cada usuário cria as próprias chaves; usuario_id
-- passa a significar o DONO (para as chaves novas, igual a criada_por_usuario_id).

-- K4: escopos explícitos, separados por vírgula ("LEITURA", "CRIAR_ORDENS"). Nenhum escopo
-- edita, confirma ou cancela ordem (R1). K8: as chaves já existentes (Inc. 79) recebem os
-- dois, para a integração de criação de ordens continuar funcionando.
ALTER TABLE chaves_api ADD COLUMN escopos VARCHAR(100);
UPDATE chaves_api SET escopos = 'LEITURA,CRIAR_ORDENS';
ALTER TABLE chaves_api ALTER COLUMN escopos SET NOT NULL;
ALTER TABLE chaves_api ADD CONSTRAINT chk_chaves_api_escopos
    CHECK (escopos ~ '^(LEITURA|CRIAR_ORDENS)(,(LEITURA|CRIAR_ORDENS))?$');

-- K5: expiração obrigatória. K8: a chave antiga sem data ganha 1 ano a partir da migração,
-- contado no dia de São Paulo (a expiração é uma data de negócio, como no Inc. 79).
UPDATE chaves_api
    SET expira_em = ((CURRENT_TIMESTAMP AT TIME ZONE 'America/Sao_Paulo')::date + INTERVAL '1 year')::date
    WHERE expira_em IS NULL;
ALTER TABLE chaves_api ALTER COLUMN expira_em SET NOT NULL;

-- K9: o evento da ordem guarda qual chave foi usada (NULL = sessão de tela).
ALTER TABLE operacoes_eventos ADD COLUMN chave_api_id BIGINT REFERENCES chaves_api(id);
