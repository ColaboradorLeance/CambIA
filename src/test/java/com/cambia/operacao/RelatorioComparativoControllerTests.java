package com.cambia.operacao;

import java.time.LocalDate;

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
class RelatorioComparativoControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteId;
	private Long tlxId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		String clienteBody = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Comparativo\",\"documento\":\"14.777.639/0001-92\"}"))
				.andReturn().getResponse().getContentAsString();
		clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"TLX\",\"formula\":\"N*70%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String tlxBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"999\",\"sigla\":\"TLX\",\"nome\":\"TLX\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		tlxId = ((Number) JsonPath.read(tlxBody, "$.id")).longValue();
	}

	private String criarOperacao(String data, String valorMe, String nivelamento, String taxaFinal) throws Exception {
		String json = """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","moeda":"USD",
				"valorMe":%s,"spotAsset":%s,"nivelamento":%s,"taxaFinal":%s}
				""".formatted(data, clienteId, tlxId, valorMe, nivelamento, nivelamento, taxaFinal);

		return mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
	}

	private String criarOperacaoComMoeda(String data, String moeda, String valorMe, String nivelamento,
			String taxaFinal) throws Exception {
		String json = """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","moeda":"%s",
				"valorMe":%s,"spotAsset":%s,"nivelamento":%s,"taxaFinal":%s}
				""".formatted(data, clienteId, tlxId, moeda, valorMe, nivelamento, nivelamento, taxaFinal);

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
	void serieTemporalDeUmUnicoDiaAcumulaEMedia() throws Exception {
		// USD 1000, nivelamento 5.10, taxaFinal 5.00, venda -> bruto 100, comissao (70%) 70
		String hoje = LocalDate.now().toString();
		completar(criarOperacao(hoje, "1000", "5.10", "5.00"));

		mockMvc.perform(get("/relatorios/comparativo").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serieTemporal.length()").value(1))
				.andExpect(jsonPath("$.serieTemporal[0].data").value(hoje))
				.andExpect(jsonPath("$.serieTemporal[0].totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.mediaDiariaComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.melhorDia.data").value(hoje))
				.andExpect(jsonPath("$.piorDia.data").value(hoje));
	}

	@Test
	void comparaComPeriodoAnteriorECalculaVariacaoPercentual() throws Exception {
		String hoje = LocalDate.now().toString();
		String ontem = LocalDate.now().minusDays(1).toString();

		// hoje: comissao 140 (USD 2000 * (5.10-5.00) * 70% = 140)
		completar(criarOperacao(hoje, "2000", "5.10", "5.00"));
		// ontem: comissao 70
		completar(criarOperacao(ontem, "1000", "5.10", "5.00"));

		mockMvc.perform(get("/relatorios/comparativo").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalOperacoes").value(1))
				.andExpect(jsonPath("$.totalComissaoLiquida").value(140.00))
				.andExpect(jsonPath("$.periodoAnterior.inicio").value(ontem))
				.andExpect(jsonPath("$.periodoAnterior.fim").value(ontem))
				.andExpect(jsonPath("$.periodoAnterior.totalOperacoes").value(1))
				.andExpect(jsonPath("$.periodoAnterior.totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.variacaoComissaoLiquidaPercentual").value(100.00));
	}

	@Test
	void quandoPeriodoAnteriorNaoTemComissaoVariacaoFicaNula() throws Exception {
		String hoje = LocalDate.now().toString();
		completar(criarOperacao(hoje, "1000", "5.10", "5.00"));

		mockMvc.perform(get("/relatorios/comparativo").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.periodoAnterior.totalComissaoLiquida").value(0))
				.andExpect(jsonPath("$.variacaoComissaoLiquidaPercentual").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void diasSemOperacaoCompletaFicamDeForaDoMelhorEPiorDia() throws Exception {
		String hoje = LocalDate.now().toString();
		// operação criada mas não completada -> comissao do dia fica zero
		criarOperacao(hoje, "1000", "5.10", "5.00");

		mockMvc.perform(get("/relatorios/comparativo").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalComissaoLiquida").value(0))
				.andExpect(jsonPath("$.melhorDia").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.piorDia").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void totalOperacoesSomaOperacoesCompletasDoPeriodoInteiro() throws Exception {
		String hoje = LocalDate.now().toString();

		completar(criarOperacao(hoje, "1000", "5.10", "5.00"));
		completar(criarOperacao(hoje, "500", "5.10", "5.00"));
		criarOperacao(hoje, "999", "5.10", "5.00"); // em andamento, não conta

		mockMvc.perform(get("/relatorios/comparativo").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalOperacoes").value(2));
	}

	@Test
	void volumePorMoedaSomaAsMoedasUsadasNoPeriodoInteiro() throws Exception {
		String hoje = LocalDate.now().toString();

		completar(criarOperacaoComMoeda(hoje, "USD", "1000", "5.10", "5.00"));
		completar(criarOperacaoComMoeda(hoje, "USD", "500", "5.10", "5.00"));
		completar(criarOperacaoComMoeda(hoje, "EUR", "300", "5.10", "5.00"));
		criarOperacaoComMoeda(hoje, "GBP", "999", "5.10", "5.00"); // em andamento, não entra no volume

		mockMvc.perform(get("/relatorios/comparativo").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.volumePorMoeda.USD").value(1500.00))
				.andExpect(jsonPath("$.volumePorMoeda.EUR").value(300.00))
				.andExpect(jsonPath("$.volumePorMoeda.GBP").doesNotExist());
	}

	@Test
	void periodoAusenteRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/comparativo").header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

	@Test
	void comparaDoisPeriodosArbitrariosNaoAdjacentes() throws Exception {
		// simula "2 meses atrás" vs "mês passado" com datas fixas, sem depender do calendário do dia do teste
		completar(criarOperacao("2026-06-15", "1000", "5.10", "5.00")); // comissao 70, dentro do período B (mês passado)
		completar(criarOperacao("2026-05-10", "2000", "5.10", "5.00")); // comissao 140, dentro do período A (2 meses atrás)
		completar(criarOperacao("2026-04-01", "500", "5.10", "5.00")); // comissao 35, fora dos dois períodos

		mockMvc.perform(get("/relatorios/comparativo")
						.param("inicioA", "2026-05-01")
						.param("fimA", "2026-05-31")
						.param("inicioB", "2026-06-01")
						.param("fimB", "2026-06-30")
						.header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inicio").value("2026-05-01"))
				.andExpect(jsonPath("$.fim").value("2026-05-31"))
				.andExpect(jsonPath("$.totalOperacoes").value(1))
				.andExpect(jsonPath("$.totalComissaoLiquida").value(140.00))
				.andExpect(jsonPath("$.periodoAnterior.inicio").value("2026-06-01"))
				.andExpect(jsonPath("$.periodoAnterior.fim").value("2026-06-30"))
				.andExpect(jsonPath("$.periodoAnterior.totalOperacoes").value(1))
				.andExpect(jsonPath("$.periodoAnterior.totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.variacaoComissaoLiquidaPercentual").value(100.00));
	}

	@Test
	void fimDoPeriodoAntesDoInicioRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/comparativo")
						.param("inicioA", "2026-05-31")
						.param("fimA", "2026-05-01")
						.param("inicioB", "2026-06-01")
						.param("fimB", "2026-06-30")
						.header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

	@Test
	void informarSoUmDosDoisPeriodosRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/comparativo")
						.param("inicioA", "2026-05-01")
						.param("fimA", "2026-05-31")
						.header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

}
