CREATE TABLE fechamento_destinatarios (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuarios(id)
);
