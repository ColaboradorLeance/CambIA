package com.cambia.cliente;

/**
 * {@code nome} vem {@code null} quando o CNPJ não foi encontrado na fonte de dados, ou
 * a consulta falhou por qualquer motivo — nunca um erro HTTP, pra não travar o
 * preenchimento manual do formulário. Ver {@link ConsultaCnpjClient}.
 */
record ConsultaCnpjResponse(String nome) {
}
