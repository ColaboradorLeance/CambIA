package com.cambia.chaveapi;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Única resposta que carrega o segredo (D10) — devolvida só na criação, nunca de novo. */
record ChaveApiCriadaResponse(Long id, String nome, String prefixo, String chave, List<String> escopos,
		Instant criadaEm, LocalDate expiraEm) {
}
