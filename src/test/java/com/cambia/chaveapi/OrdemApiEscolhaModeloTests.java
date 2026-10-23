package com.cambia.chaveapi;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.auth.TestAuthSupport.Sessao;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;

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

import static com.cambia.chaveapi.ChaveApiTestSupport.CRIAR_ORDENS;
import static com.cambia.chaveapi.ChaveApiTestSupport.HEADER;
import static com.cambia.chaveapi.ChaveApiTestSupport.criarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.id;
import static com.cambia.chaveapi.ChaveApiTestSupport.segredo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 86: o modelo de cálculo de uma ordem que chega pela API é escolhido na
 * plataforma (front), não pela integração. Decisões do PO em docs/decisoes.md ("Ordem via
 * API escolhe o modelo na plataforma"): banco com um só modelo ativo → já entra com ele;
 * senão entra sem modelo; calculoId enviado pela API é ignorado; sem modelo a comissão fica
 * vazia e a ordem não pode ser confirmada até alguém escolher na tela.
 *
 * <p>Números: venda de 1.000 com Nivelamento 5,10 e Taxa Final 5,00 → Total Bruto 100,00;
 * cálculo A (N*50%) dá comissão 50,00 e cálculo B (N*70%) dá 70,00.
 */
@Import({ TestcontainersConfiguration.class, RelogioAjustavelConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrdemApiEscolhaModeloTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private RelogioAjustavelConfig.RelogioAjustavel relogio;

	private Sessao adm;
	private String chave;
	private long clienteId;
	private long calculoA;
	private long calculoB;
	private long bancoUmModelo;
	private long bancoDoisModelos;

	@BeforeEach
	void preparar() throws Exception {
		relogio.zerar();
		adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Adm");
		Sessao ana = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ANALISTA, "Ana");
		chave = segredo(criarChave(mockMvc, ana.bearer(), "ERP", null, CRIAR_ORDENS));
		clienteId = criar("/clientes", "{\"nome\":\"Cliente Api Modelo\",\"documento\":\"41.222.333/0001-66\"}");
		calculoA = criar("/calculos", "{\"nome\":\"A Metade\",\"formula\":\"N*50%\"}");
		calculoB = criar("/calculos", "{\"nome\":\"B Setenta\",\"formula\":\"N*70%\"}");
		bancoUmModelo = criar("/bancos", banco("961", calculoA + ""));
		bancoDoisModelos = criar("/bancos", banco("962", calculoA + "," + calculoB));
	}

	private long criar(String caminho, String json) throws Exception {
		return id(mockMvc.perform(post(caminho)
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private static String banco(String codigo, String calculos) {
		return "{\"codigoBanco\":\"%s\",\"sigla\":\"M%s\",\"nome\":\"Banco %s\",\"taxaRebate\":0,\"calculoIds\":[%s]}"
				.formatted(codigo, codigo, codigo, calculos);
	}

	private String ordem(long bancoId, Long calculoId) {
		String calculo = calculoId == null ? "" : "\"calculoId\":%d,".formatted(calculoId);
		return """
				{"data":"2026-09-01","clienteId":%d,"bancoId":%d,%s"cv":"V","prCrVir":"Pronto","fundo":"P",
				"spreadEmissao":"NA","moeda":"USD","valorMe":1000,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId, calculo);
	}

	private ResultActions postarPelaApi(long bancoId, Long calculoId) throws Exception {
		return mockMvc.perform(post("/operacoes")
				.header(HEADER, chave)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ordem(bancoId, calculoId)));
	}

	private ResultActions mudarStatus(long ordemId, String novo) throws Exception {
		return mockMvc.perform(patch("/operacoes/" + ordemId + "/status")
				.header("Authorization", adm.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"%s\"}".formatted(novo)));
	}

	private long idDe(ResultActions resultado) throws Exception {
		return id(resultado.andReturn().getResponse().getContentAsString());
	}

	@Test
	void bancoComUmModeloJaEntraComEle() throws Exception {
		postarPelaApi(bancoUmModelo, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(calculoA))
				.andExpect(jsonPath("$.comissaoLiquida").value(50.00));
	}

	@Test
	void bancoComVariosModelosEntraSemModeloEComissaoVazia() throws Exception {
		postarPelaApi(bancoDoisModelos, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(nullValue()))
				.andExpect(jsonPath("$.calculoNome").value(nullValue()))
				.andExpect(jsonPath("$.comissaoLiquida").value(nullValue()))
				// o resto continua calculado
				.andExpect(jsonPath("$.totalBrutoCambio").value(100.00));
	}

	@Test
	void calculoIdEnviadoPelaApiEIgnorado() throws Exception {
		postarPelaApi(bancoDoisModelos, calculoB)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(nullValue()));
		// nem é validado: um cálculo de outro banco não derruba a ordem
		postarPelaApi(bancoUmModelo, calculoB)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(calculoA));
	}

	@Test
	void naoConfirmaSemModelo() throws Exception {
		long ordemId = idDe(postarPelaApi(bancoDoisModelos, null).andExpect(status().isCreated()));

		mudarStatus(ordemId, "CONFIRMADO")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail", containsString("modelo de cálculo")));
	}

	@Test
	void cancelarSemModeloPode() throws Exception {
		long ordemId = idDe(postarPelaApi(bancoDoisModelos, null).andExpect(status().isCreated()));

		mudarStatus(ordemId, "CANCELADO").andExpect(status().isOk());
	}

	@Test
	void escolheNaTelaEDepoisConfirma() throws Exception {
		long ordemId = idDe(postarPelaApi(bancoDoisModelos, null).andExpect(status().isCreated()));

		// edição pela tela exige a escolha, como qualquer ordem de banco com vários modelos
		mockMvc.perform(put("/operacoes/" + ordemId)
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(bancoDoisModelos, null)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/operacoes/" + ordemId)
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(bancoDoisModelos, calculoB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calculoId").value(calculoB))
				.andExpect(jsonPath("$.comissaoLiquida").value(70.00));

		mudarStatus(ordemId, "CONFIRMADO").andExpect(status().isOk());
	}

	@Test
	void bancoSemModeloAtivoEntraSemModelo() throws Exception {
		mockMvc.perform(patch("/calculos/" + calculoA + "/ativo")
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ativo\":false}"))
				.andExpect(status().isOk());

		postarPelaApi(bancoUmModelo, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calculoId").value(nullValue()));
	}

	@Test
	void pelaTelaContinuaExigindoAEscolhaNaCriacao() throws Exception {
		mockMvc.perform(post("/operacoes")
						.header("Authorization", adm.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordem(bancoDoisModelos, null)))
				.andExpect(status().isBadRequest());
	}

}
