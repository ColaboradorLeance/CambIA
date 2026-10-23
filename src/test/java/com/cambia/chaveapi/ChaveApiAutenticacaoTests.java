package com.cambia.chaveapi;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.NomesCookieAuth;
import com.cambia.auth.TestAuthSupport;
import com.cambia.auth.TestAuthSupport.Sessao;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

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
import static com.cambia.chaveapi.ChaveApiTestSupport.LEITURA;
import static com.cambia.chaveapi.ChaveApiTestSupport.clienteEBanco;
import static com.cambia.chaveapi.ChaveApiTestSupport.criarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.id;
import static com.cambia.chaveapi.ChaveApiTestSupport.ordemJson;
import static com.cambia.chaveapi.ChaveApiTestSupport.segredo;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uso da chave no header X-API-Key (A1–A9, A11 do Incremento 79, docs/chaves-api-plano.md),
 * ajustado ao Incremento 81: a chave é criada pelo próprio dono em /perfil/chaves-api (K1)
 * e age em nome dele (K2), não mais de um usuário técnico vinculado pelo Admin. Nunca
 * alcança gestão de usuários, chaves, perfil nem sessão (D7) e dispensa CSRF (D8). A10
 * (limite de tentativas) fica em ChaveApiRateLimitTests; o antigo "usuário promovido a
 * Admin deixa de valer" virou E9 em ChaveApiEscopoTests (K3).
 */
