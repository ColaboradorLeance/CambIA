package com.cambia.banco;

import java.math.BigDecimal;

/**
 * Projeção com tudo que uma listagem de Operações precisa do Banco numa consulta só
 * (antes eram três: nome, fórmula do modelo de cálculo e taxa de rebate — achado de
 * eficiência da revisão de 2026-10-08). Público porque a expressão de construtor do
 * JPQL e o chamador (operacao) ficam fora deste pacote.
 */
public record BancoResumo(String nome, String formula, BigDecimal taxaRebate) {
}
