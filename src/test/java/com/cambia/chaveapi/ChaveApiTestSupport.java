package com.cambia.chaveapi;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Apoio dos testes de chave de API do Incremento 81: a chave é criada pelo próprio dono em
 * /perfil/chaves-api (K1), com escopos (K4) e expiração (K5).
 */
final class ChaveApiTestSupport {

	static final String HEADER = "X-API-Key";
	static final String LEITURA = "LEITURA";
	static final String CRIAR_ORDENS = "CRIAR_ORDENS";

	private ChaveApiTestSupport() {
	}

	static String corpoChave(String nome, LocalDate expiraEm, String... escopos) {
		String lista = Arrays.stream(escopos).map(e -> "\"" + e + "\"").collect(Collectors.joining(","));
		String expira = expiraEm == null ? "" : ",\"expiraEm\":\"" + expiraEm + "\"";
		return "{\"nome\":\"%s\",\"escopos\":[%s]%s}".formatted(nome, lista, expira);
	}

	static ResultActions postarChave(MockMvc mockMvc, String bearer, String corpo) throws Exception {
		return mockMvc.perform(post("/perfil/chaves-api")
				.header("Authorization", bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(corpo));
	}

	/** Cria a chave do dono da sessão e devolve o corpo da resposta (com o segredo, única vez). */
	static String criarChave(MockMvc mockMvc, String bearer, String nome, LocalDate expiraEm, String... escopos)
			throws Exception {
		return postarChave(mockMvc, bearer, corpoChave(nome, expiraEm, escopos))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	static String segredo(String corpoCriacao) {
		return JsonPath.read(corpoCriacao, "$.chave");
	}

	static long id(String corpo) {
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	/** Cliente, modelo de cálculo e banco mínimos para criar ordem; devolve {clienteId, bancoId}. */
	static long[] clienteEBanco(MockMvc mockMvc, String adminBearer, String sufixo) throws Exception {
		String cliente = mockMvc.perform(post("/clientes")
						.header("Authorization", adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Chave %s\",\"documento\":\"77.777.777/0001-77\"}".formatted(sufixo)))
				.andReturn().getResponse().getContentAsString();
		String calculo = mockMvc.perform(post("/calculos")
						.header("Authorization", adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo Chave %s\",\"formula\":\"N*50%%\"}".formatted(sufixo)))
				.andReturn().getResponse().getContentAsString();
		String banco = mockMvc.perform(post("/bancos")
						.header("Authorization", adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"993\",\"sigla\":\"CB\",\"nome\":\"CB\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(id(calculo))))
				.andReturn().getResponse().getContentAsString();
		return new long[] { id(cliente), id(banco) };
	}

	static String ordemJson(long clienteId, long bancoId) {
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);
	}

}
