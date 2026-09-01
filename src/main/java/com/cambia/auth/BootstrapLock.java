package com.cambia.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Linha única (id fixo) usada só pra travar o bootstrap do primeiro Admin de forma
 * atômica — ver {@link AuthService#bootstrapAdmin} e a migração V17. Não representa
 * nenhum conceito de negócio, é puramente um mecanismo de trava via chave primária.
 */
@Entity
@Table(name = "bootstrap_lock")
class BootstrapLock {

	@Id
	private Integer id;

	protected BootstrapLock() {
	}

	BootstrapLock(Integer id) {
		this.id = id;
	}

}
