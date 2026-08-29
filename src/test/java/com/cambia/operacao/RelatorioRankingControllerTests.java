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
class RelatorioRankingControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;
	private Long clienteAId;
	private Long clienteBId;
	private Long tlxId;
	private Long bzaId;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		clienteAId = criarCliente("Cliente A LTDA", "14.777.639/0001-92");
		clienteBId = criarCliente("Cliente B LTDA", "22.333.444/0001-55");
		tlxId = criarBanco("TLX", "N*70%");
		bzaId = criarBanco("BZA", "N*50%");
	}

	private Long criarCliente(String nome, String documento) throws Exception {
		String body = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"%s\",\"documento\":\"%s\"}".formatted(nome, documento)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private Long criarBanco(String nome, String formula) throws Exception {
		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"%s\",\"formula\":\"%s\"}".formatted(nome, formula)))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String body = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"%s\",\"sigla\":\"%s\",\"nome\":\"%s\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(nome, nome, nome, calculoId)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private String criarOperacao(String authHeaderUsado, String data, Long clienteId, Long bancoId, String cv,
			String moeda, String valorMe) throws Exception {
		// bruto positivo: venda usa nivelamento > taxaFinal; compra usa taxaFinal > nivelamento
		String nivelamento = "C".equals(cv) ? "5.00" : "5.10";
		String taxaFinal = "C".equals(cv) ? "5.10" : "5.00";
		String json = """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"%s","prCrVir":"Credito","moeda":"%s",
				"valorMe":%s,"spotAsset":5.10,"nivelamento":%s,"taxaFinal":%s}
				""".formatted(data, clienteId, bancoId, cv, moeda, valorMe, nivelamento, taxaFinal);

		return mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeaderUsado)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");
	}

	private void completar(String authHeaderUsado, String location) throws Exception {
		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeaderUsado)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));
	}

	@Test
	void rankingsPorClienteBancoMoedaETipoOrdenadosPorComissaoDesc() throws Exception {
		String hoje = LocalDate.now().toString();

		// Cliente A / TLX / USD / Venda -> bruto 100, comissao 70
		completar(authHeader, criarOperacao(authHeader, hoje, clienteAId, tlxId, "V", "USD", "1000"));
		// Cliente B / BZA / EUR / Compra -> bruto 100, comissao 50
		completar(authHeader, criarOperacao(authHeader, hoje, clienteBId, bzaId, "C", "EUR", "1000"));

		mockMvc.perform(get("/relatorios/rankings").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.porCliente[0].rotulo").value("Cliente A LTDA"))
				.andExpect(jsonPath("$.porCliente[0].totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.porCliente[1].rotulo").value("Cliente B LTDA"))
				.andExpect(jsonPath("$.porCliente[1].totalComissaoLiquida").value(50.00))
				.andExpect(jsonPath("$.porBanco[0].rotulo").value("TLX"))
				.andExpect(jsonPath("$.porBanco[1].rotulo").value("BZA"))
				.andExpect(jsonPath("$.porMoeda[0].rotulo").value("USD"))
				.andExpect(jsonPath("$.porMoeda[1].rotulo").value("EUR"))
				.andExpect(jsonPath("$.porTipo[0].rotulo").value("V"))
				.andExpect(jsonPath("$.porTipo[1].rotulo").value("C"));
	}

	@Test
	void rankingDeUsuariosCriadorSeparadoDeCompletador() throws Exception {
		String hoje = LocalDate.now().toString();
		String anaToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ANALISTA, "Ana Criadora");
		String betoToken = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ANALISTA, "Beto Completador");
		String anaAuth = "Bearer " + anaToken;
		String betoAuth = "Bearer " + betoToken;

		// Ana cria, Beto completa -> comissao 70 entra na conta do Beto (completador)
		String opAna = criarOperacao(anaAuth, hoje, clienteAId, tlxId, "V", "USD", "1000");
		completar(betoAuth, opAna);

		// Beto cria e não completa
		criarOperacao(betoAuth, hoje, clienteBId, bzaId, "C", "EUR", "1000");

		mockMvc.perform(get("/relatorios/rankings").param("periodo", "HOJE").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.porUsuarioCriador.length()").value(2))
				.andExpect(jsonPath("$.porUsuarioCriador[0].rotulo").value("Ana Criadora"))
				.andExpect(jsonPath("$.porUsuarioCriador[0].totalComissaoLiquida").value(70.00))
				.andExpect(jsonPath("$.porUsuarioCriador[1].rotulo").value("Beto Completador"))
				.andExpect(jsonPath("$.porUsuarioCriador[1].totalComissaoLiquida").value(0))
				.andExpect(jsonPath("$.porUsuarioCompletador.length()").value(1))
				.andExpect(jsonPath("$.porUsuarioCompletador[0].rotulo").value("Beto Completador"))
				.andExpect(jsonPath("$.porUsuarioCompletador[0].totalComissaoLiquida").value(70.00));
	}

	@Test
	void periodoAusenteRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/rankings").header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aceitaIntervaloComInicioEFimNoLugarDoPeriodo() throws Exception {
		completar(authHeader, criarOperacao(authHeader, "2026-05-10", clienteAId, tlxId, "V", "USD", "1000"));
		completar(authHeader, criarOperacao(authHeader, "2026-06-10", clienteBId, bzaId, "C", "EUR", "1000"));

		mockMvc.perform(get("/relatorios/rankings")
						.param("inicio", "2026-05-01")
						.param("fim", "2026-05-31")
						.header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inicio").value("2026-05-01"))
				.andExpect(jsonPath("$.fim").value("2026-05-31"))
				.andExpect(jsonPath("$.porCliente.length()").value(1))
				.andExpect(jsonPath("$.porCliente[0].rotulo").value("Cliente A LTDA"));
	}

	@Test
	void fimAntesDeInicioRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/rankings")
						.param("inicio", "2026-05-31")
						.param("fim", "2026-05-01")
						.header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

	@Test
	void apenasInicioSemFimRetorna400() throws Exception {
		mockMvc.perform(get("/relatorios/rankings")
						.param("inicio", "2026-05-01")
						.header("Authorization", authHeader))
				.andExpect(status().isBadRequest());
	}

}
