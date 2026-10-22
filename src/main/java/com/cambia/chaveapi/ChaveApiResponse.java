package com.cambia.chaveapi;

import java.time.Instant;
import java.time.LocalDate;

/** Visão de listagem: sem segredo e sem hash — só o prefixo identifica a chave. */
record ChaveApiResponse(Long id, String nome, String prefixo, Long usuarioId, String usuarioNome,
		String criadaPorNome, Instant criadaEm, LocalDate expiraEm, Instant ultimoUsoEm, Instant revogadaEm,
		String revogadaPorNome, boolean ativa) {
}
