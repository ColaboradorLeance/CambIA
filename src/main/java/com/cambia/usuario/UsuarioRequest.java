package com.cambia.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record UsuarioRequest(
		@NotBlank String nome,
		@NotBlank @Email String email,
		@NotNull Perfil perfil) {
}
