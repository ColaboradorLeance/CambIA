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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 75: Fundo deixou de ser 100% derivado do tipo da ordem (regra anterior,
 * Incremento 70: "P" pra Pronto, "M" pros demais, rejeitado se não batesse). Regra nova
 * (pedido do usuário): Pronto continua exigindo "P"; qualquer outro tipo aceita QUALQUER
 * LETRA, enviada na requisição. Com isso o valor não é mais derivável e passou a ser
 * persistido e devolvido pela API (campo {@code fundo} no response).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoFundoTests {

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
						.content("{\"nome\":\"Cliente Fundo\",\"documento\":\"44.444.444/0001-44\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Fundo\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"995\",\"sigla\":\"FU\",\"nome\":\"FU\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();
	}

	private String operacaoJson(String prCrVir, String fundo) {
		String spreadEmissao = "Pronto".equalsIgnoreCase(prCrVir) ? "NA" : "0.020";
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"%s","fundo":"%s","spreadEmissao":"%s",
				"codigoOperacao":"555","moeda":"USD","valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, prCrVir, fundo, spreadEmissao);
	}

	@Test
	void prontoComPEhAceitoEDevolvido() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "P")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fundo").value("P"));
	}

	@Test
	void prontoComOutraLetraEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "X")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("Pronto")));
	}

	@Test
	void creditoAceitaQualquerLetra() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "X")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fundo").value("X"));
	}

	@Test
	void virtualAceitaQualquerLetraEPreservaComoVeio() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Virtual", "z")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fundo").value("z"));
	}

	@Test
	void creditoComMaisDeUmaLetraEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "XY")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void creditoComCaractereNaoLetraEhRejeitado() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "1")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void fundoEhPersistidoEContinuaDepoisDeBuscar() throws Exception {
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "K")))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fundo").value("K"));
	}

	@Test
	void edicaoValidaETrocaOFundo() throws Exception {
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "M")))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Credito", "W")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fundo").value("W"));

		mockMvc.perform(put(location)
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson("Pronto", "X")))
				.andExpect(status().isBadRequest());
	}

}
