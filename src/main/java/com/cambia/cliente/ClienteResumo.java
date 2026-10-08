package com.cambia.cliente;

/**
 * Projeção com o que uma listagem de Operações precisa do Cliente numa consulta só
 * (antes eram duas: nome e documento — achado de eficiência da revisão de 2026-10-08).
 * Público pelo mesmo motivo do BancoResumo: JPQL e chamador fora do pacote.
 */
public record ClienteResumo(String nome, String documento) {
}
