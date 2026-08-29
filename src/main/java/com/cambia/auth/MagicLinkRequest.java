package com.cambia.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record MagicLinkRequest(@NotBlank @Email String email) {
}
