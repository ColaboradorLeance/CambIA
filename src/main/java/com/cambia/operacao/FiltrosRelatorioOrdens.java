package com.cambia.operacao;

/** Filtros opcionais do Relatório de Ordens — os mesmos na tela e na exportação. */
record FiltrosRelatorioOrdens(Long clienteId, Long bancoId, String moeda, String cv, StatusOperacao status,
		Long criadoPorUsuarioId, Long completadoPorUsuarioId) {
}
