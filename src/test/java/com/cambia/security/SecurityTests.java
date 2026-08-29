package com.cambia.security;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private Long criarCalculoComoAdmin() throws Exception {
		String adminToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		String body = mockMvc.perform(post("/calculos")
						.header("Authorization", "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Seguranca\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	void semSessaoRetorna401() throws Exception {
		mockMvc.perform(get("/clientes")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/bancos")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
	}

	@Test
	void sessaoInvalidaRetorna401() throws Exception {
		mockMvc.perform(get("/clientes").header("Authorization", "Bearer token-invalido"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void perfilAnalistaNaoAcessaUsuarios() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA);

		mockMvc.perform(get("/usuarios").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void perfilAnalistaGerenciaClientesBancosECalculos() throws Exception {
		Long calculoId = criarCalculoComoAdmin();
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA);

		mockMvc.perform(get("/clientes").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/bancos").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/calculos").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(post("/clientes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"X\",\"documento\":\"123\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/bancos")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"001\",\"sigla\":\"X\",\"nome\":\"X\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/calculos")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Outro modelo\",\"formula\":\"N*40%\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void perfilAnalistaAcessaRelatoriosEFechamento() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA);

		mockMvc.perform(get("/relatorios/operacoes").param("periodo", "HOJE").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/fechamentos/configuracao").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(put("/fechamentos/configuracao")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"horaExecucao\":\"18:00:00\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void perfilConsultorSoAcessaOperacoes() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.CONSULTOR);

		mockMvc.perform(get("/operacoes").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/operacoes/historico").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/clientes").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/bancos").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/calculos").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/usuarios").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/relatorios/operacoes").param("periodo", "HOJE").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/fechamentos/configuracao").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/clientes")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"X\",\"documento\":\"123\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void perfilAdminAcessaTudo() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);

		mockMvc.perform(get("/clientes").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/bancos").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/calculos").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/usuarios").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/relatorios/operacoes").param("periodo", "HOJE").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(get("/fechamentos/configuracao").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void endpointsPublicosContinuamAbertos() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
