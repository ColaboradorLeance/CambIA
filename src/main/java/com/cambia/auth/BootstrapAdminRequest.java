package com.cambia.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record BootstrapAdminRequest(@NotBlank String nome, @NotBlank @Email String email) {
}
