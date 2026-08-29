package com.cambia.usuario;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;

	private static final String USUARIO_JSON = """
			{"nome":"Ana Silva","email":"ana.silva@cambia.com.br","perfil":"ANALISTA"}
			""";

	@BeforeEach
	void autenticarComoAdmin() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;
	}

	@Test
	void criaEBuscaUsuario() throws Exception {
		String location = mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(USUARIO_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.nome").value("Ana Silva"))
				.andExpect(jsonPath("$.email").value("ana.silva@cambia.com.br"))
				.andExpect(jsonPath("$.perfil").value("ANALISTA"))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Ana Silva"));
	}

	@Test
	void listaUsuariosCadastrados() throws Exception {
		mockMvc.perform(post("/usuarios")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(USUARIO_JSON));

		mockMvc.perform(get("/usuarios").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[?(@.email=='ana.silva@cambia.com.br')]").exists());
	}

	@Test
	void atualizaUsuario() throws Exception {
		String location = mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(USUARIO_JSON))
				.andReturn().getResponse().getHeader("Location");

		String atualizado = """
				{"nome":"Ana Silva","email":"ana.silva@cambia.com.br","perfil":"ADMIN"}
				""";

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(atualizado))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.perfil").value("ADMIN"));
	}

	@Test
	void removeUsuario() throws Exception {
		String location = mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(USUARIO_JSON))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(delete(location).header("Authorization", authHeader))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void retorna404ParaUsuarioInexistente() throws Exception {
		mockMvc.perform(get("/usuarios/999999").header("Authorization", authHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejeitaCadastroSemNome() throws Exception {
		String semNome = """
				{"nome":"","email":"ana.silva@cambia.com.br","perfil":"ANALISTA"}
				""";

		mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(semNome))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaEmailInvalido() throws Exception {
		String emailInvalido = """
				{"nome":"Ana Silva","email":"nao-e-email","perfil":"ANALISTA"}
				""";

		mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(emailInvalido))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaPerfilInvalido() throws Exception {
		String perfilInvalido = """
				{"nome":"Ana Silva","email":"ana.silva@cambia.com.br","perfil":"GERENTE"}
				""";

		mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(perfilInvalido))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaEmailDuplicado() throws Exception {
		mockMvc.perform(post("/usuarios")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(USUARIO_JSON));

		mockMvc.perform(post("/usuarios")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(USUARIO_JSON))
				.andExpect(status().isConflict());
	}

}
