ALTER TABLE operacoes ADD COLUMN criado_por_usuario_id BIGINT NOT NULL REFERENCES usuarios(id);
ALTER TABLE operacoes ADD COLUMN criado_em TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE operacoes ADD COLUMN completado_por_usuario_id BIGINT REFERENCES usuarios(id);
ALTER TABLE operacoes ADD COLUMN completado_em TIMESTAMP;
