package com.cambia.operacao;

import java.util.List;

record QuebrasPorDimensao(
		List<QuebraItem> porBanco,
		List<QuebraItem> porCliente,
		List<QuebraItem> porMoeda,
		List<QuebraItem> porTipo) {
}
