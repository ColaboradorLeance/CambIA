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
class FechamentoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteId;
	private Long tlxId;
	private Long bzaId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		String clienteBody = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"CRAS Agroindustria LTDA\",\"documento\":\"14.777.639/0001-92\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoTlxBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"TLX\",\"formula\":\"N*70%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoTlxId = ((Number) JsonPath.read(calculoTlxBody, "$.id")).longValue();

		String tlxBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"999\",\"sigla\":\"TLX\",\"nome\":\"TLX\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoTlxId)))
				.andReturn().getResponse().getContentAsString();
		tlxId = ((Number) JsonPath.read(tlxBody, "$.id")).longValue();

		String calculoBzaBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"BZA\",\"formula\":\"N*50%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoBzaId = ((Number) JsonPath.read(calculoBzaBody, "$.id")).longValue();

		String bzaBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"998\",\"sigla\":\"BZA\",\"nome\":\"BZA\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoBzaId)))
				.andReturn().getResponse().getContentAsString();
		bzaId = ((Number) JsonPath.read(bzaBody, "$.id")).longValue();
	}

	private String criarOperacao(String data, Long bancoId, String cv, String moeda, String valorMe,
			String nivelamento, String taxaFinal) throws Exception {
		String json = """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"%s","prCrVir":"Credito","fundo":"M","codigoOperacao":"555","spreadEmissao":"0.020","moeda":"%s",
				"valorMe":%s,"spotAsset":%s,"nivelamento":%s,"taxaFinal":%s}
				""".formatted(data, clienteId, bancoId, cv, moeda, valorMe, nivelamento, nivelamento, taxaFinal);

		return mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
	}

	private void completar(String location) throws Exception {
		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));
	}

	@Test
	void calculaFechamentoDoDiaComResumoResultadoQuebrasEPosicao() throws Exception {
		// Op1: TLX, venda, USD 1000, nivelamento 5.10, taxaFinal 5.00 -> reais 5000, bruto 100, comissao 70 (completa)
		String op1 = criarOperacao("2026-07-02", tlxId, "V", "USD", "1000", "5.10", "5.00");
		completar(op1);

		// Op2: BZA, compra, EUR 2000, nivelamento 5.00, taxaFinal 5.05 -> reais 10100, bruto 100, comissao 50 (completa)
		String op2 = criarOperacao("2026-07-02", bzaId, "C", "EUR", "2000", "5.00", "5.05");
		completar(op2);

		// Op3: TLX, venda, USD 500, mesmo dia, mas fica em andamento (não completa)
		criarOperacao("2026-07-02", tlxId, "V", "USD", "500", "5.10", "5.00");

		// Op4: TLX, venda, USD 300, dia diferente, em andamento -> só conta na posição em aberto
		criarOperacao("2026-07-03", tlxId, "V", "USD", "300", "5.10", "5.00");

		mockMvc.perform(get("/fechamentos/2026-07-02").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("2026-07-02"))
				.andExpect(jsonPath("$.resumoOperacional.totalOperacoes").value(3))
				.andExpect(jsonPath("$.resumoOperacional.porStatus.ANDAMENTO").value(1))
				.andExpect(jsonPath("$.resumoOperacional.porStatus.CONFIRMADO").value(2))
				.andExpect(jsonPath("$.resultadoFinanceiro.totalReais").value(15100.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.totalBrutoCambio").value(200.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.totalComissaoLiquida").value(120.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.ticketMedioReais").value(7550.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.maiorOperacaoReais").value(10100.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.menorOperacaoReais").value(5000.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.volumePorMoeda.USD").value(1000.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.volumePorMoeda.EUR").value(2000.00))
				.andExpect(jsonPath("$.resultadoFinanceiro.quantidadePorMoeda.USD").value(1))
				.andExpect(jsonPath("$.resultadoFinanceiro.quantidadePorMoeda.EUR").value(1))
				.andExpect(jsonPath("$.quebras.porBanco[0].rotulo").value("TLX"))
				.andExpect(jsonPath("$.quebras.porBanco[0].quantidade").value(2))
				// Op3 (em andamento) conta na quantidade do grupo TLX, mas NÃO soma dinheiro:
				// desde o Incremento 76 a prévia existe na resposta da operação, e as quebras
				// zeram os calculados das não confirmadas antes de somar (só op1 entra aqui).
				.andExpect(jsonPath("$.quebras.porBanco[0].totalReais").value(5000.00))
				.andExpect(jsonPath("$.quebras.porBanco[0].totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.quebras.porBanco[1].rotulo").value("BZA"))
				.andExpect(jsonPath("$.quebras.porBanco[1].quantidade").value(1))
				.andExpect(jsonPath("$.quebras.porBanco[1].totalReais").value(10100.00))
				.andExpect(jsonPath("$.quebras.porMoeda[0].rotulo").value("USD"))
				.andExpect(jsonPath("$.quebras.porMoeda[0].quantidade").value(2))
				.andExpect(jsonPath("$.quebras.porMoeda[1].rotulo").value("EUR"))
				.andExpect(jsonPath("$.quebras.porMoeda[1].quantidade").value(1))
				.andExpect(jsonPath("$.posicaoEmAberto.totalOperacoesEmAndamento").value(2))
				.andExpect(jsonPath("$.posicaoEmAberto.exposicaoPorMoeda.USD").value(800.00));
	}

	@Test
	void fechamentoDeDiaSemOperacoesRetornaZerado() throws Exception {
		mockMvc.perform(get("/fechamentos/2020-01-01").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resumoOperacional.totalOperacoes").value(0))
				.andExpect(jsonPath("$.resultadoFinanceiro.totalReais").value(0));
	}

}
