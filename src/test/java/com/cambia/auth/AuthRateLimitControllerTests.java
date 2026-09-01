package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;
import com.cambia.usuario.UsuarioTestFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o limite de tentativas de verdade, ponta a ponta — por isso fica numa classe
 * separada, com um limite bem menor que o padrão de produção (via @TestPropertySource,
 * só nesta classe) em vez do valor bem alto usado no resto da suíte
 * (src/test/resources/application.properties).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
		"cambia.rate-limit.auth.maximo=3",
		"cambia.rate-limit.auth.janela-minutos=15"
})
class AuthRateLimitControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Test
	void bloqueiaAPartirDaQuartaTentativaDeMagicLinkParaOMesmoEmail() throws Exception {
		String email = "alvo-rate-limit@cambia.com.br";
		usuarioRepository.save(UsuarioTestFactory.novo("Alvo", email, Perfil.ANALISTA));
		String corpo = "{\"email\":\"" + email + "\"}";

		mockMvc.perform(post("/auth/magic-link").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isAccepted());
		mockMvc.perform(post("/auth/magic-link").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isAccepted());
		mockMvc.perform(post("/auth/magic-link").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isAccepted());

		mockMvc.perform(post("/auth/magic-link").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void naoAfetaUmEmailDiferenteMesmoDepoisDeEstourarOLimiteDoOutro() throws Exception {
		String email = "spam-alvo@cambia.com.br";
		usuarioRepository.save(UsuarioTestFactory.novo("Alvo do Spam", email, Perfil.ANALISTA));
		String corpo = "{\"email\":\"" + email + "\"}";
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(post("/auth/magic-link").contentType(MediaType.APPLICATION_JSON).content(corpo))
					.andExpect(status().isAccepted());
		}
		mockMvc.perform(post("/auth/magic-link").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isTooManyRequests());

		String outroEmail = "nao-e-alvo@cambia.com.br";
		usuarioRepository.save(UsuarioTestFactory.novo("Outra Pessoa", outroEmail, Perfil.ANALISTA));
		mockMvc.perform(post("/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + outroEmail + "\"}"))
				.andExpect(status().isAccepted());
	}

	@Test
	void bloqueiaAPartirDaQuartaTentativaDeVerify() throws Exception {
		Usuario usuario = usuarioRepository.save(
				UsuarioTestFactory.novo("Usuário Verify", "verify-rate-limit@cambia.com.br", Perfil.ANALISTA));

		for (int i = 0; i < 3; i++) {
			MagicLinkToken token = magicLinkTokenRepository.save(
					new MagicLinkToken(usuario.getId(), UUID.randomUUID().toString(), Instant.now().plusSeconds(900)));
			mockMvc.perform(get("/auth/verify").param("token", token.getToken()))
					.andExpect(status().isOk());
		}

		MagicLinkToken quartoToken = magicLinkTokenRepository.save(
				new MagicLinkToken(usuario.getId(), UUID.randomUUID().toString(), Instant.now().plusSeconds(900)));
		mockMvc.perform(get("/auth/verify").param("token", quartoToken.getToken()))
				.andExpect(status().isTooManyRequests());
	}

}
