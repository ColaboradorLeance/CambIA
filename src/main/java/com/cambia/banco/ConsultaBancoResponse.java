package com.cambia.banco;

/**
 * {@code nome} vem {@code null} quando o código não corresponde a um COMPE (3
 * dígitos) nem a um ISPB (8 dígitos) válido, ou a consulta não encontrou nada —
 * nunca um erro HTTP, pra não travar o preenchimento manual do formulário. Ver
 * {@link ConsultaBancoClient}.
 */
record ConsultaBancoResponse(String nome) {
}
