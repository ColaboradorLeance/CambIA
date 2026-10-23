package com.cambia.banco;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

record BancoRequest(
		@NotBlank @Pattern(regexp = "\\d+", message = "Código do banco deve conter somente números") String codigoBanco,
		@NotBlank String sigla,
		@NotBlank String nome,
		@NotNull BigDecimal taxaRebate,
		// Incremento 84: um ou mais modelos de cálculo. "calculoId" (um só) continua aceito
		// por compatibilidade com quem já chamava a API assim; os dois juntos somam.
		Long calculoId,
		List<Long> calculoIds) {

	/** Ids informados, sem repetição, na ordem em que vieram. */
	List<Long> calculosInformados() {
		Set<Long> ids = new LinkedHashSet<>();
		if (calculoId != null) {
			ids.add(calculoId);
		}
		if (calculoIds != null) {
			calculoIds.stream().filter(java.util.Objects::nonNull).forEach(ids::add);
		}
		return List.copyOf(ids);
	}

}
