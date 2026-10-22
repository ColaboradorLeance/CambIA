package com.cambia.operacao;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 80 (achado do teste de carga de 2026-10-08): as telas baixavam a tabela de
 * ordens inteira e filtravam no navegador. GET /operacoes/pagina pagina e filtra no
 * banco; GET /operacoes?status=X traz só um status. Cada teste cria um cliente de nome
 * único e filtra por ele, pra contar só as próprias ordens (e já exercitar o filtro).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoPaginacaoTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String auth;
	private String nomeCliente;
	private Long clienteId;
	private Long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		auth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ADMIN, "Admin Paginacao");
		nomeCliente = "Cliente Paginacao " + UUID.randomUUID();
		clienteId = criar("/clientes", "{\"nome\":\"%s\",\"documento\":\"88.123.456/0001-%02d\"}"
				.formatted(nomeCliente, (int) (Math.random() * 100)));
		Long calculoId = criar("/calculos", "{\"nome\":\"Modelo Paginacao\",\"formula\":\"N*50%\"}");
		bancoId = criar("/bancos",
				"{\"codigoBanco\":\"991\",\"sigla\":\"PG\",\"nome\":\"Banco Paginacao Unico\",\"taxaRebate\":0,\"calculoId\":%d}"
						.formatted(calculoId));
	}

	private Long criar(String caminho, String json) throws Exception {
		String corpo = mockMvc.perform(post(caminho)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	private Long criarOrdem(String cv, String moeda, String valorMe, String prCrVir, String fundo) throws Exception {
		boolean pronto = "Pronto".equals(prCrVir);
		String extras = pronto ? "\"spreadEmissao\":\"NA\"" : "\"spreadEmissao\":\"0.020\",\"codigoOperacao\":\"555\",\"spotAsset\":5.10";
		return criar("/operacoes", """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"%s","prCrVir":"%s","fundo":"%s",%s,
				"moeda":"%s","valorMe":%s,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, cv, prCrVir, fundo, extras, moeda, valorMe));
	}

	private Long criarOrdem() throws Exception {
		return criarOrdem("V", "USD", "1000", "Pronto", "P");
	}

	private void confirmar(Long id) throws Exception {
		mockMvc.perform(patch("/operacoes/" + id + "/status")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk());
	}

	private MockHttpServletRequestBuilder pagina() {
		return get("/operacoes/pagina").header("Authorization", auth).param("nome", nomeCliente);
	}

	@Test
	void paginaRetornaFatiaETotal() throws Exception {
		for (int i = 0; i < 3; i++) {
			confirmar(criarOrdem());
		}
		criarOrdem(); // em andamento — fora do filtro de status

		mockMvc.perform(pagina().param("status", "CONFIRMADO").param("tamanho", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.itens", hasSize(2)))
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanho").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(2));

		mockMvc.perform(pagina().param("status", "CONFIRMADO").param("tamanho", "2").param("pagina", "1"))
				.andExpect(jsonPath("$.itens", hasSize(1)));
	}

	@Test
	void confirmadasVemDaMaisRecentementeConfirmadaPraMaisAntiga() throws Exception {
		Long primeira = criarOrdem();
		Long segunda = criarOrdem();
		confirmar(segunda);
		confirmar(primeira);

		String corpo = mockMvc.perform(pagina().param("status", "CONFIRMADO"))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(corpo, "$.itens[*].id");
		assertEquals(List.of(primeira, segunda), ids.stream().map(Number::longValue).toList());
	}

	@Test
	void itensTrazemValoresCalculados() throws Exception {
		confirmar(criarOrdem());

		mockMvc.perform(pagina().param("status", "CONFIRMADO"))
				.andExpect(jsonPath("$.itens[0].reais").value(5000.0))
				.andExpect(jsonPath("$.itens[0].clienteNome").value(nomeCliente))
				.andExpect(jsonPath("$.itens[0].bancoNome").value("Banco Paginacao Unico"));
	}

	@Test
	void filtrosExatosNoBanco() throws Exception {
		criarOrdem("C", "EUR", "1000", "Pronto", "P");
		criarOrdem("V", "USD", "1000", "Credito", "K");

		mockMvc.perform(pagina().param("moeda", "EUR"))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.itens[0].moeda").value("EUR"));
		mockMvc.perform(pagina().param("cv", "V"))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("tipo", "Credito"))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("fundo", "K"))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("status", "ANDAMENTO"))
				.andExpect(jsonPath("$.total").value(2));
	}

	@Test
	void filtrosDeTextoSaoParciaisEIgnoramCaixa() throws Exception {
		Long id = criarOrdem();
		String idTrade = JsonPath.read(mockMvc.perform(get("/operacoes/" + id).header("Authorization", auth))
				.andReturn().getResponse().getContentAsString(), "$.idTrade");

		mockMvc.perform(pagina().param("banco", "paginacao uni"))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("banco", "banco que nao existe"))
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.itens", hasSize(0)));
		mockMvc.perform(pagina().param("cnpj", "88.123"))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("idTrade", idTrade.substring(5)))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("criadoPor", "admin pagin"))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("completadoPor", "admin pagin"))
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void curingasDoLikeSaoTratadosComoTextoLiteral() throws Exception {
		criarOrdem();

		// "%" e "_" digitados pelo usuário não podem virar curinga e trazer tudo.
		mockMvc.perform(pagina().param("banco", "%"))
				.andExpect(jsonPath("$.total").value(0));
		mockMvc.perform(pagina().param("banco", "_"))
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void filtroDeValorAceitaFormatoBrasileiro() throws Exception {
		criarOrdem("V", "USD", "1234.56", "Pronto", "P");
		criarOrdem("V", "USD", "999", "Pronto", "P");

		mockMvc.perform(pagina().param("valorMoeda", "1.234"))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.itens[0].valorMe").value(1234.56));
		mockMvc.perform(pagina().param("valorMoeda", "1.234,56"))
				.andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void filtroDeDataDoFechamentoUsaODiaEmSaoPaulo() throws Exception {
		confirmar(criarOrdem());
		LocalDate hoje = LocalDate.now(FUSO);

		mockMvc.perform(pagina().param("dataFechamento", hoje.toString()))
				.andExpect(jsonPath("$.total").value(1));
		mockMvc.perform(pagina().param("dataFechamento", hoje.minusDays(1).toString()))
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void tamanhoEPaginaForaDosLimitesSaoRejeitados() throws Exception {
		mockMvc.perform(pagina().param("tamanho", "0")).andExpect(status().isBadRequest());
		mockMvc.perform(pagina().param("tamanho", "201")).andExpect(status().isBadRequest());
		mockMvc.perform(pagina().param("pagina", "-1")).andExpect(status().isBadRequest());
	}

	@Test
	void listagemPorStatusTrazSoAqueleStatus() throws Exception {
		confirmar(criarOrdem());
		criarOrdem();

		mockMvc.perform(get("/operacoes").header("Authorization", auth).param("status", "ANDAMENTO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].status", everyItem(is("ANDAMENTO"))));
	}

	@Test
	void listagemSemParametroContinuaTrazendoTodosOsStatus() throws Exception {
		// Compatibilidade: integrações que já chamam GET /operacoes sem parâmetro.
		Long confirmada = criarOrdem();
		confirmar(confirmada);
		Long aberta = criarOrdem();

		String corpo = mockMvc.perform(get("/operacoes").header("Authorization", auth))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(corpo, "$[*].id");
		List<Long> todos = ids.stream().map(Number::longValue).toList();
		org.junit.jupiter.api.Assertions.assertTrue(todos.containsAll(List.of(confirmada, aberta)));
	}

	@Test
	void opcoesDeFiltroTrazemMoedasEFundosDistintosDoStatus() throws Exception {
		confirmar(criarOrdem("V", "CHF", "1000", "Pronto", "P"));
		confirmar(criarOrdem("V", "CHF", "1000", "Credito", "Q"));
		criarOrdem("V", "JPY", "1000", "Pronto", "P"); // em andamento: não entra

		String corpo = mockMvc.perform(get("/operacoes/opcoes-filtro").header("Authorization", auth)
						.param("status", "CONFIRMADO"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<String> moedas = JsonPath.read(corpo, "$.moedas");
		List<String> fundos = JsonPath.read(corpo, "$.fundos");
		org.junit.jupiter.api.Assertions.assertTrue(moedas.contains("CHF"));
		org.junit.jupiter.api.Assertions.assertFalse(moedas.contains("JPY"));
		org.junit.jupiter.api.Assertions.assertTrue(fundos.containsAll(List.of("P", "Q")));
		assertEquals(moedas.stream().distinct().sorted().toList(), moedas);
	}

}
