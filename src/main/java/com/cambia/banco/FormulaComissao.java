package com.cambia.banco;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Fórmula de comissão de um banco, no mesmo estilo das fórmulas de planilha que os
 * bancos já usam (ex: "N*70%-N*70%*4,65%"). Duas variáveis suportadas, confirmadas
 * com o usuário: "N" (Total Bruto do Câmbio) e "R" (Taxa de Rebate do banco). Suporta
 * + - * / %, parênteses, e decimais com vírgula ou ponto. Avaliação própria (sem
 * eval/scripting) por segurança, já que o texto vem de entrada do usuário.
 */
public class FormulaComissao {

	private final String expressao;

	public FormulaComissao(String expressao) {
		this.expressao = expressao;
	}

	public BigDecimal avaliar(BigDecimal totalBrutoCambio, BigDecimal taxaRebate) {
		Parser parser = new Parser(expressao, totalBrutoCambio, taxaRebate);
		BigDecimal resultado = parser.parseExpressao();
		parser.exigirFim();
		return resultado;
	}

	static void validar(String expressao) {
		new FormulaComissao(expressao).avaliar(BigDecimal.ONE, BigDecimal.ONE);
	}

	private static class Parser {
		private final String texto;
		private final BigDecimal n;
		private final BigDecimal r;
		private int pos;

		Parser(String texto, BigDecimal n, BigDecimal r) {
			this.texto = texto == null ? "" : texto.replace(" ", "");
			this.n = n;
			this.r = r;
			this.pos = 0;
		}

		void exigirFim() {
			if (pos != texto.length()) {
				throw new IllegalArgumentException("Fórmula inválida perto de: " + texto.substring(pos));
			}
		}

		BigDecimal parseExpressao() {
			BigDecimal valor = parseTermo();
			while (temProximo() && (atual() == '+' || atual() == '-')) {
				char op = texto.charAt(pos++);
				BigDecimal termo = parseTermo();
				valor = op == '+' ? valor.add(termo) : valor.subtract(termo);
			}
			return valor;
		}

		BigDecimal parseTermo() {
			BigDecimal valor = parseFator();
			while (temProximo() && (atual() == '*' || atual() == '/')) {
				char op = texto.charAt(pos++);
				BigDecimal fator = parseFator();
				valor = op == '*' ? valor.multiply(fator) : valor.divide(fator, MathContext.DECIMAL64);
			}
			return valor;
		}

		BigDecimal parseFator() {
			boolean negativo = false;
			if (temProximo() && atual() == '-') {
				negativo = true;
				pos++;
			}
			BigDecimal valor = parsePrimario();
			if (temProximo() && atual() == '%') {
				pos++;
				valor = valor.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
			}
			return negativo ? valor.negate() : valor;
		}

		BigDecimal parsePrimario() {
			if (!temProximo()) {
				throw new IllegalArgumentException("Fórmula inválida: fim inesperado");
			}
			if (atual() == '(') {
				pos++;
				BigDecimal valor = parseExpressao();
				if (!temProximo() || atual() != ')') {
					throw new IllegalArgumentException("Fórmula inválida: parêntese não fechado");
				}
				pos++;
				return valor;
			}
			if (Character.toUpperCase(atual()) == 'N') {
				pos++;
				return n;
			}
			if (Character.toUpperCase(atual()) == 'R') {
				pos++;
				return r;
			}
			int inicio = pos;
			while (temProximo() && (Character.isDigit(atual()) || atual() == '.' || atual() == ',')) {
				pos++;
			}
			if (pos == inicio) {
				throw new IllegalArgumentException("Fórmula inválida perto de: " + texto.substring(pos));
			}
			String numero = texto.substring(inicio, pos).replace(',', '.');
			return new BigDecimal(numero).round(MathContext.DECIMAL64);
		}

		boolean temProximo() {
			return pos < texto.length();
		}

		char atual() {
			return texto.charAt(pos);
		}
	}

}
