package com.cambia.chaveapi;

import java.time.LocalDate;
import java.time.ZoneId;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.auth.TestAuthSupport.Sessao;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.cambia.chaveapi.ChaveApiTestSupport.CRIAR_ORDENS;
import static com.cambia.chaveapi.ChaveApiTestSupport.HEADER;
import static com.cambia.chaveapi.ChaveApiTestSupport.LEITURA;
import static com.cambia.chaveapi.ChaveApiTestSupport.corpoChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.criarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.id;
import static com.cambia.chaveapi.ChaveApiTestSupport.postarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.segredo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 81 (P1–P10, L1 e S1 de docs/chaves-api-usuario-plano.md): cada usuário cria,
 * lista e revoga as próprias chaves em /perfil/chaves-api (K1), em qualquer perfil (K3),
 * com escopos (K4), expiração obrigatória de até 1 ano (K5) e escopo limitado pelo perfil
 * (K6). Ninguém vê nem revoga a chave de outro por aqui.
 */
@Import({ TestcontainersConfiguration.class, RelogioAjustavelConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class PerfilChaveApiControllerTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private ChaveApiRepository chaveApiRepository;

	@Autowired
	private RelogioAjustavelConfig.RelogioAjustavel relogio;

	private Sessao ana;
	private Sessao caio;

	@BeforeEach
	void preparar() throws Exception {
		relogio.zerar();
		ana = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA, "Ana");
		caio = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.CONSULTOR, "Caio");
	}

	// "Hoje" pelo mesmo relógio da aplicação, para o teste não falhar perto da meia-noite.
	private LocalDate hoje() {
		return LocalDate.ofInstant(relogio.instant(), FUSO);
	}

	@Test
	void analistaCriaAPropriaChaveERecebeSegredoUmaVez() throws Exception {
		String corpo = criarChave(mockMvc, ana.bearer(), "Integração ERP, produção", null, LEITURA, CRIAR_ORDENS);
		String chave = segredo(corpo);

		assertTrue(chave.startsWith("cambia_"));
		assertEquals(50, chave.length());
		assertEquals(chave.substring(0, 13), JsonPath.read(corpo, "$.prefixo"));
		ChaveApi gravada = chaveApiRepository.findById(id(corpo)).orElseThrow();
		// K2: o dono é quem criou.
		assertEquals(ana.usuario().getId(), gravada.getUsuarioId());
		assertEquals(ana.usuario().getId(), gravada.getCriadaPorUsuarioId());

		mockMvc.perform(get("/perfil/chaves-api").header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].chave").doesNotExist())
				.andExpect(jsonPath("$[0].hash").doesNotExist())
				.andExpect(content().string(not(containsString(chave))));
	}

	@Test
	void expiracaoPadraoEhUmAno() throws Exception {
		LocalDate hoje = hoje();
		String corpo = criarChave(mockMvc, ana.bearer(), "Sem data", null, LEITURA);

		assertEquals(hoje.plusYears(1).toString(), JsonPath.read(corpo, "$.expiraEm"));
	}

	@Test
	void rejeitaExpiracaoNoPassadoOuAlemDeUmAno() throws Exception {
		postarChave(mockMvc, ana.bearer(), corpoChave("Ontem", hoje().minusDays(1), LEITURA))
				.andExpect(status().isBadRequest());
		postarChave(mockMvc, ana.bearer(), corpoChave("Longe", hoje().plusYears(1).plusDays(1), LEITURA))
				.andExpect(status().isBadRequest());
		// Limite exato vale: hoje + 1 ano.
		postarChave(mockMvc, ana.bearer(), corpoChave("No limite", hoje().plusYears(1), LEITURA))
				.andExpect(status().isCreated());
	}

	@Test
	void rejeitaEscoposVaziosOuInvalidos() throws Exception {
		postarChave(mockMvc, ana.bearer(), corpoChave("Vazia", null))
				.andExpect(status().isBadRequest());
		postarChave(mockMvc, ana.bearer(), corpoChave("Inválida", null, "APAGAR"))
				.andExpect(status().isBadRequest());
		postarChave(mockMvc, ana.bearer(), "{\"nome\":\"   \",\"escopos\":[\"LEITURA\"]}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void consultorSoCriaChaveDeLeitura() throws Exception {
		// K6: a chave nunca faz mais do que o dono faria pela tela.
		postarChave(mockMvc, caio.bearer(), corpoChave("Caio cria", null, CRIAR_ORDENS))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("Consultor")));
		postarChave(mockMvc, caio.bearer(), corpoChave("Caio lê", null, LEITURA))
				.andExpect(status().isCreated());
	}

	@Test
	void adminCriaChavePessoal() throws Exception {
		Sessao adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Adm");

		// K3: substitui a D3 do Inc. 79 para chaves pessoais.
		postarChave(mockMvc, adm.bearer(), corpoChave("Chave do Admin", null, LEITURA))
				.andExpect(status().isCreated());
	}

	@Test
	void listaSoAsPropriasChaves() throws Exception {
		criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA, CRIAR_ORDENS);
		criarChave(mockMvc, caio.bearer(), "Do Caio", null, LEITURA);

		mockMvc.perform(get("/perfil/chaves-api").header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].nome").value("Da Ana"))
				.andExpect(jsonPath("$[0].escopos", hasSize(2)))
				.andExpect(jsonPath("$[0].status").value("ATIVA"))
				.andExpect(jsonPath("$[0].prefixo", containsString("cambia_")));
		mockMvc.perform(get("/perfil/chaves-api").header("Authorization", caio.bearer()))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].nome").value("Do Caio"));
	}

	@Test
	void naoRevogaChaveDeOutro() throws Exception {
		String corpo = criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA);

		// 404, não 403: não revelar a quem tenta que a chave existe.
		mockMvc.perform(delete("/perfil/chaves-api/" + id(corpo)).header("Authorization", caio.bearer()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/operacoes").header(HEADER, segredo(corpo)))
				.andExpect(status().isOk());
	}

	@Test
	void revogarAPropria() throws Exception {
		String corpo = criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA);

		mockMvc.perform(delete("/perfil/chaves-api/" + id(corpo)).header("Authorization", ana.bearer()))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/perfil/chaves-api/" + id(corpo)).header("Authorization", ana.bearer()))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/perfil/chaves-api").header("Authorization", ana.bearer()))
				.andExpect(jsonPath("$[0].status").value("REVOGADA"));
		mockMvc.perform(get("/operacoes").header(HEADER, segredo(corpo)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void semSessaoNaoGerencia() throws Exception {
		mockMvc.perform(get("/perfil/chaves-api"))
				.andExpect(status().isUnauthorized());
		postarChave(mockMvc, "Bearer invalido", corpoChave("X", null, LEITURA))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void respostasNuncaTrazemSegredo() throws Exception {
		// S1: só a resposta da criação carrega o segredo.
		Sessao adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Adm");
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA));

		for (String[] rota : new String[][] { { "/perfil/chaves-api", ana.bearer() }, { "/chaves-api", adm.bearer() } }) {
			mockMvc.perform(get(rota[0]).header("Authorization", rota[1]))
					.andExpect(status().isOk())
					.andExpect(content().string(not(containsString(chave))))
					.andExpect(content().string(not(containsString("\"hash\""))))
					.andExpect(content().string(not(containsString("\"chave\""))));
		}
	}

	@Test
	void segredoNaoApareceEmLog(CapturedOutput saida) throws Exception {
		// L1: criar, usar e errar a chave não pode deixar o segredo na saída da aplicação.
		String chave = segredo(criarChave(mockMvc, ana.bearer(), "Da Ana", null, LEITURA));
		mockMvc.perform(get("/operacoes").header(HEADER, chave)).andExpect(status().isOk());
		mockMvc.perform(get("/operacoes").header(HEADER, chave + "x")).andExpect(status().isUnauthorized());

		assertFalse(saida.getAll().contains(chave), "o segredo da chave apareceu na saída");
		assertFalse(saida.getAll().contains(chave.substring(13)), "parte secreta da chave apareceu na saída");
	}

}
