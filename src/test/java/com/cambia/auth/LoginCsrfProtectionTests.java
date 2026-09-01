package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;
import com.cambia.usuario.UsuarioTestFactory;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achado de revisão de segurança ("olhar de hacker ético", Médio-Alto): "login CSRF" em
 * {@code GET /auth/verify}. Sem essa proteção, um atacante conseguia pedir seu próprio
 * link mágico, pegar o token e induzir a vítima a completá-lo (clicando num link ou
 * colando um código repassado por engenharia social) — a vítima acabava autenticada NA
 * CONTA DO ATACANTE sem perceber, e qualquer dado sensível que digitasse depois (ex.:
 * cadastro de cliente, operação) ficava visível pro atacante ao voltar pra própria conta.
 *
 * <p>Correção: {@code POST /auth/magic-link} grava um cookie httpOnly com um nonce
 * ("vínculo") — mas só quando a requisição vem de verdade por HTTPS (via
 * {@code X-Forwarded-Proto: https}, o sinal que o reverse-proxy real grava — ver
 * {@code reverse-proxy/nginx.conf}); {@code GET /auth/verify} exige esse mesmo vínculo de
 * volta. Como o cookie é httpOnly e nunca é exposto no link/código em si, só o navegador
 * que de fato pediu o link tem como completá-lo — o link do atacante para de funcionar no
 * navegador da vítima.
 *
 * <p>Fora de HTTPS (sem {@code X-Forwarded-Proto}, o caso do resto desta suíte via
 * MockMvc e do dev local sem o reverse-proxy), nenhum cookie é exigido — comportamento
 * idêntico ao de antes desta correção, coberto implicitamente por toda a suíte existente
 * (nenhum teste precisou mudar por causa desta classe).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginCsrfProtectionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private MockHttpServletResponse pedirLinkComoSeFosseHttps(String email) throws Exception {
		usuarioRepository.save(UsuarioTestFactory.novo("Usuário HTTPS", email, Perfil.ADMIN));
		return mockMvc.perform(comoSeFosseHttps(post("/auth/magic-link"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse();
	}

	private MockHttpServletRequestBuilder comoSeFosseHttps(MockHttpServletRequestBuilder builder) {
		return builder.header("X-Forwarded-Proto", "https");
	}

	@Test
	void magicLinkPorHttpsGravaCookieDeVinculoHttpOnlySecure() throws Exception {
		MockHttpServletResponse resposta = pedirLinkComoSeFosseHttps("vinculo-cookie@cambia.com.br");

		Cookie cookieVinculo = resposta.getCookie(NomesCookieAuth.VINCULO_LOGIN);
		assertTrue(cookieVinculo != null, "esperava um cookie " + NomesCookieAuth.VINCULO_LOGIN);
		assertTrue(cookieVinculo.isHttpOnly());
		assertTrue(cookieVinculo.getSecure());
		assertTrue(cookieVinculo.getValue() != null && !cookieVinculo.getValue().isBlank());
	}

	@Test
	void magicLinkForaDeHttpsNaoGravaCookieDeVinculo() throws Exception {
		String email = "sem-https@cambia.com.br";
		usuarioRepository.save(UsuarioTestFactory.novo("Usuário HTTP", email, Perfil.ADMIN));

		MockHttpServletResponse resposta = mockMvc.perform(post("/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted())
				.andReturn().getResponse();

		assertTrue(resposta.getCookie(NomesCookieAuth.VINCULO_LOGIN) == null);
	}

	@Test
	void verifyComOMesmoCookieDeVinculoFunciona() throws Exception {
		String email = "vinculo-correto@cambia.com.br";
		MockHttpServletResponse respostaLink = pedirLinkComoSeFosseHttps(email);
		Cookie cookieVinculo = respostaLink.getCookie(NomesCookieAuth.VINCULO_LOGIN);
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(
				usuarioRepository.findByEmail(email).orElseThrow().getId()).orElseThrow();

		mockMvc.perform(comoSeFosseHttps(get("/auth/verify"))
						.param("token", magicLinkToken.getToken())
						.cookie(cookieVinculo))
				.andExpect(status().isOk());
	}

	// O cenário do ataque de verdade: o atacante pediu o link (e por isso tem o token
	// válido), mas quem está completando o verify agora é o navegador da VÍTIMA — que
	// nunca chamou /auth/magic-link, então não tem (e não pode forjar, é httpOnly) o
	// cookie de vínculo do atacante.
	@Test
	void verifySemOCookieDeVinculoEhRejeitadoMesmoComTokenValido() throws Exception {
		String email = "vitima-sem-cookie@cambia.com.br";
		pedirLinkComoSeFosseHttps(email);
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(
				usuarioRepository.findByEmail(email).orElseThrow().getId()).orElseThrow();

		mockMvc.perform(comoSeFosseHttps(get("/auth/verify"))
						.param("token", magicLinkToken.getToken()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void verifyComCookieDeVinculoErradoEhRejeitado() throws Exception {
		String email = "vinculo-errado@cambia.com.br";
		pedirLinkComoSeFosseHttps(email);
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(
				usuarioRepository.findByEmail(email).orElseThrow().getId()).orElseThrow();

		mockMvc.perform(comoSeFosseHttps(get("/auth/verify"))
						.param("token", magicLinkToken.getToken())
						.cookie(new Cookie(NomesCookieAuth.VINCULO_LOGIN, "valor-que-nao-bate")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void verifyForaDeHttpsContinuaFuncionandoSemVinculoNenhum() throws Exception {
		// Mesmo fluxo usado no resto da suíte inteira (TestAuthSupport, dev local sem o
		// reverse-proxy): sem X-Forwarded-Proto, magic-link não grava vínculo, e verify
		// não deveria exigir nenhum — comportamento idêntico ao de antes desta correção.
		String email = "sem-https-verify@cambia.com.br";
		Usuario usuario = usuarioRepository.save(UsuarioTestFactory.novo("Usuário HTTP", email, Perfil.ADMIN));
		mockMvc.perform(post("/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"));
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();

		mockMvc.perform(get("/auth/verify").param("token", magicLinkToken.getToken()))
				.andExpect(status().isOk());
	}

	@Test
	void cookieDeVinculoEhLimpoDepoisDeUmVerifyBemSucedido() throws Exception {
		String email = "limpa-vinculo@cambia.com.br";
		MockHttpServletResponse respostaLink = pedirLinkComoSeFosseHttps(email);
		Cookie cookieVinculo = respostaLink.getCookie(NomesCookieAuth.VINCULO_LOGIN);
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(
				usuarioRepository.findByEmail(email).orElseThrow().getId()).orElseThrow();

		MockHttpServletResponse respostaVerify = mockMvc.perform(comoSeFosseHttps(get("/auth/verify"))
						.param("token", magicLinkToken.getToken())
						.cookie(cookieVinculo))
				.andExpect(status().isOk())
				.andReturn().getResponse();

		Cookie cookieLimpo = respostaVerify.getCookie(NomesCookieAuth.VINCULO_LOGIN);
		assertFalse(cookieLimpo == null, "esperava o cookie de vínculo sendo limpo na resposta");
		assertTrue(cookieLimpo.getMaxAge() == 0);
	}

}
