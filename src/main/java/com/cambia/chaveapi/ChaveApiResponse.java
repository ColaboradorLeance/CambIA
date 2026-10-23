package com.cambia.chaveapi;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chave na tela do Admin (todas as chaves, K7): com o dono e quem criou/revogou. Sem segredo
 * e sem hash. {@code ativa} fica por compatibilidade com a tela do Inc. 79.
 */
record ChaveApiResponse(Long id, String nome, String prefixo, List<String> escopos, Long donoId, String donoNome,
		String criadaPorNome, Instant criadaEm, LocalDate expiraEm, Instant ultimoUsoEm, Instant revogadaEm,
		String revogadaPorNome, boolean ativa, StatusChaveApi status) {
}
