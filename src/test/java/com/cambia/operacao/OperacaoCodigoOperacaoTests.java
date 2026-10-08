package com.cambia.operacao;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 71: Código da operação é um campo manual digitado na criação/edição —
 * só números. Obrigatório quando o tipo da ordem (PR/CR/VIR) é "Crédito"; opcional
 * pra qualquer outro tipo (pedido do usuário: "código da operação = tipo crédito").
 * Mesmo padrão de validação condicional do Spread emissão (OperacaoSpreadEmissaoTests).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoCodigoOperacaoTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteId;
	private Long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		String clienteBody = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Codigo Operacao\",\"documento\":\"22.222.222/0001-22\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Codigo Operacao\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"997\",\"sigla\":\"CO\",\"nome\":\"CO\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();
	}

	private String operacaoJson(String prCrVir, String codigoOperacaoJson) {
		String fundo = "Pronto".equalsIgnoreCase(prCrVir) ? "P" : "M";
		String spreadEmissao = "Pronto".equalsIgnoreCase(prCrVir) ? "NA" : "0.020";
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"%s","fundo":"%s","spreadEmissao":"%s",
				"codigoOperacao":%s,
				"moeda":"USD","valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, prCrVir, fundo, spreadEmissao, codigoOperacaoJson);
	}

	@Test
	void creditoSemCodigoEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "null")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Crédito")));
	}

	@Test
	void creditoComCodigoEmBrancoEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "\"   \"")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void creditoComCodigoNumericoEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "\"12345\"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigoOperacao").value("12345"));
	}

	@Test
	void prontoSemCodigoEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "null")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigoOperacao").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void prontoComCodigoNumericoEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "\"777\"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigoOperacao").value("777"));
	}

	@Test
	void codigoNaoNumericoEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "\"ABC12\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("números")));
	}

	@Test
	void codigoComMaisDeVinteDigitosEhRejeitado() throws Exception {
		// Limite técnico da coluna (VARCHAR(20), migração V20): sem a validação, o INSERT
		// estourava e o erro virava 500 em vez de um 400 explicável.
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "\"123456789012345678901\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("20")));
	}

	@Test
	void codigoComEspacosEhGuardadoSemEspacos() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "\"  321  \"")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.codigoOperacao").value("321"));
	}

	@Test
	void edicaoTambemValidaAConsistencia() throws Exception {
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "null")))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "null")))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "\"98765\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigoOperacao").value("98765"));
	}

}
