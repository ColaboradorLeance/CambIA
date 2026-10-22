package com.cambia.chaveapi;

import java.time.Instant;
import java.time.LocalDate;

/** Única resposta que carrega o segredo (D10) — devolvida só no POST, nunca de novo. */
record ChaveApiCriadaResponse(Long id, String nome, String prefixo, String chave, Long usuarioId,
		String usuarioNome, Instant criadaEm, LocalDate expiraEm) {
}
