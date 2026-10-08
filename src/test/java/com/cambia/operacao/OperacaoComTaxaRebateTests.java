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

/**
 * A fórmula de comissão do Modelo de Cálculo pode usar "R" para representar a Taxa de
 * Rebate cadastrada no Banco, além de "N" (Total Bruto do Câmbio) — pedido do usuário.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperacaoComTaxaRebateTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String authHeader;

	@BeforeEach
	void preparar() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		authHeader = "Bearer " + token;
	}

	private Long criarCliente() throws Exception {
		String body = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Cliente Rebate LTDA\",\"documento\":\"14.777.639/0001-92\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private Long criarCalculo(String formula) throws Exception {
		String body = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Modelo com rebate\",\"formula\":\"%s\"}".formatted(formula)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private Long criarBanco(Long calculoId, String taxaRebate) throws Exception {
		String body = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"001\",\"sigla\":\"RBT\",\"nome\":\"Banco Rebate\",\"taxaRebate\":%s,\"calculoId\":%d}"
								.formatted(taxaRebate, calculoId)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	void comissaoUsaTaxaDeRebateCadastradaNoBancoAlemDoTotalBruto() throws Exception {
		// Fórmula usa N (Total Bruto) e R (Taxa de Rebate, cadastrada em % no Banco): N*R%
		Long calculoId = criarCalculo("N*R%");
		Long bancoId = criarBanco(calculoId, "60");
		Long clienteId = criarCliente();

		// venda: USD 1000, nivelamento 5.10, taxaFinal 5.00 -> Total Bruto = 100.00
		// comissão = Total Bruto (100) * Taxa de Rebate (60%) = 60.00
		String json = """
				{"data":"2026-08-27","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","fundo":"M","codigoOperacao":"555","spreadEmissao":"0.020","moeda":"USD",
				"valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);

		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));

		mockMvc.perform(get(location).header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalBrutoCambio").value(100.00))
				.andExpect(jsonPath("$.comissaoLiquida").value(60.00));
	}

}
