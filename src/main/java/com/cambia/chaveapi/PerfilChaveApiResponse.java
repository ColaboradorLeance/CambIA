package com.cambia.chaveapi;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Chave na tela "Meu perfil": sem segredo e sem hash — só o prefixo identifica a chave. */
record PerfilChaveApiResponse(Long id, String nome, String prefixo, List<String> escopos, Instant criadaEm,
		LocalDate expiraEm, Instant ultimoUsoEm, Instant revogadaEm, StatusChaveApi status) {
}
