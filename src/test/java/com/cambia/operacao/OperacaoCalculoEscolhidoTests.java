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
 * Incremento 84: a ordem guarda qual modelo de cálculo do banco a Comissão Líquida usa.
 * Banco com um cálculo: preenchido sozinho. Com mais de um: escolha obrigatória (400 sem
 * ela, decisão do PO). Tirar o cálculo do banco depois não muda as ordens que já o usaram.
 *
 * <p>Números: venda de 1.000 com Nivelamento 5,10 e Taxa Final 5,00 → Total Bruto 100,00;
 * cálculo A (N*50%) dá comissão 50,00 e cálculo B (N*70%) dá 70,00.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoCalculoEscolhidoTests {

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
	private Long bancoUmCalculo;
	private Long bancoDoisCalculos;

	@BeforeEach
	void preparar() throws Exception {
		auth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		clienteId = criar("/clientes", "{\"nome\":\"Cliente Calculo\",\"documento\":\"21.222.333/0001-44\"}");
		calculoA = criar("/calculos", "{\"nome\":\"A Metade\",\"formula\":\"N*50%\"}");
		calculoB = criar("/calculos", "{\"nome\":\"B Setenta\",\"formula\":\"N*70%\"}");
		bancoUmCalculo = criar("/bancos", banco("982", "\"calculoIds\":[%d]".formatted(calculoA)));
		bancoDoisCalculos = criar("/bancos", banco("983", "\"calculoIds\":[%d,%d]".formatted(calculoA, calculoB)));
	}

	private static String banco(String codigo, String calculos) {
		return "{\"codigoBanco\":\"%s\",\"sigla\":\"C%s\",\"nome\":\"Banco %s\",\"taxaRebate\":0,%s}"
				.formatted(codigo, codigo, codigo, calculos);
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

	private static String ordem(Long clienteId, Long bancoId, Long calculoId) {
		String calculo = calculoId == null ? "" : "\"calculoId\":%d,".formatted(calculoId);
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,%s"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, calculo);
	}

	private ResultActions postar(Long bancoId, Long calculoId) throws Exception {
		return mockMvc.perform(post("/operacoes")
				.header("Authorization", auth)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ordem(clienteId, bancoId, calculoId)));
	}

	private void confirmar(Long id) throws Exception {
		mockMvc.perform(patch("/operacoes/" + id + "/status")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk());
	}

	private Long id(ResultActions resultado) throws Exception {
		return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void bancoComUmCalculoNaoPrecisaInformar() throws Exception {
		postar(bancoUmCalculo, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(calculoA))
				.andExpect(jsonPath("$.calculoNome").value("A Metade"))
				// fórmula vai junto pro pop-up das tabelas de ordens
				.andExpect(jsonPath("$.calculoFormula").value("N*50%"));
	}

	@Test
	void bancoComVariosCalculosExigeAEscolha() throws Exception {
		postar(bancoDoisCalculos, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("cálculo")));
	}

	@Test
	void calculoQueNaoEDoBancoERecusado() throws Exception {
		postar(bancoUmCalculo, calculoB)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("não pertence")));
	}

	@Test
	void comissaoUsaOCalculoEscolhido() throws Exception {
		Long comA = id(postar(bancoDoisCalculos, calculoA).andExpect(status().isCreated()));
		Long comB = id(postar(bancoDoisCalculos, calculoB).andExpect(status().isCreated()));
		confirmar(comA);
		confirmar(comB);

		mockMvc.perform(get("/operacoes/" + comA).header("Authorization", auth))
				.andExpect(jsonPath("$.comissaoLiquida").value(50.00));
		mockMvc.perform(get("/operacoes/" + comB).header("Authorization", auth))
				.andExpect(jsonPath("$.comissaoLiquida").value(70.00));
	}

	@Test
	void tirarOCalculoDoBancoNaoMudaAOrdemQueJaUsou() throws Exception {
		Long ordemB = id(postar(bancoDoisCalculos, calculoB).andExpect(status().isCreated()));
		confirmar(ordemB);

		mockMvc.perform(put("/bancos/" + bancoDoisCalculos)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(banco("983", "\"calculoIds\":[%d]".formatted(calculoA))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/operacoes/" + ordemB).header("Authorization", auth))
				.andExpect(jsonPath("$.calculoId").value(calculoB))
				.andExpect(jsonPath("$.comissaoLiquida").value(70.00));
		// ordens novas já não podem escolher B
		postar(bancoDoisCalculos, calculoB).andExpect(status().isBadRequest());
	}

	@Test
	void edicaoPodeTrocarOCalculo() throws Exception {
		Long ordem = id(postar(bancoDoisCalculos, calculoB).andExpect(status().isCreated()));

		mockMvc.perform(put("/operacoes/" + ordem)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(clienteId, bancoDoisCalculos, calculoA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculoId").value(calculoA))
				.andExpect(jsonPath("$.comissaoLiquida").value(50.00));
	}

	@Test
	void edicaoMantemCalculoQueSaiuDoBancoSeNaoForTrocado() throws Exception {
		Long ordem = id(postar(bancoDoisCalculos, calculoB).andExpect(status().isCreated()));
		mockMvc.perform(put("/bancos/" + bancoDoisCalculos)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(banco("983", "\"calculoIds\":[%d]".formatted(calculoA))))
				.andExpect(status().isOk());

		// Editar outro campo mantendo o cálculo B (que já era da ordem) é permitido.
		mockMvc.perform(put("/operacoes/" + ordem)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(clienteId, bancoDoisCalculos, calculoB).replace("\"valorMe\":1000", "\"valorMe\":2000")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculoId").value(calculoB));
	}

	@Test
	void trocarDeBancoNaEdicaoExigeCalculoDoNovoBanco() throws Exception {
		Long ordem = id(postar(bancoDoisCalculos, calculoB).andExpect(status().isCreated()));

		mockMvc.perform(put("/operacoes/" + ordem)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(clienteId, bancoUmCalculo, calculoB)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/operacoes/" + ordem)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(clienteId, bancoUmCalculo, null)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculoId").value(calculoA));
	}

}
