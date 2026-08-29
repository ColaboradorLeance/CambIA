package com.cambia.banco;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record TesteFormulaRequest(
		@NotBlank String formula,
		@NotNull BigDecimal totalBrutoCambioExemplo,
		@NotNull BigDecimal taxaRebateExemplo) {
}