@Import({ TestcontainersConfiguration.class, RelogioAjustavelConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChaveApiAutenticacaoTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private RelogioAjustavelConfig.RelogioAjustavel relogio;

	private Sessao adm;
	private Sessao dono;
	private long clienteId;
	private long bancoId;

	@BeforeEach
	void preparar() throws Exception {
		relogio.zerar();
		adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Adm");
		dono = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA,
				"Integração Beyond Teste");
		long[] ids = clienteEBanco(mockMvc, adm.bearer(), "Autenticação");
		clienteId = ids[0];
		bancoId = ids[1];
	}

	private String criarChaveDoDono(LocalDate expiraEm) throws Exception {
		return segredo(criarChave(mockMvc, dono.bearer(), "Chave", expiraEm, LEITURA, CRIAR_ORDENS));
	}

	private ResultActions criarOrdemCom(String chave) throws Exception {
		return mockMvc.perform(post("/operacoes")
				.header(HEADER, chave)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ordemJson(clienteId, bancoId)));
	}

	@Test
	void chaveValidaCriaOrdemComoDono() throws Exception {
		String chave = criarChaveDoDono(null);

		String corpo = criarOrdemCom(chave)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criadoPorNome").value("Integração Beyond Teste"))
				.andReturn().getResponse().getContentAsString();

		Number usuarioDoEvento = (Number) entityManager
				.createNativeQuery("SELECT usuario_id FROM operacoes_eventos WHERE operacao_id = :id")
				.setParameter("id", id(corpo))
				.getSingleResult();
		assertEquals(dono.usuario().getId(), usuarioDoEvento.longValue());
	}

	@Test
	void chaveRespeitaPerfilDoDono() throws Exception {
		Sessao consultor = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.CONSULTOR, "Consultor Integração");
		String chave = segredo(criarChave(mockMvc, consultor.bearer(), "Leitura", null, LEITURA));

		mockMvc.perform(get("/operacoes").header(HEADER, chave))
				.andExpect(status().isOk());
		// O Consultor só pode ter chave de leitura (K6, na criação); o 403 aqui é do escopo.
		// A regra do perfil no uso (dono rebaixado) é testada em ChaveApiEscopoTests (E6).
		criarOrdemCom(chave)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("escopo")));
	}

	@Test
	void chaveInexistenteRetorna401() throws Exception {
		mockMvc.perform(get("/operacoes").header(HEADER, "cambia_" + "x".repeat(43)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail", containsString("Chave de API")));
	}

	@Test
	void chaveRevogadaRetorna401() throws Exception {
		String corpo = criarChave(mockMvc, dono.bearer(), "Chave", null, LEITURA, CRIAR_ORDENS);
		String chave = segredo(corpo);

		criarOrdemCom(chave).andExpect(status().isCreated());
		mockMvc.perform(delete("/perfil/chaves-api/" + id(corpo)).header("Authorization", dono.bearer()))
				.andExpect(status().isNoContent());
		criarOrdemCom(chave).andExpect(status().isUnauthorized());
	}

	@Test
	void chaveExpiradaRetorna401() throws Exception {
		// Vale até o fim do dia informado (fuso de São Paulo); dois dias depois, não vale mais.
		String chave = criarChaveDoDono(LocalDate.now(FUSO));
		criarOrdemCom(chave).andExpect(status().isCreated());

		relogio.avancar(Duration.ofDays(2));

		criarOrdemCom(chave).andExpect(status().isUnauthorized());
	}

	@Test
	void chaveNaoGerenciaUsuariosChavesPerfilNemSessao() throws Exception {
		String chave = criarChaveDoDono(null);

		// A mensagem prova que foi a D7 (e não o escopo, que também recusaria estas rotas).
		for (String rota : new String[] { "/usuarios", "/chaves-api", "/perfil/chaves-api" }) {
			mockMvc.perform(get(rota).header(HEADER, chave))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		}
		mockMvc.perform(delete("/auth/sessao").header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
	}

	@Test
	void caminhoCodificadoNaoBurlaBloqueio() throws Exception {
		// Achado da revisão de segurança do Inc. 79: "/%75suarios" é "/usuarios" pro
		// roteamento do Spring, mas não começava com "/usuarios" na comparação crua do filtro.
		String chave = criarChaveDoDono(null);

		// URI.create: com uma String, o MockMvc trata o caminho como template e codifica o
		// "%" de novo ("%2575"), que o firewall do Spring barra com 400 antes do filtro —
		// o teste passaria sem exercitar o cenário real.
		// Rota pública (/auth) de propósito: em /usuarios a chave de Analista já levaria 403
		// pela regra de perfil, e o teste passaria mesmo sem o bloqueio da D7 funcionar. A
		// mensagem distingue os dois 403 (perfil: "Seu perfil não tem permissão").
		mockMvc.perform(delete(java.net.URI.create("/%61uth/sessao")).header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
		mockMvc.perform(get(java.net.URI.create("/%75suarios")).header(HEADER, chave))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail", containsString("Chave de API não pode")));
	}

	@Test
	void chaveNaoPrecisaDeCsrf() throws Exception {
		// Pior caso: chave E cookie de sessão (de um Admin) na mesma requisição, sem o
		// header X-XSRF-TOKEN. A chave vence (a ordem fica em nome do dono da chave) e o
		// CSRF não se aplica, porque não foi o cookie que autenticou.
		String chave = criarChaveDoDono(null);

		mockMvc.perform(post("/operacoes")
						.header(HEADER, chave)
						.cookie(new Cookie(NomesCookieAuth.SESSAO, adm.token()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(ordemJson(clienteId, bancoId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.criadoPorNome").value("Integração Beyond Teste"));
	}

	private String ultimoUso() throws Exception {
		String lista = mockMvc.perform(get("/perfil/chaves-api").header("Authorization", dono.bearer()))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(lista, "$[0].ultimoUsoEm");
	}

	@Test
	void registraUltimoUso() throws Exception {
		String chave = criarChaveDoDono(null);
		assertNull(ultimoUso());

		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		String primeiro = ultimoUso();
		assertNotNull(primeiro);

		// Uso logo em seguida não regrava (janela de 5 minutos evita um UPDATE por requisição).
		relogio.avancar(Duration.ofMinutes(1));
		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		assertEquals(primeiro, ultimoUso());

		relogio.avancar(Duration.ofMinutes(6));
		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		assertNotEquals(primeiro, ultimoUso());
	}

	@Test
	void headerVazioOuMalformadoNaoAutentica() throws Exception {
		mockMvc.perform(get("/operacoes").header(HEADER, ""))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/operacoes").header(HEADER, "abc"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sessaoContinuaFuncionandoSemHeader() throws Exception {
		mockMvc.perform(get("/operacoes").header("Authorization", adm.bearer()))
				.andExpect(status().isOk());
	}

}
