package com.cambia.operacao;

import com.cambia.TestcontainersConfiguration;
import com.cambia.auth.MagicLinkTokenRepository;
import com.cambia.auth.TestAuthSupport;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
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

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FechamentoExportTests {

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

		String clienteBody = mockMvc.perform(post("/clientes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"CRAS Agroindustria LTDA\",\"documento\":\"14.777.639/0001-92\"}"))
				.andReturn().getResponse().getContentAsString();
		Long clienteId = ((Number) JsonPath.read(clienteBody, "$.id")).longValue();

		String calculoBody = mockMvc.perform(post("/calculos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"TLX\",\"formula\":\"N*70%\"}"))
				.andReturn().getResponse().getContentAsString();
		Long calculoId = ((Number) JsonPath.read(calculoBody, "$.id")).longValue();

		String bancoBody = mockMvc.perform(post("/bancos")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"codigoBanco\":\"999\",\"sigla\":\"TLX\",\"nome\":\"TLX\",\"taxaRebate\":0,\"calculoId\":%d}"
								.formatted(calculoId)))
				.andReturn().getResponse().getContentAsString();
		Long bancoId = ((Number) JsonPath.read(bancoBody, "$.id")).longValue();

		String opJson = """
				{"data":"2026-07-02","clienteId":%d,"bancoId":%d,"cv":"V","prCrVir":"Credito","moeda":"USD",
				"valorMe":1000,"spotAsset":5.10,"nivelamento":5.10,"taxaFinal":5.00}
				""".formatted(clienteId, bancoId);

		String location = mockMvc.perform(post("/operacoes")
						.header("Authorization", authHeader)
						.contentType(MediaType.APPLICATION_JSON)
						.content(opJson))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(patch(location + "/status")
				.header("Authorization", authHeader)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONFIRMADO\"}"));
	}

	@Test
	void exportaFechamentoEmPdf() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/fechamentos/2026-07-02/pdf")
						.header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().exists("Content-Disposition"))
				.andReturn().getResponse();

		byte[] corpo = response.getContentAsByteArray();
		assertTrue(corpo.length > 100, "PDF gerado parece vazio demais");
		String assinatura = new String(corpo, 0, 4);
		assertEquals("%PDF", assinatura);
	}

	@Test
	void exportaFechamentoEmExcel() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/fechamentos/2026-07-02/xlsx")
						.header("Authorization", authHeader))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.andExpect(header().exists("Content-Disposition"))
				.andReturn().getResponse();

		byte[] corpo = response.getContentAsByteArray();
		assertTrue(corpo.length > 100, "Excel gerado parece vazio demais");

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(corpo))) {
			Sheet sheet = workbook.getSheetAt(0);
			boolean encontrouComissao = false;
			for (Row row : sheet) {
				for (var cell : row) {
					if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
							&& Math.abs(cell.getNumericCellValue() - 70.0) < 0.01) {
						encontrouComissao = true;
					}
				}
			}
			assertTrue(encontrouComissao, "Não encontrei o valor da comissão líquida (70,00) na planilha gerada");
		}
	}

	@Test
	void excelTemCabecalhosDeColunaERotulosPorExtenso() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/fechamentos/2026-07-02/xlsx")
						.header("Authorization", authHeader))
				.andReturn().getResponse();

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
			Sheet sheet = workbook.getSheetAt(0);
			java.util.List<String> textos = new java.util.ArrayList<>();
			for (Row row : sheet) {
				for (var cell : row) {
					if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
						textos.add(cell.getStringCellValue());
					}
				}
			}

			// cabeçalhos de coluna das tabelas de quebra — não podem faltar
			assertTrue(textos.contains("Banco"), "Faltou o cabeçalho de coluna 'Banco'");
			assertTrue(textos.contains("Quantidade"), "Faltou o cabeçalho de coluna 'Quantidade'");
			assertTrue(textos.contains("Total R$"), "Faltou o cabeçalho de coluna 'Total R$'");
			assertTrue(textos.contains("Comissão Líquida (R$)"), "Faltou o cabeçalho 'Comissão Líquida (R$)'");

			// status e C/V por extenso, não os códigos internos
			assertTrue(textos.contains("Confirmado"), "Status deveria aparecer por extenso ('Confirmado')");
			assertTrue(textos.contains("V (Venda)"), "C/V deveria aparecer por extenso ('V (Venda)')");
			assertTrue(!textos.contains("ANDAMENTO") && !textos.contains("CONFIRMADO"),
					"Não deveria expor o código bruto do status (ANDAMENTO/CONFIRMADO)");
		}
	}

}
