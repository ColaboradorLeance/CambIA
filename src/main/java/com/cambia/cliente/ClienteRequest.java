package com.cambia.cliente;

import jakarta.validation.constraints.NotBlank;

record ClienteRequest(
		@NotBlank String nome,
		@NotBlank String documento) {
}
