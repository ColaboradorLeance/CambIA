package com.cambia.exportacao;

import java.awt.Color;

/**
 * Cores dos arquivos exportados (Incremento 83) — as mesmas da interface (frontend/src/
 * index.css: --accent #4f46e5), pra PDF e planilha parecerem parte do mesmo sistema.
 * Cor só onde ajuda a ler: faixa de título, cabeçalho de tabela, linhas alternadas,
 * negativos e situação da ordem.
 */
final class Paleta {

	static final Color MARCA = new Color(0x4F, 0x46, 0xE5);
	static final Color MARCA_SUAVE = new Color(0xEE, 0xF2, 0xFF);
	static final Color MARCA_CLARA = new Color(0xC7, 0xD2, 0xFE);
	static final Color TEXTO = new Color(0x1F, 0x29, 0x37);
	static final Color TEXTO_SECUNDARIO = new Color(0x6B, 0x72, 0x80);
	static final Color LINHA_ALTERNADA = new Color(0xF8, 0xFA, 0xFC);
	static final Color BORDA = new Color(0xE5, 0xE7, 0xEB);
	static final Color BRANCO = Color.WHITE;
	static final Color POSITIVO = new Color(0x15, 0x80, 0x3D);
	static final Color NEGATIVO = new Color(0xB9, 0x1C, 0x1C);
	static final Color ATENCAO = new Color(0xB4, 0x53, 0x09);

	private Paleta() {
	}

	/** Cor do texto de uma situação de ordem; {@code null} = cor normal. */
	static Color corDoStatus(String status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case "Confirmado" -> POSITIVO;
			case "Cancelado" -> NEGATIVO;
			case "Em andamento" -> ATENCAO;
			default -> null;
		};
	}

	static byte[] rgb(Color cor) {
		return new byte[] { (byte) cor.getRed(), (byte) cor.getGreen(), (byte) cor.getBlue() };
	}

}
