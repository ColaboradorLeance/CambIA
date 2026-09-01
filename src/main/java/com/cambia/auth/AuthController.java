package com.cambia.auth;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
class AuthController {

	private static final String MENSAGEM_LIMITE_EXCEDIDO = "Muitas tentativas. Aguarde alguns minutos antes de tentar de novo.";

	private final AuthService service;
	private final LimitadorDeRequisicoes limitador;

	AuthController(AuthService service, LimitadorDeRequisicoes limitador) {
		this.service = service;
		this.limitador = limitador;
	}

	@PostMapping("/bootstrap-admin")
	@ResponseStatus(HttpStatus.CREATED)
	UsuarioLogado bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
		return service.bootstrapAdmin(request);
	}

	// Achado de revisão de segurança: sem limite, esse endpoint podia ser usado pra
	// "bombardear" o e-mail de alguém com links, ou pra tentativas repetidas de
	// enumeração. Limite por e-mail (não por IP) — é o alvo de verdade sendo protegido.
	@PostMapping("/magic-link")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void solicitarLink(@Valid @RequestBody MagicLinkRequest request) {
		if (!limitador.permitir("magic-link:" + request.email().toLowerCase())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MENSAGEM_LIMITE_EXCEDIDO);
		}
		service.solicitarLink(request.email());
	}

	// Achado de revisão de segurança: sem limite, nada impedia tentativas repetidas de
	// adivinhar um token válido. Limite por IP de origem (não por token — o token muda a
	// cada tentativa, então não serviria de chave).
	@GetMapping("/verify")
	LoginResponse verificar(@RequestParam String token, HttpServletRequest requisicao) {
		if (!limitador.permitir("verify:" + requisicao.getRemoteAddr())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MENSAGEM_LIMITE_EXCEDIDO);
		}
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
