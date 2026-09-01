package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioTestFactory;
import com.cambia.usuario.UsuarioRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achado de revisão de segurança (Fase 3B): a sessão passou a viver também num cookie
 * httpOnly (além do header Authorization, mantido pro dev local sem o reverse-proxy
 * HTTPS — ver README). Esses testes cobrem o esquema novo ponta a ponta: cookies gravados
 * com os atributos certos no login, autenticação por cookie funcionando sem o header, e a
 * proteção CSRF (dublê de cookie) exigida só quando não há header Authorization.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CookieSessionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private MockHttpServletResponse logar(String email) throws Exception {
		Usuario usuario = usuarioRepository.save(UsuarioTestFactory.novo("Usuário Cookie", email, Perfil.ADMIN));
		mockMvc.perform(post("/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"));
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();

		return mockMvc.perform(get("/auth/verify").param("token", magicLinkToken.getToken()))
				.andExpect(status().isOk())
				.andReturn().getResponse();
	}

	@Test
	void verifyGravaCookieDeSessaoHttpOnlySecureSameSiteStrict() throws Exception {
		MockHttpServletResponse resposta = logar("cookie-httponly@cambia.com.br");

		Cookie cookieSessao = resposta.getCookie(NomesCookieAuth.SESSAO);
		assertTrue(cookieSessao != null, "esperava um cookie " + NomesCookieAuth.SESSAO);
		assertTrue(cookieSessao.isHttpOnly());
		assertTrue(cookieSessao.getSecure());
		assertTrue(cookieSessao.getValue() != null && !cookieSessao.getValue().isBlank());

		String setCookieBruto = resposta.getHeaders("Set-Cookie").stream()
				.filter(h -> h.startsWith(NomesCookieAuth.SESSAO + "="))
				.findFirst().orElseThrow();
		assertTrue(setCookieBruto.contains("SameSite=Strict"));
	}

	@Test
	void verifyGravaCookieCsrfLegivelPorJavascript() throws Exception {
		MockHttpServletResponse resposta = logar("cookie-csrf@cambia.com.br");

		Cookie cookieCsrf = resposta.getCookie(NomesCookieAuth.CSRF);
		assertTrue(cookieCsrf != null, "esperava um cookie " + NomesCookieAuth.CSRF);
		assertFalse(cookieCsrf.isHttpOnly(), "o cookie CSRF precisa ser legível por JavaScript");
	}

	@Test
	void autenticaPorCookieSemPrecisarDoHeaderAuthorization() throws Exception {
		MockHttpServletResponse resposta = logar("cookie-auth@cambia.com.br");
		Cookie cookieSessao = resposta.getCookie(NomesCookieAuth.SESSAO);

		mockMvc.perform(get("/operacoes").cookie(cookieSessao))
				.andExpect(status().isOk());
	}

	@Test
	void semCookieNemHeaderContinuaDevolvendo401() throws Exception {
		mockMvc.perform(get("/operacoes"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutLimpaOsDoisCookies() throws Exception {
		MockHttpServletResponse loginResp = logar("cookie-logout@cambia.com.br");
		Cookie cookieSessao = loginResp.getCookie(NomesCookieAuth.SESSAO);
		Cookie cookieCsrf = loginResp.getCookie(NomesCookieAuth.CSRF);

		MockHttpServletResponse logoutResp = mockMvc.perform(delete("/auth/sessao")
						.cookie(cookieSessao, cookieCsrf)
						.header("X-XSRF-TOKEN", cookieCsrf.getValue()))
				.andExpect(status().isNoContent())
				.andReturn().getResponse();

		assertTrue(logoutResp.getCookie(NomesCookieAuth.SESSAO).getMaxAge() == 0);
		assertTrue(logoutResp.getCookie(NomesCookieAuth.CSRF).getMaxAge() == 0);

		// o cookie de sessão (já "expirado" pelo Max-Age=0) não autentica mais
		mockMvc.perform(get("/operacoes").cookie(new Cookie(NomesCookieAuth.SESSAO, cookieSessao.getValue())))
				.andExpect(status().isUnauthorized());
	}

	// --- CSRF (dublê de cookie) ---

	@Test
	void requisicaoViaCookieSemHeaderCsrfEhRejeitada() throws Exception {
		MockHttpServletResponse loginResp = logar("csrf-sem-header@cambia.com.br");
		Cookie cookieSessao = loginResp.getCookie(NomesCookieAuth.SESSAO);

		mockMvc.perform(post("/clientes")
						.cookie(cookieSessao)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente CSRF\",\"documento\":\"11.111.111/0001-11\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void requisicaoViaCookieComHeaderCsrfErradoEhRejeitada() throws Exception {
		MockHttpServletResponse loginResp = logar("csrf-errado@cambia.com.br");
		Cookie cookieSessao = loginResp.getCookie(NomesCookieAuth.SESSAO);

		mockMvc.perform(post("/clientes")
						.cookie(cookieSessao)
						.header("X-XSRF-TOKEN", "valor-que-nao-bate-com-o-cookie")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente CSRF\",\"documento\":\"11.111.111/0001-11\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void requisicaoViaCookieComHeaderCsrfCorretoFunciona() throws Exception {
		MockHttpServletResponse loginResp = logar("csrf-correto@cambia.com.br");
		Cookie cookieSessao = loginResp.getCookie(NomesCookieAuth.SESSAO);
		Cookie cookieCsrf = loginResp.getCookie(NomesCookieAuth.CSRF);

		mockMvc.perform(post("/clientes")
						.cookie(cookieSessao, cookieCsrf)
						.header("X-XSRF-TOKEN", cookieCsrf.getValue())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente CSRF\",\"documento\":\"11.111.111/0001-11\"}"))
				.andExpect(status().isCreated());
	}

	// Achado de revisão de segurança ("olhar de hacker ético" — não explorável hoje, mas
	// frágil): CsrfProtectionFilter isentava CSRF só por existir QUALQUER valor no header
	// Authorization, mesmo sem o prefixo "Bearer " — nesse caso, SessaoAuthenticationFilter
	// nunca autentica de fato por esse header (só cai pro cookie de sessão), então CSRF
	// era pulado justamente na hora em que a credencial em jogo era o cookie, a peça que
	// ele existe pra proteger. Corrigido com AutorizacaoHeader, compartilhado pelos dois
	// filtros.
	@Test
	void requisicaoComHeaderAuthorizationMalFormadoAindaExigeCsrfMesmoTendoCookieDeSessao() throws Exception {
		MockHttpServletResponse loginResp = logar("csrf-header-mal-formado@cambia.com.br");
		Cookie cookieSessao = loginResp.getCookie(NomesCookieAuth.SESSAO);

		mockMvc.perform(post("/clientes")
						.cookie(cookieSessao)
						.header("Authorization", "qualquer-coisa-sem-o-prefixo-bearer")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente CSRF\",\"documento\":\"11.111.111/0001-11\"}"))
				.andExpect(status().isForbidden());
	}

	// Garante que a correção acima não "super-corrigiu": um header Authorization Bearer
	// de verdade continua isentando CSRF mesmo quando a requisição também carrega (por
	// acidente ou não) um cookie de sessão — SessaoAuthenticationFilter usa o header nesse
	// caso, nunca o cookie, então não há credencial ambiente sendo explorada.
	@Test
	void requisicaoComHeaderAuthorizationBearerValidoContinuaImuneAoCsrfMesmoComCookieDeSessaoPresente()
			throws Exception {
		MockHttpServletResponse loginResp = logar("csrf-header-e-cookie@cambia.com.br");
		Cookie cookieSessao = loginResp.getCookie(NomesCookieAuth.SESSAO);
		String sessionToken = com.jayway.jsonpath.JsonPath.read(loginResp.getContentAsString(), "$.sessionToken");

		mockMvc.perform(post("/clientes")
						.cookie(cookieSessao)
						.header("Authorization", "Bearer " + sessionToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente CSRF\",\"documento\":\"22.222.222/0001-22\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void requisicaoComHeaderAuthorizationNaoPrecisaDeCsrf() throws Exception {
		// O esquema original (header Bearer, sem cookie) continua imune ao CSRF por
		// natureza — um site de terceiros não tem como forjar esse header. Nenhum teste
		// existente no resto da suíte precisou mudar por causa da proteção CSRF nova.
		Usuario usuario = usuarioRepository.save(
				UsuarioTestFactory.novo("Usuário Header", "sem-cookie@cambia.com.br", Perfil.ADMIN));
		mockMvc.perform(post("/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"sem-cookie@cambia.com.br\"}"));
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();
		String corpo = mockMvc.perform(get("/auth/verify").param("token", magicLinkToken.getToken()))
				.andReturn().getResponse().getContentAsString();
		String sessionToken = com.jayway.jsonpath.JsonPath.read(corpo, "$.sessionToken");

		mockMvc.perform(post("/clientes")
						.header("Authorization", "Bearer " + sessionToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Sem Cookie\",\"documento\":\"22.222.222/0001-22\"}"))
				.andExpect(status().isCreated());
	}

}
