package com.cambia.banco;

import java.math.BigDecimal;

/**
 * Projeção com o que uma listagem de Operações precisa do Banco numa consulta só (achado
 * de eficiência da revisão de 2026-10-08). Desde o Incremento 84 a fórmula de comissão não
 * vem mais daqui: é a do modelo de cálculo escolhido na ordem ({@link CalculoResumo}).
 * Público porque a expressão de construtor do JPQL e o chamador (operacao) ficam fora deste pacote.
 */
public record BancoResumo(String nome, BigDecimal taxaRebate) {
}
