package com.cambia.operacao;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FechamentoAgendadorTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	@Autowired
	private FechamentoAgendadorService agendador;

	@Autowired
	private ConfiguracaoFechamentoRepository configuracaoRepository;

	@Autowired
	private FechamentoGeradoRepository fechamentoGeradoRepository;

	private String authHeader() throws Exception {
		String token = TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository, Perfil.ADMIN);
		return "Bearer " + token;
	}

	@Test
	void semConfiguracaoNaoGeraNada() {
		agendador.verificarEGerarSeNecessario(LocalDateTime.of(2026, 7, 2, 18, 0));
		assertEquals(0, fechamentoGeradoRepository.count());
	}

	@Test
	void horarioDiferenteDoConfiguradoNaoGeraNada() {
		configuracaoRepository.save(new ConfiguracaoFechamento(LocalTime.of(18, 0)));

		agendador.verificarEGerarSeNecessario(LocalDateTime.of(2026, 7, 2, 10, 0));

		assertEquals(0, fechamentoGeradoRepository.count());
	}

	@Test
	void horarioConfiguradoGeraPdfEExcelUmaVezPorDia() throws Exception {
		configuracaoRepository.save(new ConfiguracaoFechamento(LocalTime.of(18, 0)));

		agendador.verificarEGerarSeNecessario(LocalDateTime.of(2026, 7, 2, 18, 0));

		List<FechamentoGerado> gerados = fechamentoGeradoRepository.findAll();
		assertEquals(2, gerados.size());
		assertTrue(gerados.stream().anyMatch(g -> "PDF".equals(g.getFormato())));
		assertTrue(gerados.stream().anyMatch(g -> "XLSX".equals(g.getFormato())));
		assertTrue(gerados.stream().allMatch(g -> g.getData().equals(LocalDate.of(2026, 7, 2))));

		// rodar de novo no mesmo dia não deve duplicar
		agendador.verificarEGerarSeNecessario(LocalDateTime.of(2026, 7, 2, 18, 0));
		assertEquals(2, fechamentoGeradoRepository.count());

		String auth = authHeader();
		mockMvc.perform(get("/fechamentos/historico").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));

		Long idPdf = gerados.stream().filter(g -> "PDF".equals(g.getFormato())).findFirst().orElseThrow().getId();
		mockMvc.perform(get("/fechamentos/historico/" + idPdf + "/download").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.header().string("Content-Type", "application/pdf"));
	}

}
