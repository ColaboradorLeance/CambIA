package com.cambia.exportacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Texto em pt-BR de cada tipo de coluna — o mesmo que as telas mostram ("1.234,56",
 * "2,0%", "23/10/2026"). Os relatórios só existem no fuso de São Paulo.
 */
public final class FormatoBrasileiro {

	public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
	public static final String VAZIO = "—";

	private static final Locale PT_BR = Locale.of("pt", "BR");
	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private FormatoBrasileiro() {
	}

	public static String formatar(Object valor, TipoColuna tipo) {
		if (valor == null) {
			return VAZIO;
		}
		return switch (tipo) {
			case TEXTO, STATUS -> valor.toString();
			case INTEIRO -> numero(valor, 0, 0);
			case DECIMAL -> numero(valor, 2, 2);
			case TAXA -> numero(valor, 4, 4);
			case DECIMAL3 -> numero(valor, 3, 3);
			case PERCENTUAL -> numero(new BigDecimal(valor.toString()).multiply(BigDecimal.valueOf(100)), 1, 3) + "%";
			case DATA -> data((LocalDate) valor);
			case DATA_HORA -> dataHora((Instant) valor);
			case AUTO -> formatar(valor, tipoDe(valor));
		};
	}

	/** Tipo implícito de um valor solto (tabelas "indicador / valor"). */
	public static TipoColuna tipoDe(Object valor) {
		if (valor instanceof Integer || valor instanceof Long) {
			return TipoColuna.INTEIRO;
		}
		if (valor instanceof BigDecimal) {
			return TipoColuna.DECIMAL;
		}
		if (valor instanceof LocalDate) {
			return TipoColuna.DATA;
		}
		if (valor instanceof Instant) {
			return TipoColuna.DATA_HORA;
		}
		return TipoColuna.TEXTO;
	}

	public static String data(LocalDate data) {
		return data == null ? VAZIO : DATA.format(data);
	}

	public static String dataHora(Instant instante) {
		return instante == null ? VAZIO : DATA_HORA.format(instante.atZone(FUSO));
	}

	public static String intervalo(LocalDate inicio, LocalDate fim) {
		return data(inicio) + " a " + data(fim);
	}

	public static String dinheiro(BigDecimal valor) {
		return valor == null ? VAZIO : numero(valor, 2, 2);
	}

	private static String numero(Object valor, int minimoCasas, int maximoCasas) {
		DecimalFormat formato = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(PT_BR));
		formato.setMinimumFractionDigits(minimoCasas);
		formato.setMaximumFractionDigits(maximoCasas);
		formato.setRoundingMode(RoundingMode.HALF_UP);
		return formato.format(new BigDecimal(valor.toString()));
	}

}
