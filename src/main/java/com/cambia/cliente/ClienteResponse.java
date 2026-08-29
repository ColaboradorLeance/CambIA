package com.cambia.cliente;

record ClienteResponse(Long id, String nome, String documento) {

	static ClienteResponse from(Cliente cliente) {
		return new ClienteResponse(cliente.getId(), cliente.getNome(), cliente.getDocumento());
	}

}
