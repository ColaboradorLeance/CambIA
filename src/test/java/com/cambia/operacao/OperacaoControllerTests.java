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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String usuarioAuthHeader;
	private Long clienteId;
	private Long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		String adminToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		String adminAuthHeader = "Bearer " + adminToken;

		String clienteBody = mockMvc.perform(post("/clientes")
						.header("Authorization", adminAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"CRAS Agroindustria LTDA\",\"documento\":\"14.777.639/0001-92\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", adminAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"TLX\",\"formula\":\"N*70%-N*70%*4,65%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", adminAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"TLX\",\"sigla\":\"TLX\",\"nome\":\"TLX\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();

		String usuarioToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA);
		usuarioAuthHeader = "Bearer " + usuarioToken;
	}

	private String operacaoJson() {
		return """
				{"data":"2026-07-02","codigoBanco":"143258","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","valorMe":885242.40,"spotAsset":5.1990,"nivelamento":5.1960,"taxaFinal":5.1856}
				""".formatted(clienteId, bancoId);
	}

	private String criarOperacao(String json) throws Exception {
		return mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
	}

	@Test
	void criaEBuscaOperacao() throws Exception {
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.idTrade").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{6}")))
				.andExpect(jsonPath("$.clienteId").value(clienteId))
				.andExpect(jsonPath("$.bancoId").value(bancoId))
				.andExpect(jsonPath("$.cv").value("V"))
				.andExpect(jsonPath("$.moeda").value("USD"))
				.andExpect(jsonPath("$.valorMe").value(885242.40))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location).header("Authorization", usuarioAuthHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.codigoBanco").value("143258"));
	}

	@Test
	void registraQuemCriouEQuemCompletouAOperacao() throws Exception {
		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criadoPorNome").value("Usuário de Teste"))
				.andExpect(jsonPath("$.criadoEm").exists())
				.andExpect(jsonPath("$.completadoPorNome").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.completadoEm").value(org.hamcrest.Matchers.nullValue()))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.completadoPorNome").value("Usuário de Teste"))
				.andExpect(jsonPath("$.completadoEm").exists());
	}

	@Test
	void idTradeEhSequencialDentroDoAno() throws Exception {
		String r1 = mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson()))
				.andReturn().getResponse().getContentAsString();
		String r2 = mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson()))
				.andReturn().getResponse().getContentAsString();

		String idTrade1 = JsonPath.read(r1, "$.idTrade");
		String idTrade2 = JsonPath.read(r2, "$.idTrade");

		org.junit.jupiter.api.Assertions.assertNotEquals(idTrade1, idTrade2);
		org.junit.jupiter.api.Assertions.assertTrue(idTrade1.startsWith("2026-"));
		org.junit.jupiter.api.Assertions.assertTrue(idTrade2.startsWith("2026-"));
	}

	@Test
	void listaOperacoesCadastradas() throws Exception {
		mockMvc.perform(post("/operacoes")
				.header("Authorization", usuarioAuthHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content(operacaoJson()));

		mockMvc.perform(get("/operacoes").header("Authorization", usuarioAuthHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$[?(@.moeda=='USD')]").exists());
	}

	@Test
	void retorna404ParaOperacaoInexistente() throws Exception {
		mockMvc.perform(get("/operacoes/999999").header("Authorization", usuarioAuthHeader))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejeitaClienteInexistente() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":999999,"bancoId":%d,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","valorMe":100.00,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.10}
				""".formatted(bancoId);

		mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaBancoInexistente() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":999999,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","valorMe":100.00,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.10}
				""".formatted(clienteId);

		mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaSemValorEmMe() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.10}
				""".formatted(clienteId, bancoId);

		mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejeitaSpotAssetComMaisDeQuatroCasasDecimais() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","valorMe":100.00,"spotAsset":5.19999,"nivelamento":5.10,"taxaFinal":5.10}
				""".formatted(clienteId, bancoId);

		// O corpo do erro precisa dizer qual campo falhou e por quê (não só "400 Bad Request"),
		// senão o front-end não tem o que mostrar num pop-up de erro pro usuário.
		mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("spotAsset")));
	}

	@Test
	void rejeitaNivelamentoComMaisDeQuatroCasasDecimais() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","valorMe":100.00,"spotAsset":5.10,"nivelamento":5.19999,"taxaFinal":5.10}
				""".formatted(clienteId, bancoId);

		mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aceitaSpotAssetENivelamentoComExatamenteQuatroCasasDecimais() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito",
				"moeda":"USD","valorMe":100.00,"spotAsset":5.1999,"nivelamento":5.1234,"taxaFinal":5.10}
				""".formatted(clienteId, bancoId);

		mockMvc.perform(post("/operacoes")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated());
	}

	@Test
	void novaOperacaoComecaEmAndamentoSemValoresCalculados() throws Exception {
		String location = criarOperacao(operacaoJson());

		mockMvc.perform(get(location).header("Authorization", usuarioAuthHeader))
				.andExpect(jsonPath("$.status").value("ANDAMENTO"))
				.andExpect(jsonPath("$.reais").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.totalBrutoCambio").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.comissaoLiquida").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.valorAbsoluto").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void completarOperacaoPassaACalcularOsValores() throws Exception {
		String location = criarOperacao(operacaoJson());

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMADO"))
				.andExpect(jsonPath("$.reais").value(4590512.99))
				.andExpect(jsonPath("$.totalBrutoCambio").value(9206.52))
				.andExpect(jsonPath("$.comissaoLiquida").value(6144.89))
				.andExpect(jsonPath("$.valorAbsoluto").value(9206.52));

		mockMvc.perform(get(location).header("Authorization", usuarioAuthHeader))
				.andExpect(jsonPath("$.status").value("CONFIRMADO"))
				.andExpect(jsonPath("$.reais").value(4590512.99));
	}

	@Test
	void completarOperacaoComCvDesconhecidoCalculaSoOReais() throws Exception {
		String json = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":%d,"cv":"NA","prCrVir":"Virtual",
				"moeda":"USD","valorMe":1000.00,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);
		String location = criarOperacao(json);

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reais").value(5000.00))
				.andExpect(jsonPath("$.totalBrutoCambio").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.comissaoLiquida").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.valorAbsoluto").value(100.00));
	}

	@Test
	void retorna404AoAtualizarStatusDeOperacaoInexistente() throws Exception {
		mockMvc.perform(patch("/operacoes/999999/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejeitaStatusInvalido() throws Exception {
		String location = criarOperacao(operacaoJson());

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"XPTO\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void cancelarOperacaoEmAndamentoNaoCalculaValores() throws Exception {
		String location = criarOperacao(operacaoJson());

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CANCELADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELADO"))
				.andExpect(jsonPath("$.reais").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.totalBrutoCambio").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.comissaoLiquida").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.valorAbsoluto").value(org.hamcrest.Matchers.nullValue()));

		mockMvc.perform(get(location).header("Authorization", usuarioAuthHeader))
				.andExpect(jsonPath("$.status").value("CANCELADO"));
	}

	@Test
	void naoPermiteConfirmarOuCancelarOperacaoQueJaSaiuDeAndamento() throws Exception {
		String location = criarOperacao(operacaoJson());

		mockMvc.perform(patch(location + "/status")
				.header("Authorization", usuarioAuthHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CANCELADO\"}"));

		// já cancelada: nem confirmar nem cancelar de novo é permitido
		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isConflict());

		mockMvc.perform(patch(location + "/status")
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CANCELADO\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void naoPermiteEditarOperacaoCancelada() throws Exception {
		String location = criarOperacao(operacaoJson());

		mockMvc.perform(patch(location + "/status")
				.header("Authorization", usuarioAuthHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CANCELADO\"}"));

		mockMvc.perform(put(location)
						.header("Authorization", usuarioAuthHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(operacaoJson()))
				.andExpect(status().isConflict());
	}

}
