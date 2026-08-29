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
class RelatorioPosicaoAbertoControllerTests {

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

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;

		clienteAId = criarCliente("Cliente A LTDA", "14.777.639/0001-92");
		clienteBId = criarCliente("Cliente B LTDA", "22.333.444/0001-55");
		tlxId = criarBanco("TLX", "N*70%");
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

	private String criarOperacao(String data, Long clienteId, String moeda, String valorMe) throws Exception {
		String json = """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","moeda":"%s",
				"valorMe":%s,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(data, clienteId, tlxId, moeda, valorMe);

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
	void listaOperacoesEmAbertoComIdadeOrdenadasDaMaisAntigaParaMaisRecente() throws Exception {
		String dezDiasAtras = LocalDate.now().minusDays(10).toString();
		String doisDiasAtras = LocalDate.now().minusDays(2).toString();
		criarOperacao(dezDiasAtras, clienteAId, "USD", "1000");
		criarOperacao(doisDiasAtras, clienteAId, "USD", "500");

		mockMvc.perform(get("/relatorios/posicao-aberto").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalOperacoesEmAndamento").value(2))
				.andExpect(jsonPath("$.operacoes[0].data").value(dezDiasAtras))
				.andExpect(jsonPath("$.operacoes[0].diasEmAberto").value(10))
				.andExpect(jsonPath("$.operacoes[1].data").value(doisDiasAtras))
				.andExpect(jsonPath("$.operacoes[1].diasEmAberto").value(2));
	}

	@Test
	void operacaoCompletaNaoEntraNaPosicaoEmAberto() throws Exception {
		String hoje = LocalDate.now().toString();
		completar(criarOperacao(hoje, clienteAId, "USD", "1000"));

		mockMvc.perform(get("/relatorios/posicao-aberto").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalOperacoesEmAndamento").value(0))
				.andExpect(jsonPath("$.operacoes.length()").value(0));
	}

	@Test
	void exposicaoPorMoedaAgregaOperacoesDaMesmaMoeda() throws Exception {
		String hoje = LocalDate.now().toString();
		criarOperacao(hoje, clienteAId, "USD", "1000");
		criarOperacao(hoje, clienteBId, "USD", "500");

		mockMvc.perform(get("/relatorios/posicao-aberto").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exposicaoPorMoeda.USD").value(1500.00));
	}

	@Test
	void exposicaoPorClienteFicaSegmentadaPorMoedaSemSomarMoedasDiferentes() throws Exception {
		String hoje = LocalDate.now().toString();
		criarOperacao(hoje, clienteAId, "USD", "1000");
		criarOperacao(hoje, clienteAId, "EUR", "300");

		mockMvc.perform(get("/relatorios/posicao-aberto").header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.exposicaoPorCliente.length()").value(2))
				.andExpect(jsonPath("$.exposicaoPorCliente[0].moeda").value("USD"))
				.andExpect(jsonPath("$.exposicaoPorCliente[0].valorMe").value(1000.00))
				.andExpect(jsonPath("$.exposicaoPorCliente[1].moeda").value("EUR"))
				.andExpect(jsonPath("$.exposicaoPorCliente[1].valorMe").value(300.00));
	}

}
