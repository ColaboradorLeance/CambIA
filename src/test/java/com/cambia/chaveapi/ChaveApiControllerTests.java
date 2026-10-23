package com.cambia.chaveapi;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.cambia.chaveapi.ChaveApiTestSupport.LEITURA;
import static com.cambia.chaveapi.ChaveApiTestSupport.criarChave;
import static com.cambia.chaveapi.ChaveApiTestSupport.id;
import static com.cambia.chaveapi.ChaveApiTestSupport.segredo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestão de chaves que vale nas duas telas (perfil e Admin). Origem: C1–C10 do Incremento
 * 79 (docs/chaves-api-plano.md), ajustados no Incremento 81:
 * <ul>
 * <li>C1, C3, C4 (Admin cria chave vinculada a outro usuário; usuário inexistente; nunca
 * Admin) deixaram de existir com K1/K3/K7: viraram P1, P6 e G4.</li>
 * <li>C2 (só Admin gerencia) virou G3 (Analista e Consultor não usam a rota do Admin) e
 * P7/P8 (cada um gerencia só as próprias).</li>
 * <li>C5 e C6 (expiração no passado, nome em branco) viraram P3 e P4.</li>
 * <li>Ficam aqui: a listagem do Admin com o status (C7), revogação definitiva (C8), o
 * segredo fora do banco (C9) e a chave estrangeira do dono (C10).</li>
 * </ul>
 */
@Import({ TestcontainersConfiguration.class, RelogioAjustavelConfig.class })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChaveApiControllerTests {

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

	private Sessao adm;
	private Sessao ana;

	@BeforeEach
	void preparar() throws Exception {
		relogio.zerar();
		adm = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN, "Admin Chaves");
		ana = TestAuthSupport.obterSessao(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ANALISTA, "Ana");
	}

	@Test
	void listaTrazStatusEDadosSemSegredo() throws Exception {
		LocalDate hoje = LocalDate.ofInstant(relogio.instant(), FUSO);
		long idA = id(criarChave(mockMvc, ana.bearer(), "Chave A", null, LEITURA));
		long idB = id(criarChave(mockMvc, ana.bearer(), "Chave B", hoje.plusDays(30), LEITURA));
		long idC = id(criarChave(mockMvc, ana.bearer(), "Chave C", hoje, LEITURA));
		mockMvc.perform(delete("/perfil/chaves-api/" + idA).header("Authorization", ana.bearer()))
				.andExpect(status().isNoContent());
		// Chave C vencia hoje; dois dias depois está expirada.
		relogio.avancar(Duration.ofDays(2));

		// A lista do Admin traz todas as chaves do banco: as asserções vão por id, não por
		// posição; a ordem (mais recente primeiro) é conferida só entre as três deste teste.
		String lista = mockMvc.perform(get("/chaves-api").header("Authorization", adm.bearer()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		java.util.List<Integer> ids = com.jayway.jsonpath.JsonPath.read(lista, "$[*].id");
		java.util.List<Long> ordem = ids.stream().map(Integer::longValue).filter(i -> i == idA || i == idB || i == idC).toList();
		assertEquals(java.util.List.of(idC, idB, idA), ordem);
		mockMvc.perform(get("/chaves-api").header("Authorization", adm.bearer()))
				.andExpect(jsonPath("$[?(@.id == %d)].status".formatted(idC)).value("EXPIRADA"))
				.andExpect(jsonPath("$[?(@.id == %d)].ativa".formatted(idC)).value(false))
				.andExpect(jsonPath("$[?(@.id == %d)].status".formatted(idB)).value("ATIVA"))
				.andExpect(jsonPath("$[?(@.id == %d)].ativa".formatted(idB)).value(true))
				.andExpect(jsonPath("$[?(@.id == %d)].expiraEm".formatted(idB)).value(hoje.plusDays(30).toString()))
				.andExpect(jsonPath("$[?(@.id == %d)].prefixo".formatted(idB)).value(org.hamcrest.Matchers.hasItem(containsString("cambia_"))))
				.andExpect(jsonPath("$[?(@.id == %d)].donoNome".formatted(idB)).value("Ana"))
				.andExpect(jsonPath("$[?(@.id == %d)].criadaPorNome".formatted(idB)).value("Ana"))
				.andExpect(jsonPath("$[?(@.id == %d)].revogadaEm".formatted(idB)).value(org.hamcrest.Matchers.hasItem(nullValue())))
				.andExpect(jsonPath("$[?(@.id == %d)].status".formatted(idA)).value("REVOGADA"))
				.andExpect(jsonPath("$[?(@.id == %d)].revogadaPorNome".formatted(idA)).value("Ana"));
	}

	@Test
	void revogarChave() throws Exception {
		long id = id(criarChave(mockMvc, ana.bearer(), "Chave", null, LEITURA));

		mockMvc.perform(delete("/chaves-api/" + id).header("Authorization", adm.bearer()))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/chaves-api/" + id).header("Authorization", adm.bearer()))
				.andExpect(status().isConflict());
		mockMvc.perform(delete("/chaves-api/999999").header("Authorization", adm.bearer()))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/perfil/chaves-api/" + id).header("Authorization", ana.bearer()))
				.andExpect(status().isConflict());
	}

	@Test
	void segredoNaoFicaNoBanco() throws Exception {
		String corpo = criarChave(mockMvc, ana.bearer(), "Chave", null, LEITURA);
		String chave = segredo(corpo);

		ChaveApi gravada = chaveApiRepository.findById(id(corpo)).orElseThrow();
		assertNotEquals(chave, gravada.getHash());
		assertEquals(GeradorDeChaveApi.hash(chave), gravada.getHash());
	}

	@Test
	void removerUsuarioComChaveVira409() throws Exception {
		criarChave(mockMvc, ana.bearer(), "Chave", null, LEITURA);

		// K10: sem mudança — o Admin revoga as chaves antes; a chave estrangeira segura a remoção.
		mockMvc.perform(delete("/usuarios/" + ana.usuario().getId()).header("Authorization", adm.bearer()))
				.andExpect(status().isConflict());
	}

}
