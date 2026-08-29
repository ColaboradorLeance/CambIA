package com.cambia.banco;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record BancoRequest(
		@NotBlank String codigoBanco,
		@NotBlank String sigla,
		@NotBlank String nome,
		@NotNull BigDecimal taxaRebate,
		@NotNull Long calculoId) {
}
