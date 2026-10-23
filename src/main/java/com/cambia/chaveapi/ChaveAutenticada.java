package com.cambia.chaveapi;

import java.util.Set;

import com.cambia.usuario.Usuario;

/** Resultado de uma autenticação por chave: o dono (em nome de quem a chave age) e o que ela pode. */
public record ChaveAutenticada(Usuario dono, Long chaveId, String chaveNome, Set<EscopoChaveApi> escopos) {

	public boolean permite(String metodo, String caminho) {
		return EscopoChaveApi.algumPermite(escopos, metodo, caminho);
	}

}
