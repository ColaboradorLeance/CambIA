package com.cambia.operacao;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

class PeriodoResolver {

	private PeriodoResolver() {
	}

	static IntervaloDatas resolver(Periodo periodo, LocalDate hoje) {
		return switch (periodo) {
			case HOJE -> new IntervaloDatas(hoje, hoje);
			case SEMANA -> new IntervaloDatas(hoje.with(DayOfWeek.MONDAY), hoje.with(DayOfWeek.SUNDAY));
			case MES -> new IntervaloDatas(hoje.withDayOfMonth(1), hoje.with(TemporalAdjusters.lastDayOfMonth()));
			case ANO -> new IntervaloDatas(hoje.withDayOfYear(1), hoje.with(TemporalAdjusters.lastDayOfYear()));
		};
	}

	static IntervaloDatas anterior(Periodo periodo, LocalDate hoje) {
		LocalDate referencia = switch (periodo) {
			case HOJE -> hoje.minusDays(1);
			case SEMANA -> hoje.minusWeeks(1);
			case MES -> hoje.minusMonths(1);
			case ANO -> hoje.minusYears(1);
		};
		return resolver(periodo, referencia);
	}

}
