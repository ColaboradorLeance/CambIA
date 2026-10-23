package com.cambia.auth;

import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;
import com.cambia.usuario.UsuarioTestFactory;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TestAuthSupport {

	public static String obterToken(MockMvc mockMvc, UsuarioRepository usuarioRepository,
			MagicLinkTokenRepository magicLinkTokenRepository, Perfil perfil) throws Exception {
		return obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, perfil, "Usuário de Teste");
	}

	public static String obterToken(MockMvc mockMvc, UsuarioRepository usuarioRepository,
			MagicLinkTokenRepository magicLinkTokenRepository, Perfil perfil, String nome) throws Exception {
		return obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, perfil, nome).token();
	}

	/** Sessão de teste com o usuário dela — o Incremento 81 precisa saber quem é o dono da chave. */
	public record Sessao(String token, Usuario usuario) {

		public String bearer() {
			return "Bearer " + token;
		}

	}

	public static Sessao obterSessao(MockMvc mockMvc, UsuarioRepository usuarioRepository,
			MagicLinkTokenRepository magicLinkTokenRepository, Perfil perfil, String nome) throws Exception {
		String email = "teste-" + UUID.randomUUID() + "@cambia.com.br";
		Usuario usuario = usuarioRepository.save(UsuarioTestFactory.novo(nome, email, perfil));

		mockMvc.perform(post("/auth/magic-link")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isAccepted());

		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByUsuarioId(usuario.getId()).orElseThrow();

		String responseBody = mockMvc.perform(get("/auth/verify").param("token", magicLinkToken.getToken()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		return new Sessao(JsonPath.read(responseBody, "$.sessionToken"), usuario);
	}

}
