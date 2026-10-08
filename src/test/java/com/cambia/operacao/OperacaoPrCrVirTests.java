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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 77 (pendência #16): PR/CR/VIR deixou de ser texto livre — o domínio é
 * fechado em Pronto/Credito/Virtual. Variações de caixa e acento ("crédito", "PRONTO")
 * são aceitas e NORMALIZADAS pro valor canônico (sem acento, como o resto do sistema já
 * comparava); qualquer outro valor é rejeitado com 400. Motivo: "Crédito" com acento
 * passava por fora das obrigatoriedades de Crédito (Código da operação/Spot Asset) e
 * calculava Custo 0 — achado da revisão de código de 2026-10-08.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoPrCrVirTests {

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
						.content("{\"nome\":\"Cliente PrCrVir\",\"documento\":\"55.555.555/0001-55\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo PrCrVir\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"994\",\"sigla\":\"PV\",\"nome\":\"PV\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();
	}

	private String operacaoJson(String prCrVir, String fundo, String spreadEmissao, String camposExtras) {
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"%s","fundo":"%s","spreadEmissao":"%s",
				%s"moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, prCrVir, fundo, spreadEmissao, camposExtras);
	}

	@Test
	void tipoForaDoDominioEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Xyz", "M", "0.020", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail",
						org.hamcrest.Matchers.containsString("Pronto")));
	}

	@Test
	void caixaDiferenteEhAceitaENormalizada() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("PRONTO", "P", "NA", "")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prCrVir").value("Pronto"));
	}

	@Test
	void creditoComAcentoEhAceitoENormalizado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Crédito", "M", "0.020",
								"\"codigoOperacao\":\"555\",\"spotAsset\":5.10,")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prCrVir").value("Credito"));
	}

	@Test
	void creditoComAcentoNaoEscapaDasObrigatoriedadesDeCredito() throws Exception {
		// Antes do domínio fechado, "Crédito" (com acento) criava a ordem SEM código da
		// operação e SEM spot asset — exatamente o desvio que motivou esta mudança.
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Crédito", "M", "0.020", "")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void edicaoTambemNormalizaERejeitaForaDoDominio() throws Exception {
		// A edição passa pelos MESMOS validadores da criação — este teste existe pra um
		// refactor que separe os dois caminhos não derrubar a normalização só no PUT.
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "P", "NA", "")))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("crédito", "M", "0.020",
								"\"codigoOperacao\":\"555\",\"spotAsset\":5.10,")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prCrVir").value("Credito"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Xyz", "M", "0.020", "")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void semTipoEhRejeitado() throws Exception {
		String json = """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","fundo":"P","spreadEmissao":"NA",
				"moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

}
