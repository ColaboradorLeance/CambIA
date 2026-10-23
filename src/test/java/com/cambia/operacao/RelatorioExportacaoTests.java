package com.cambia.operacao;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Incremento 82: todos os relatórios (Ordens, Comparativos, Rankings, Posição em aberto)
 * exportam em PDF e Excel, com os mesmos parâmetros da tela — antes só o Fechamento
 * Diário exportava. Os testes abrem o arquivo gerado de verdade (texto do PDF, células
 * da planilha), não só o status HTTP.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelatorioExportacaoTests {

	private static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private MagicLinkTokenRepository magicLinkTokenRepository;

	private String auth;
	private String nomeCliente;
	private Long clienteId;
	private Long bancoId;
	private String idTradeAberta;

	@BeforeEach
	void preparar() throws Exception {
		auth = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository, magicLinkTokenRepository,
				Perfil.ADMIN, "Admin Exportacao");
		nomeCliente = "Cliente Exportacao " + UUID.randomUUID().toString().substring(0, 8);
		clienteId = criar("/clientes", "{\"nome\":\"%s\",\"documento\":\"66.123.456/0001-01\"}".formatted(nomeCliente));
		Long calculoId = criar("/calculos", "{\"nome\":\"Modelo Exportacao\",\"formula\":\"N*50%\"}");
		bancoId = criar("/bancos",
				"{\"codigoBanco\":\"990\",\"sigla\":\"EX\",\"nome\":\"Banco Exportacao\",\"taxaRebate\":0,\"calculoId\":%d}"
						.formatted(calculoId));

		Long confirmada = criarOrdem("USD", "1000");
		mockMvc.perform(patch("/operacoes/" + confirmada + "/status")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"CONFIRMADO\"}"))
				.andExpect(status().isOk());

		Long aberta = criarOrdem("EUR", "300");
		idTradeAberta = JsonPath.read(mockMvc.perform(get("/operacoes/" + aberta).header("Authorization", auth))
				.andReturn().getResponse().getContentAsString(), "$.idTrade");
	}

	private Long criar(String caminho, String json) throws Exception {
		String corpo = mockMvc.perform(post(caminho)
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(corpo, "$.id")).longValue();
	}

	private Long criarOrdem(String moeda, String valorMe) throws Exception {
		return criar("/operacoes", """
				{"data":"%s","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Pronto","fundo":"P","spreadEmissao":"NA",
				"moeda":"%s","valorMe":%s,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(LocalDate.now(), clienteId, bancoId, moeda, valorMe));
	}

	private MockHttpServletResponse baixar(String caminho, String tipoEsperado) throws Exception {
		return mockMvc.perform(get(caminho).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", tipoEsperado))
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andReturn().getResponse();
	}

	private String textoDoPdf(String caminho) throws Exception {
		byte[] pdf = baixar(caminho, "application/pdf").getContentAsByteArray();
		assertEquals("%PDF", new String(pdf, 0, 4));
		PdfReader leitor = new PdfReader(pdf);
		PdfTextExtractor extrator = new PdfTextExtractor(leitor);
		StringBuilder texto = new StringBuilder();
		for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
			texto.append(extrator.getTextFromPage(pagina)).append('\n');
		}
		// Quebra de linha ENTRE palavras numa célula é legítima ("Código da" / "Ordem");
		// normalizar pra espaço deixa o teste exigir só o conteúdo, não a diagramação.
		return texto.toString().replaceAll("\\s+", " ");
	}

	private XSSFWorkbook planilha(String caminho) throws Exception {
		byte[] xlsx = baixar(caminho, TIPO_XLSX).getContentAsByteArray();
		return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
	}

	private static List<String> nomesDasAbas(XSSFWorkbook planilha) {
		List<String> nomes = new ArrayList<>();
		planilha.forEach(aba -> nomes.add(aba.getSheetName()));
		return nomes;
	}

	/** Linha de cabeçalho da tabela da aba: a primeira cuja 1ª célula é o título da 1ª coluna. */
	private static Row cabecalho(Sheet aba, String primeiraColuna) {
		for (Row linha : aba) {
			Cell celula = linha.getCell(0);
			if (celula != null && celula.getCellType() == CellType.STRING
					&& primeiraColuna.equals(celula.getStringCellValue())) {
				return linha;
			}
		}
		throw new AssertionError("Cabeçalho '" + primeiraColuna + "' não encontrado na aba " + aba.getSheetName());
	}

	private static List<String> textosDaLinha(Row linha) {
		List<String> textos = new ArrayList<>();
		linha.forEach(celula -> textos.add(celula.getCellType() == CellType.STRING ? celula.getStringCellValue()
				: String.valueOf(celula.getNumericCellValue())));
		return textos;
	}

	@Test
	void relatorioDeOrdensEmPdfTrazTituloPeriodoEDados() throws Exception {
		String texto = textoDoPdf("/relatorios/operacoes/pdf?periodo=HOJE&clienteId=" + clienteId);

		assertTrue(texto.contains("Relatório de Ordens"), texto);
		assertTrue(texto.contains(nomeCliente), texto);
		assertTrue(texto.contains("Código da Ordem"), texto);
		assertTrue(texto.contains("Gerado em"), texto);
		// Incremento 83: cartões de destaque, linha de total e paginação no rodapé.
		assertTrue(texto.contains("Comissão Líquida (confirmadas)"), texto);
		assertTrue(texto.contains(" Total "), texto);
		assertTrue(texto.contains("Página 1 de "), texto);
		// A data da ordem sai inteira na célula: coluna estreita demais a quebrava no meio
		// ("23/10/202" + "6"), o que a normalização acima não esconderia.
		// Na LINHA da ordem (código seguido da data) — o cabeçalho "Período: ..." já contém
		// a data de hoje e faria a verificação passar mesmo com a célula quebrada.
		String hoje = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(LocalDate.now());
		assertTrue(java.util.regex.Pattern.compile("\\d{4}-\\d{6} " + java.util.regex.Pattern.quote(hoje))
				.matcher(texto).find(), texto);
		// Títulos curtos de coluna estreita também não podem quebrar no meio ("Fund o",
		// "Moed a", "C/ V" — visto no PDF renderizado): a largura tem que contar o espaço
		// interno da célula, não só os caracteres.
		assertTrue(texto.contains(" Fundo "), texto);
		assertTrue(texto.contains(" Moeda "), texto);
		assertTrue(texto.contains(" C/V "), texto);
	}

	@Test
	void relatorioDeOrdensEmExcelRespeitaFiltrosETemNumerosDeVerdade() throws Exception {
		try (XSSFWorkbook planilha = planilha(
				"/relatorios/operacoes/xlsx?periodo=HOJE&moeda=USD&clienteId=" + clienteId)) {
			Sheet aba = planilha.getSheet("Ordens");
			assertNotNull(aba, "abas: " + nomesDasAbas(planilha));
			Row cabecalho = cabecalho(aba, "Código da Ordem");
			List<String> colunas = textosDaLinha(cabecalho);
			// Excel traz o conjunto completo, inclusive colunas que o PDF omite por largura.
			assertTrue(colunas.containsAll(List.of("Valor em moeda", "Spot Asset", "Nivelamento", "Taxa Final",
					"Valor em Real", "Comissão Líquida", "Criado em")), colunas.toString());

			Row dados = aba.getRow(cabecalho.getRowNum() + 1);
			assertNotNull(dados);
			// Depois da única ordem em USD vem a linha de Total (Incremento 83), não outra ordem.
			assertEquals("Total", aba.getRow(cabecalho.getRowNum() + 2).getCell(0).getStringCellValue(),
					"só a ordem em USD deveria sair");
			Cell valorEmMoeda = dados.getCell(colunas.indexOf("Valor em moeda"));
			assertEquals(CellType.NUMERIC, valorEmMoeda.getCellType());
			assertEquals(1000.0, valorEmMoeda.getNumericCellValue(), 0.001);
			assertEquals("USD", dados.getCell(colunas.indexOf("Moeda")).getStringCellValue());
		}
	}

	@Test
	void comparativoExportaNosDoisFormatos() throws Exception {
		String texto = textoDoPdf("/relatorios/comparativo/pdf?periodo=MES");
		assertTrue(texto.contains("Comparativos e Tendências"), texto);
		assertTrue(texto.contains("Período anterior"), texto);

		try (XSSFWorkbook planilha = planilha("/relatorios/comparativo/xlsx?periodo=MES")) {
			assertTrue(nomesDasAbas(planilha).containsAll(List.of("Resumo", "Série temporal")),
					nomesDasAbas(planilha).toString());
			// Como a tela: só os dias com movimento (o serviço devolve TODOS os dias do
			// período, zerados inclusive — um mês viraria ~30 linhas de zero).
			Sheet serie = planilha.getSheet("Série temporal");
			Row cabecalho = cabecalho(serie, "Data");
			assertNotNull(serie.getRow(cabecalho.getRowNum() + 1), "o dia de hoje tem movimento");
			assertEquals("Total", serie.getRow(cabecalho.getRowNum() + 2).getCell(0).getStringCellValue(),
					"dias sem movimento não deveriam sair");
		}
	}

	@Test
	void comparativoEntreDoisPeriodosTambemExporta() throws Exception {
		LocalDate hoje = LocalDate.now();
		String caminho = "/relatorios/comparativo/xlsx?inicioA=%s&fimA=%s&inicioB=%s&fimB=%s"
				.formatted(hoje.minusDays(7), hoje, hoje.minusDays(14), hoje.minusDays(8));
		try (XSSFWorkbook planilha = planilha(caminho)) {
			assertTrue(nomesDasAbas(planilha).contains("Resumo"));
		}
	}

	@Test
	void rankingsTemUmaAbaPorDimensao() throws Exception {
		try (XSSFWorkbook planilha = planilha("/relatorios/rankings/xlsx?periodo=MES")) {
			List<String> abas = nomesDasAbas(planilha);
			assertTrue(abas.containsAll(List.of("Por cliente", "Por banco", "Por moeda", "Por tipo (C-V)")),
					abas.toString());
		}
		assertTrue(textoDoPdf("/relatorios/rankings/pdf?periodo=MES").contains("Rankings por Dimensão"));
	}

	@Test
	void rankingsEntreDoisPeriodosSaemNoMesmoArquivo() throws Exception {
		LocalDate hoje = LocalDate.now();
		String caminho = "/relatorios/rankings/xlsx?inicioA=%s&fimA=%s&inicioB=%s&fimB=%s"
				.formatted(hoje.minusDays(7), hoje, hoje.minusDays(14), hoje.minusDays(8));
		try (XSSFWorkbook planilha = planilha(caminho)) {
			List<String> abas = nomesDasAbas(planilha);
			assertTrue(abas.stream().anyMatch(nome -> nome.startsWith("A - ")), abas.toString());
			assertTrue(abas.stream().anyMatch(nome -> nome.startsWith("B - ")), abas.toString());
		}
	}

	@Test
	void posicaoEmAbertoExportaAsOrdensAbertas() throws Exception {
		assertTrue(textoDoPdf("/relatorios/posicao-aberto/pdf").contains(idTradeAberta));

		try (XSSFWorkbook planilha = planilha("/relatorios/posicao-aberto/xlsx")) {
			Sheet aba = planilha.getSheet("Ordens em aberto");
			assertNotNull(aba, "abas: " + nomesDasAbas(planilha));
			boolean achou = false;
			for (Row linha : aba) {
				Cell celula = linha.getCell(0);
				achou |= celula != null && celula.getCellType() == CellType.STRING
						&& idTradeAberta.equals(celula.getStringCellValue());
			}
			assertTrue(achou, "ordem aberta " + idTradeAberta + " não saiu na planilha");
		}
	}

	@Test
	void exportacaoUsaAsMesmasValidacoesDoRelatorio() throws Exception {
		mockMvc.perform(get("/relatorios/comparativo/pdf").header("Authorization", auth))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/relatorios/operacoes/xlsx").header("Authorization", auth))
				.andExpect(status().isBadRequest());
	}

	@Test
	void consultorNaoExporta() throws Exception {
		String consultor = "Bearer " + TestAuthSupport.obterToken(mockMvc, usuarioRepository,
				magicLinkTokenRepository, Perfil.CONSULTOR);
		mockMvc.perform(get("/relatorios/posicao-aberto/pdf").header("Authorization", consultor))
				.andExpect(status().isForbidden());
	}

}
