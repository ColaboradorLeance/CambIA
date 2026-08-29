package com.cambia.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

	private final AuthService service;

	AuthController(AuthService service) {
		this.service = service;
	}

	@PostMapping("/bootstrap-admin")
	@ResponseStatus(HttpStatus.CREATED)
	UsuarioLogado bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
		return service.bootstrapAdmin(request);
	}

	@PostMapping("/magic-link")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void solicitarLink(@Valid @RequestBody MagicLinkRequest request) {
		service.solicitarLink(request.email());
	}

	@GetMapping("/verify")
	LoginResponse verificar(@RequestParam String token) {
		return service.verificar(token);
	}

}
