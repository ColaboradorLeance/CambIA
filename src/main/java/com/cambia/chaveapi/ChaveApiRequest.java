package com.cambia.chaveapi;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ChaveApiRequest(
		@NotBlank @Size(max = 120) String nome,
		@NotNull Long usuarioId,
		// Opcional (D4): ausente = a chave não expira.
		LocalDate expiraEm) {
}
