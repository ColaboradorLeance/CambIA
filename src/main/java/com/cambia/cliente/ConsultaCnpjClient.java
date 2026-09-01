package com.cambia.cliente;

import java.util.Optional;

/**
 * Consulta o nome (razão social) de uma empresa a partir do CNPJ, numa fonte pública
 * de dados. Usado só para preencher automaticamente o campo Nome no cadastro de
 * Cliente — nunca falha de forma visível pro usuário: se o CNPJ não existe, ou a
 * fonte de dados está fora do ar, o método simplesmente retorna vazio (o usuário
 * continua podendo digitar o nome manualmente).
 */
interface ConsultaCnpjClient {

	Optional<String> buscarNome(String cnpjSomenteDigitos);

}
