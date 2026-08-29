package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record OperacaoRequest(
		@NotNull LocalDate data,
		String codigoBanco,
		@NotNull Long clienteId,
		@NotNull Long bancoId,
		@NotBlank String cv,
		@NotBlank String prCrVir,
		@NotBlank String moeda,
		@NotNull @Positive BigDecimal valorMe,
		@NotNull @Positive @Digits(integer = 8, fraction = 4) BigDecimal spotAsset,
		@NotNull @Positive @Digits(integer = 8, fraction = 4) BigDecimal nivelamento,
		@NotNull @Positive BigDecimal taxaFinal) {
}
