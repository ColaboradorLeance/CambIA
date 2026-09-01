package com.cambia.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

	// Achado de revisão de segurança: logout precisa invalidar a sessão no servidor, não só
	// limpar o token no navegador — sem isso, um token vazado continuava válido até a
	// expiração natural (8h) mesmo depois do usuário "sair". Sempre 204, mesmo sem header
	// ou com um token que já não existe/é inválido (idempotente, nunca revela se o token
	// era válido).
	@DeleteMapping("/sessao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void encerrarSessao(@RequestHeader(value = "Authorization", required = false) String authorization) {
		if (authorization != null && authorization.startsWith("Bearer ")) {
			service.encerrarSessao(authorization.substring("Bearer ".length()));
		}
	}

}
