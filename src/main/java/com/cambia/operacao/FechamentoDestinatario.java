package com.cambia.operacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fechamento_destinatarios")
class FechamentoDestinatario {

	@Id
	@Column(name = "usuario_id")
	private Long usuarioId;

	protected FechamentoDestinatario() {
	}

	FechamentoDestinatario(Long usuarioId) {
		this.usuarioId = usuarioId;
	}

	Long getUsuarioId() {
		return usuarioId;
	}

}
