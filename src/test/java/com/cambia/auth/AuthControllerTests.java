package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private SessaoRepository sessaoRepository;

	private Usuario criarUsuario(String email) {
		return usuarioRepository.save(com.cambia.usuario.UsuarioTestFactory.novo("Ana Silva", email, Perfil.ANALISTA));
	}

	@Test
	void solicitaLinkELogaComSucesso() throws Exception {
		Usuario usuario = criarUsuario("ana@cambia.com.br");

		mockMvc.perform(post("/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"ana@cambia.com.br\"}"))
				.andExpect(status().isAccepted());

		MagicLinkToken token = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();

		mockMvc.perform(get("/auth/verify").param("token", token.getToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionToken").exists())
				.andExpect(jsonPath("$.usuario.email").value("ana@cambia.com.br"));
	}

	@Test
	void solicitarLinkParaEmailNaoCadastradoNaoCriaToken() throws Exception {
		long antes = magicLinkTokenRepository.count();

		mockMvc.perform(post("/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"naoexiste@cambia.com.br\"}"))
				.andExpect(status().isAccepted());

		org.junit.jupiter.api.Assertions.assertEquals(antes, magicLinkTokenRepository.count());
	}

	@Test
	void rejeitaTokenInexistente() throws Exception {
		mockMvc.perform(get("/auth/verify").param("token", "token-que-nao-existe"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaTokenExpirado() throws Exception {
		Usuario usuario = criarUsuario("bia@cambia.com.br");
		MagicLinkToken expirado = new MagicLinkToken(usuario.getId(), UUID.randomUUID().toString(),
				Instant.now().minusSeconds(60));
		magicLinkTokenRepository.save(expirado);

		mockMvc.perform(get("/auth/verify").param("token", expirado.getToken()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rejeitaTokenJaUsado() throws Exception {
		Usuario usuario = criarUsuario("carlos@cambia.com.br");

		mockMvc.perform(post("/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"carlos@cambia.com.br\"}"));

		MagicLinkToken token = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();

		mockMvc.perform(get("/auth/verify").param("token", token.getToken()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/auth/verify").param("token", token.getToken()))
				.andExpect(status().isUnauthorized());
	}

	// Achado de revisão de segurança: logout precisa invalidar a sessão no servidor, não
	// só limpar o token no navegador — senão um token vazado continua válido até expirar.
	@Test
	void logoutInvalidaASessaoNoServidor() throws Exception {
		Usuario usuario = criarUsuario("dora@cambia.com.br");
		mockMvc.perform(post("/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"dora@cambia.com.br\"}"));
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();

		String resposta = mockMvc.perform(get("/auth/verify").param("token", magicLinkToken.getToken()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String sessionToken = JsonPath.read(resposta, "$.sessionToken");

		org.junit.jupiter.api.Assertions.assertTrue(sessaoRepository.findByToken(sessionToken).isPresent());

		mockMvc.perform(delete("/auth/sessao").header("Authorization", "Bearer " + sessionToken))
				.andExpect(status().isNoContent());

		org.junit.jupiter.api.Assertions.assertTrue(sessaoRepository.findByToken(sessionToken).isEmpty());

		// o mesmo token não autentica mais depois do logout
		mockMvc.perform(get("/operacoes").header("Authorization", "Bearer " + sessionToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutSemTokenNaoQuebra() throws Exception {
		mockMvc.perform(delete("/auth/sessao"))
				.andExpect(status().isNoContent());
	}

	@Test
	void logoutComTokenInexistenteAindaAssimRespondeNoContent() throws Exception {
		mockMvc.perform(delete("/auth/sessao").header("Authorization", "Bearer token-que-nao-existe"))
				.andExpect(status().isNoContent());
	}

}
