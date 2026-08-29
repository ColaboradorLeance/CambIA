package com.cambia.banco;

import jakarta.validation.constraints.NotBlank;

record CalculoRequest(
		@NotBlank String nome,
		@NotBlank String formula) {
}
