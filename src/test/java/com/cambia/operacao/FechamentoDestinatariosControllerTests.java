package com.cambia.operacao;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
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
class FechamentoDestinatariosControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String adminAuth;
	private String analistaAuth;

	@BeforeEach
	void preparar() throws Exception {
		String adminToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ADMIN, "Admin");
		adminAuth = "Bearer " + adminToken;

		String analistaToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ANALISTA, "Analista");
		analistaAuth = "Bearer " + analistaToken;
	}

	@Test
	void semDestinatariosCadastradosRetornaListaVazia() throws Exception {
		mockMvc.perform(get("/fechamentos/destinatarios").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void adminDefineDestinatariosEDepoisConsultaAListaAtualizada() throws Exception {
		Long adminId = usuarioPorNome("Admin").getId();
		Long analistaId = usuarioPorNome("Analista").getId();

		mockMvc.perform(put("/fechamentos/destinatarios")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"usuarioIds\":[%d,%d]}".formatted(adminId, analistaId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		mockMvc.perform(get("/fechamentos/destinatarios").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.nome=='Admin')]").exists())
				.andExpect(jsonPath("$[?(@.nome=='Analista')]").exists());

		// substituir a lista (só o admin) — a anterior deve ser totalmente trocada, não somada
		mockMvc.perform(put("/fechamentos/destinatarios")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"usuarioIds\":[%d]}".formatted(adminId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].nome").value("Admin"));
	}

	@Test
	void usuarioInexistenteRetorna400() throws Exception {
		mockMvc.perform(put("/fechamentos/destinatarios")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"usuarioIds\":[999999]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void analistaConsultaMasNaoAltera() throws Exception {
		mockMvc.perform(get("/fechamentos/destinatarios").header("Authorization", analistaAuth))
				.andExpect(status().isOk());

		mockMvc.perform(put("/fechamentos/destinatarios")
						.header("Authorization", analistaAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"usuarioIds\":[]}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void consultorNaoAcessa() throws Exception {
		String consultorToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.CONSULTOR);
		String consultorAuth = "Bearer " + consultorToken;

		mockMvc.perform(get("/fechamentos/destinatarios").header("Authorization", consultorAuth))
				.andExpect(status().isForbidden());
	}

	private com.cambia.usuario.Usuario usuarioPorNome(String nome) {
		return usuarioRepository.findAll().stream()
				.filter(u -> u.getNome().equals(nome))
				.findFirst()
				.orElseThrow();
	}

}
