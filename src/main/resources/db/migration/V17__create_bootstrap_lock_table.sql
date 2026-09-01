-- Achado de revisão de segurança: POST /auth/bootstrap-admin fazia um "count(*) > 0,
-- depois insere" que não é atômico — duas requisições concorrentes no instante em que o
-- sistema ainda não tem nenhum usuário podiam, ambas, passar pela checagem e criar um
-- Admin cada uma (condição de corrida). Uma tabela de uma linha só, com chave primária
-- fixa, resolve isso: só uma requisição consegue inserir a linha (id=1); a outra recebe
-- uma violação de chave primária e é tratada como 409 — sem depender de timing.
CREATE TABLE bootstrap_lock (
    id INTEGER PRIMARY KEY
);
