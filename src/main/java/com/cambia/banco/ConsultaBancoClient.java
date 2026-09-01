package com.cambia.banco;

import java.util.Optional;

/**
 * Consulta o nome completo de um banco a partir do código COMPE (3 dígitos) ou do
 * ISPB (8 dígitos), numa fonte pública de dados mantida sincronizada com o Banco
 * Central. Usado só para preencher automaticamente o campo Nome no cadastro de
 * Banco (reaproveitando o campo "Código do banco" já existente) — nunca falha de
 * forma visível: se o código não existe (banco não participa do COMPE, ou ISPB
 * desconhecido), ou a fonte de dados está fora do ar, o método retorna vazio (o
 * usuário continua podendo digitar o nome manualmente).
 */
interface ConsultaBancoClient {

	Optional<String> buscarNomePorCompe(String codigoCompe);

	Optional<String> buscarNomePorIspb(String ispb);

}
