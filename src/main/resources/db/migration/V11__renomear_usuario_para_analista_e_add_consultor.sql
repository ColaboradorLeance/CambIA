-- Perfil "USUARIO" passa a se chamar "ANALISTA". Um novo perfil "CONSULTOR"
-- (somente leitura) também passa a existir, mas não precisa de migração de dados
-- porque nenhum usuário tinha esse perfil antes de ele existir.
UPDATE usuarios SET perfil = 'ANALISTA' WHERE perfil = 'USUARIO';
