package com.cambia.operacao;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
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
class FechamentoConfiguracaoTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Test
	void configuracaoComecaVaziaEAdminConseguerDefinirHorario() throws Exception {
		String adminToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		String adminAuth = "Bearer " + adminToken;

		mockMvc.perform(get("/fechamentos/configuracao").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.horaExecucao").value(org.hamcrest.Matchers.nullValue()));

		mockMvc.perform(put("/fechamentos/configuracao")
						.header("Authorization", adminAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"horaExecucao\":\"18:00:00\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.horaExecucao").value("18:00:00"));

		mockMvc.perform(get("/fechamentos/configuracao").header("Authorization", adminAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.horaExecucao").value("18:00:00"));
	}

	@Test
	void usuarioComumNaoPodeAlterarConfiguracaoMasPodeConsultar() throws Exception {
		String usuarioToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA);
		String usuarioAuth = "Bearer " + usuarioToken;

		mockMvc.perform(get("/fechamentos/configuracao").header("Authorization", usuarioAuth))
				.andExpect(status().isOk());

		mockMvc.perform(put("/fechamentos/configuracao")
						.header("Authorization", usuarioAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"horaExecucao\":\"18:00:00\"}"))
				.andExpect(status().isForbidden());
	}

}
