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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 72: Spot Asset deixou de ser coletado na tela de Registrar Operação —
 * passa a entrar somente via API (pedido do usuário). Com isso, deixou de ser
 * obrigatório em toda criação/edição: agora é obrigatório só quando o tipo da ordem
 * (PR/CR/VIR) é "Crédito" (único caso em que entra numa fórmula confirmada — o Custo),
 * e opcional nos demais. Mesmo padrão condicional do Spread emissão/Fundo/Código da
 * operação.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoSpotAssetTests {

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
						.content("{\"nome\":\"Cliente Spot Asset\",\"documento\":\"33.333.333/0001-33\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Spot Asset\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"996\",\"sigla\":\"SA\",\"nome\":\"SA\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();
	}

	private String operacaoJson(String prCrVir, String spotAssetJson) {
		String fundo = "Pronto".equalsIgnoreCase(prCrVir) ? "P" : "M";
		String spreadEmissao = "Pronto".equalsIgnoreCase(prCrVir) ? "NA" : "0.020";
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"%s","fundo":"%s","spreadEmissao":"%s",
				"codigoOperacao":"555","moeda":"USD","valorMe":1000,"spotAsset":%s,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, prCrVir, fundo, spreadEmissao, spotAssetJson);
	}

	@Test
	void prontoSemSpotAssetEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "null")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.spotAsset").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void virtualSemSpotAssetEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Virtual", "null")))
				.andExpect(status().isCreated());
	}

	@Test
	void creditoSemSpotAssetEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "null")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Crédito")));
	}

	@Test
	void creditoComSpotAssetEhAceito() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "5.1990")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.spotAsset").value(5.1990));
	}

	@Test
	void spotAssetContinuaValidadoQuandoPresente() throws Exception {
		// As regras que já existiam (positivo, até 4 casas decimais) continuam valendo
		// quando o campo vem preenchido — só a obrigatoriedade mudou.
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "-1")))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "5.12345")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void prontoSemSpotAssetConfirmadaCalculaValoresComCustoZero() throws Exception {
		// Garante que a cadeia de cálculo inteira é segura sem Spot Asset: o único uso
		// dele é no Custo, que pra tipos fora de "Crédito" é 0 fixo sem tocar no campo.
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "null")))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.custo").value(0.000))
				.andExpect(jsonPath("$.reais").value(org.hamcrest.Matchers.notNullValue()));
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
						.content(operacaoJson("Credito", "5.1990")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spotAsset").value(5.1990));
	}

}
