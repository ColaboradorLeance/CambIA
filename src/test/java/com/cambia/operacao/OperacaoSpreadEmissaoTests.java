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
 * Achado de negócio (Incremento 56): Spread emissão não é calculado — vem no próprio
 * request de criação/edição, e precisa ser consistente com PR/CR/VIR: "NA" quando é
 * "Pronto" (único tipo que a tela de Registrar Operação consegue criar); um número com
 * o tipo da ordem não sendo "Pronto" (Crédito/Virtual, só alcançáveis via API). Sem campo
 * nenhum na tela — a UI sempre manda "NA" por baixo dos panos, já que só cria operações
 * "Pronto" (ver frontend/src/pages/OperacoesPage.jsx).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoSpreadEmissaoTests {

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
						.content("{\"nome\":\"Cliente Spread Emissao\",\"documento\":\"11.111.111/0001-11\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Spread Emissao\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"998\",\"sigla\":\"SE\",\"nome\":\"SE\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();
	}

	private String operacaoJson(String prCrVir, String spreadEmissao) {
		String fundo = "Pronto".equalsIgnoreCase(prCrVir) ? "P" : "M";
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"%s","fundo":"%s","spreadEmissao":"%s",
				"moeda":"USD","valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, prCrVir, fundo, spreadEmissao);
	}

	@Test
	void prontoComNaEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "NA")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.spreadEmissao").value("NA"));
	}

	@Test
	void prontoComNumeroEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "0.020")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Pronto")));
	}

	@Test
	void creditoComNumeroEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "0.020")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.spreadEmissao").value("0.020"));
	}

	@Test
	void virtualComNaEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Virtual", "NA")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void virtualComTextoNaoNumericoEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Virtual", "abc")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void semSpreadEmissaoEhRejeitado() throws Exception {
		String json = """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Pronto","fundo":"P",
				"moeda":"USD","valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);

		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void edicaoTambemValidaAConsistencia() throws Exception {
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "NA")))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "0.020")))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "0.020")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spreadEmissao").value("0.020"));
	}

}
