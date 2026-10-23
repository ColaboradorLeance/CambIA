package com.cambia.banco;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 85: modelo de cálculo pode ser desativado e reativado. Decisões do PO em
 * docs/decisoes.md ("Ativar e desativar modelo de cálculo"): o banco continua com ele
 * marcado, mas ordens novas não podem escolhê-lo; banco sem cálculo ativo recusa ordem
 * nova; ordens que já usam o cálculo continuam com ele.
 *
 * <p>Números: venda de 1.000 com Nivelamento 5,10 e Taxa Final 5,00 → Total Bruto 100,00;
 * cálculo A (N*50%) dá comissão 50,00 e cálculo B (N*70%) dá 70,00.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CalculoAtivacaoTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String auth;
	private Long clienteId;
	private Long calculoA;
	private Long calculoB;

	@BeforeEach
	void preparar() throws Exception {
		auth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		clienteId = criar("/clientes", "{\"nome\":\"Cliente Ativacao\",\"documento\":\"31.222.333/0001-55\"}");
		calculoA = criar("/calculos", "{\"nome\":\"A Metade\",\"formula\":\"N*50%\"}");
		calculoB = criar("/calculos", "{\"nome\":\"B Setenta\",\"formula\":\"N*70%\"}");
	}

	private Long criar(String caminho, String json) throws Exception {
		String corpo = mockMvc.perform(post(caminho)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	private static String banco(String codigo, String calculos) {
		return "{\"codigoBanco\":\"%s\",\"sigla\":\"A%s\",\"nome\":\"Banco %s\",\"taxaRebate\":0,\"calculoIds\":[%s]}"
				.formatted(codigo, codigo, codigo, calculos);
	}

	private ResultActions ativar(Long calculoId, boolean ativo) throws Exception {
		return mockMvc.perform(patch("/calculos/" + calculoId + "/ativo")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ativo\":%s}".formatted(ativo)));
	}

	private static String ordem(Long clienteId, Long bancoId, Long calculoId) {
		String calculo = calculoId == null ? "" : "\"calculoId\":%d,".formatted(calculoId);
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,%s"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, calculo);
	}

	private ResultActions postarOrdem(Long bancoId, Long calculoId) throws Exception {
		return mockMvc.perform(post("/operacoes")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ordem(clienteId, bancoId, calculoId)));
	}

	private Long id(ResultActions resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void calculoNasceAtivo() throws Exception {
		mockMvc.perform(get("/calculos/" + calculoA).header("Authorization", auth))
				.andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void desativaEReativa() throws Exception {
		ativar(calculoA, false).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
		mockMvc.perform(get("/calculos/" + calculoA).header("Authorization", auth))
				.andExpect(jsonPath("$.ativo").value(false));

		ativar(calculoA, true).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void exigeOCampoAtivo() throws Exception {
		mockMvc.perform(patch("/calculos/" + calculoA + "/ativo")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void bancoContinuaComOCalculoDesativadoMarcado() throws Exception {
		Long bancoId = criar("/bancos", banco("971", calculoA + "," + calculoB));
		ativar(calculoB, false).andExpect(status().isOk());

		mockMvc.perform(get("/bancos/" + bancoId).header("Authorization", auth))
				.andExpect(jsonPath("$.calculos.length()").value(2))
				.andExpect(jsonPath("$.calculos[0].ativo").value(true))
				.andExpect(jsonPath("$.calculos[1].id").value(calculoB))
				.andExpect(jsonPath("$.calculos[1].ativo").value(false));
	}

	@Test
	void ordemNovaNaoPodeEscolherCalculoInativo() throws Exception {
		Long bancoId = criar("/bancos", banco("972", calculoA + "," + calculoB));
		ativar(calculoB, false).andExpect(status().isOk());

		postarOrdem(bancoId, calculoB)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("inativo")));
	}

	@Test
	void bancoComUmSoCalculoAtivoUsaEleSemPrecisarInformar() throws Exception {
		// Dois marcados, um inativo: para a ordem, o banco tem um cálculo só.
		Long bancoId = criar("/bancos", banco("973", calculoA + "," + calculoB));
		ativar(calculoA, false).andExpect(status().isOk());

		postarOrdem(bancoId, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(calculoB));
	}

	@Test
	void bancoSemCalculoAtivoRecusaOrdemNova() throws Exception {
		Long bancoId = criar("/bancos", banco("974", String.valueOf(calculoA)));
		ativar(calculoA, false).andExpect(status().isOk());

		postarOrdem(bancoId, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("ativo")));
	}

	@Test
	void ordemQueJaUsaContinuaComOCalculoDesativado() throws Exception {
		Long bancoId = criar("/bancos", banco("975", calculoA + "," + calculoB));
		Long ordemId = id(postarOrdem(bancoId, calculoB).andExpect(status().isCreated()));
		ativar(calculoB, false).andExpect(status().isOk());

		mockMvc.perform(get("/operacoes/" + ordemId).header("Authorization", auth))
				.andExpect(jsonPath("$.calculoId").value(calculoB))
				.andExpect(jsonPath("$.comissaoLiquida").value(70.00));

		// Editar a ordem em andamento mantendo o cálculo desativado é permitido.
		mockMvc.perform(put("/operacoes/" + ordemId)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(clienteId, bancoId, calculoB).replace("\"valorMe\":1000", "\"valorMe\":2000")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculoId").value(calculoB))
				.andExpect(jsonPath("$.comissaoLiquida").value(140.00));
	}

	@Test
	void naoMarcaCalculoInativoEmBancoQueNaoOTinha() throws Exception {
		ativar(calculoB, false).andExpect(status().isOk());

		mockMvc.perform(post("/bancos")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(banco("976", calculoA + "," + calculoB)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("inativo")));
	}

	@Test
	void edicaoDoBancoPodeManterCalculoInativoQueJaTinha() throws Exception {
		Long bancoId = criar("/bancos", banco("977", calculoA + "," + calculoB));
		ativar(calculoB, false).andExpect(status().isOk());

		mockMvc.perform(put("/bancos/" + bancoId)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(banco("977", calculoA + "," + calculoB).replace("Banco 977", "Banco 977 Renomeado")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculos.length()").value(2));
	}

}
