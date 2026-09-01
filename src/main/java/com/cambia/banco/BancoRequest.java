package com.cambia.banco;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

record BancoRequest(
		@NotBlank @Pattern(regexp = "\\d+", message = "Código do banco deve conter somente números") String codigoBanco,
		@NotBlank String sigla,
		@NotBlank String nome,
		@NotNull BigDecimal taxaRebate,
		@NotNull Long calculoId) {
}
