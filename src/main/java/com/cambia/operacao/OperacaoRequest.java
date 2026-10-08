package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

record OperacaoRequest(
		@NotNull LocalDate data,
		String codigoBanco,
		// Incremento 71: obrigatório só quando prCrVir é "Crédito" — validação condicional
		// no OperacaoService (não dá pra expressar com anotação simples aqui).
		String codigoOperacao,
		@NotNull Long clienteId,
		@NotNull Long bancoId,
		@NotBlank String cv,
		@NotBlank String prCrVir,
		@NotBlank String fundo,
		@NotBlank String spreadEmissao,
		@NotBlank String moeda,
		@NotNull @Positive BigDecimal valorMe,
		// Incremento 72: sem @NotNull — obrigatório só quando prCrVir é "Crédito"
		// (validação condicional no OperacaoService); as regras de positivo/4 casas
		// continuam valendo quando o valor vem preenchido.
		@Positive @Digits(integer = 8, fraction = 4) BigDecimal spotAsset,
		@NotNull @Positive @Digits(integer = 8, fraction = 4) BigDecimal nivelamento,
		@NotNull @Positive BigDecimal taxaFinal) {
}
