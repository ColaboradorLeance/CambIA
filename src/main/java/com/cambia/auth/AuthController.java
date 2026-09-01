package com.cambia.auth;

import java.time.Duration;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
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

// Achado de revisão de segurança (Fase 3B): além de devolver o token no corpo (usado no
// dev local, sem HTTPS — ver README), /auth/verify agora TAMBÉM grava a sessão num cookie
// httpOnly. Em produção (atrás do reverse-proxy HTTPS da Fase 3A), o front-end para de
// guardar o token em localStorage e passa a confiar só no cookie — um XSS futuro não
// consegue mais roubar a sessão lendo JavaScript, já que o cookie não é legível por
// script nenhum. O corpo da resposta continua tendo o token só pra não quebrar quem
// roda em dev sem o reverse-proxy (HTTP puro, cookie Secure não seria aceito).
@RestController
@RequestMapping("/auth")
class AuthController {

	private static final String MENSAGEM_LIMITE_EXCEDIDO = "Muitas tentativas. Aguarde alguns minutos antes de tentar de novo.";
	private static final Duration VALIDADE_COOKIE_SESSAO = Duration.ofHours(8); // mesmo valor de AuthService
	private static final Duration VALIDADE_COOKIE_VINCULO = Duration.ofMinutes(15); // mesmo valor de AuthService

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
	//
	// Achado de revisão de segurança ("login CSRF" em /auth/verify): grava um cookie de
	// vínculo (nonce httpOnly) só quando a requisição vem por HTTPS de verdade (atrás do
	// reverse-proxy — ver usaCookieDeVinculo). Em dev local sem HTTPS, sem cookie, sem
	// vínculo exigido depois — comportamento igual ao de antes desta correção.
	@PostMapping("/magic-link")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void solicitarLink(@Valid @RequestBody MagicLinkRequest request, HttpServletRequest requisicao,
			HttpServletResponse resposta) {
		if (!limitador.permitir("magic-link:" + request.email().toLowerCase())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MENSAGEM_LIMITE_EXCEDIDO);
		}
		String vinculo = usaCookieDeVinculo(requisicao) ? UUID.randomUUID().toString() : null;
		service.solicitarLink(request.email(), vinculo);
		if (vinculo != null) {
			definirCookieDeVinculo(resposta, vinculo);
		}
	}

	// Achado de revisão de segurança: sem limite, nada impedia tentativas repetidas de
	// adivinhar um token válido. Limite por IP de origem (não por token — o token muda a
	// cada tentativa, então não serviria de chave).
	//
	// Achado de revisão de segurança ("login CSRF"): sem o cookie de vínculo (gravado em
	// /auth/magic-link, ver acima), um atacante conseguia pedir seu próprio link e induzir
	// a vítima a completá-lo, autenticando a vítima NA CONTA DO ATACANTE sem perceber.
	// vinculoDoCookie precisa bater com o gravado junto do token — ver
	// AuthService.verificar/MagicLinkToken.vinculoCompativel.
	@GetMapping("/verify")
	LoginResponse verificar(@RequestParam String token, HttpServletRequest requisicao,
			HttpServletResponse resposta,
			@CookieValue(value = NomesCookieAuth.VINCULO_LOGIN, required = false) String vinculoDoCookie) {
		if (!limitador.permitir("verify:" + ResolvedorDeIpReal.resolver(requisicao))) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MENSAGEM_LIMITE_EXCEDIDO);
		}
		LoginResponse loginResponse = service.verificar(token, vinculoDoCookie);
		definirCookiesDeSessao(resposta, loginResponse.sessionToken());
		limparCookieDeVinculo(resposta);
		return loginResponse;
	}

	// Achado de revisão de segurança: logout precisa invalidar a sessão no servidor, não só
	// limpar o token no navegador — sem isso, um token vazado continuava válido até a
	// expiração natural (8h) mesmo depois do usuário "sair". Sempre 204, mesmo sem
	// header/cookie ou com um token que já não existe/é inválido (idempotente, nunca
	// revela se o token era válido). Aceita o token tanto pelo header (dev, sem
	// reverse-proxy) quanto pelo cookie (produção) — o mesmo esquema duplo do login.
	@DeleteMapping("/sessao")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void encerrarSessao(@RequestHeader(value = "Authorization", required = false) String authorization,
			@CookieValue(value = NomesCookieAuth.SESSAO, required = false) String tokenDoCookie,
			HttpServletResponse resposta) {
		String token = authorization != null && authorization.startsWith("Bearer ")
				? authorization.substring("Bearer ".length())
				: tokenDoCookie;
		if (token != null) {
			service.encerrarSessao(token);
		}
		limparCookiesDeSessao(resposta);
	}

	private void definirCookiesDeSessao(HttpServletResponse resposta, String sessionToken) {
		resposta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NomesCookieAuth.SESSAO, sessionToken)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/")
				.maxAge(VALIDADE_COOKIE_SESSAO)
				.build()
				.toString());
		// Cookie do dublê CSRF: precisa ser legível por JavaScript (por isso httpOnly(false))
		// — o valor em si não é secreto, só serve pra provar que quem está mandando a
		// requisição consegue ler cookies desta origem (o que um site de terceiros não
		// consegue). Ver CsrfProtectionFilter.
		resposta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NomesCookieAuth.CSRF, UUID.randomUUID().toString())
				.httpOnly(false)
				.secure(true)
				.sameSite("Strict")
				.path("/")
				.maxAge(VALIDADE_COOKIE_SESSAO)
				.build()
				.toString());
	}

	// Achado de revisão de segurança ("olhar de hacker ético" — inconsistência de baixo
	// risco): usava a API antiga (jakarta.servlet.http.Cookie) em vez do mesmo
	// ResponseCookie usado pra GRAVAR esses cookies (ver definirCookiesDeSessao acima) —
	// funcionava nos testes/curl, mas não replicava Secure/SameSite do cookie original;
	// um navegador que leve esses atributos em conta ao decidir se apaga o cookie
	// existente poderia, em tese, não reconhecer como o mesmo cookie. Reescrito pra usar
	// o mesmo builder e os mesmos atributos, só com Max-Age=0 (expira imediatamente).
	private void limparCookiesDeSessao(HttpServletResponse resposta) {
		resposta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NomesCookieAuth.SESSAO, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/")
				.maxAge(Duration.ZERO)
				.build()
				.toString());
		resposta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NomesCookieAuth.CSRF, "")
				.httpOnly(false)
				.secure(true)
				.sameSite("Strict")
				.path("/")
				.maxAge(Duration.ZERO)
				.build()
				.toString());
	}

	// Achado de revisão de segurança ("login CSRF"): o cookie de vínculo só faz sentido —
	// e só é aceito pelo navegador — quando a conexão de verdade (cliente-reverse-proxy) é
	// HTTPS; marcá-lo Secure incondicionalmente (mesmo padrão dos outros cookies desta
	// classe) faria o navegador simplesmente descartá-lo em dev local sem HTTPS, e nesse
	// caso o vínculo NÃO deve ser exigido (senão quebraria o login em dev, que depende só
	// do header Authorization — ver README). O backend nunca termina TLS ele mesmo (isso é
	// feito pelo reverse-proxy — Fase 3A), então request.isSecure() sempre vem falso
	// mesmo em produção; o sinal de verdade é o X-Forwarded-Proto que o próprio
	// reverse-proxy grava (reverse-proxy/nginx.conf) — confiável porque o backend nunca é
	// alcançável direto de fora do Docker Compose (portas não publicadas, Fase 3A).
	private boolean usaCookieDeVinculo(HttpServletRequest requisicao) {
		return requisicao.isSecure() || "https".equalsIgnoreCase(requisicao.getHeader("X-Forwarded-Proto"));
	}

	private void definirCookieDeVinculo(HttpServletResponse resposta, String vinculo) {
		resposta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NomesCookieAuth.VINCULO_LOGIN, vinculo)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/auth")
				.maxAge(VALIDADE_COOKIE_VINCULO)
				.build()
				.toString());
	}

	// Mesmo motivo de limparCookiesDeSessao acima: usa o mesmo builder e os mesmos
	// atributos de definirCookieDeVinculo, só com Max-Age=0.
	private void limparCookieDeVinculo(HttpServletResponse resposta) {
		resposta.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NomesCookieAuth.VINCULO_LOGIN, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path("/auth")
				.maxAge(Duration.ZERO)
				.build()
				.toString());
	}

}
