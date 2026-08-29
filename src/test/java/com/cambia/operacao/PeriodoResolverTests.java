package com.cambia.operacao;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodoResolverTests {

	@Test
	void hojeRetornaSoOMesmoDia() {
		IntervaloDatas intervalo = PeriodoResolver.resolver(Periodo.HOJE, LocalDate.of(2026, 8, 26));

		assertEquals(LocalDate.of(2026, 8, 26), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 8, 26), intervalo.fim());
	}

	@Test
	void semanaVaiDeSegundaADomingo() {
		// 2026-08-26 é uma quarta-feira
		IntervaloDatas intervalo = PeriodoResolver.resolver(Periodo.SEMANA, LocalDate.of(2026, 8, 26));

		assertEquals(LocalDate.of(2026, 8, 24), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 8, 30), intervalo.fim());
	}

	@Test
	void mesVaiDoPrimeiroAoUltimoDiaDoMes() {
		IntervaloDatas intervalo = PeriodoResolver.resolver(Periodo.MES, LocalDate.of(2026, 2, 15));

		assertEquals(LocalDate.of(2026, 2, 1), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 2, 28), intervalo.fim());
	}

	@Test
	void anoVaiDoPrimeiroAoUltimoDiaDoAno() {
		IntervaloDatas intervalo = PeriodoResolver.resolver(Periodo.ANO, LocalDate.of(2026, 8, 26));

		assertEquals(LocalDate.of(2026, 1, 1), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 12, 31), intervalo.fim());
	}

	@Test
	void anteriorDeHojeEOntem() {
		IntervaloDatas intervalo = PeriodoResolver.anterior(Periodo.HOJE, LocalDate.of(2026, 8, 26));

		assertEquals(LocalDate.of(2026, 8, 25), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 8, 25), intervalo.fim());
	}

	@Test
	void anteriorDeSemanaEASemanaAnterior() {
		IntervaloDatas intervalo = PeriodoResolver.anterior(Periodo.SEMANA, LocalDate.of(2026, 8, 26));

		assertEquals(LocalDate.of(2026, 8, 17), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 8, 23), intervalo.fim());
	}

	@Test
	void anteriorDeMesEOMesCalendarioAnteriorInteiro() {
		// 1º de março -> mês anterior é fevereiro inteiro (28 dias em 2026, ano não bissexto)
		IntervaloDatas intervalo = PeriodoResolver.anterior(Periodo.MES, LocalDate.of(2026, 3, 1));

		assertEquals(LocalDate.of(2026, 2, 1), intervalo.inicio());
		assertEquals(LocalDate.of(2026, 2, 28), intervalo.fim());
	}

	@Test
	void anteriorDeAnoEOAnoCalendarioAnterior() {
		IntervaloDatas intervalo = PeriodoResolver.anterior(Periodo.ANO, LocalDate.of(2026, 8, 26));

		assertEquals(LocalDate.of(2025, 1, 1), intervalo.inicio());
		assertEquals(LocalDate.of(2025, 12, 31), intervalo.fim());
	}

}
