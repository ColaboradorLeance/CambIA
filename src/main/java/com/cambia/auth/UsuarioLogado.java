package com.cambia.auth;

import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;

record UsuarioLogado(Long id, String nome, String email, Perfil perfil) {

	static UsuarioLogado from(Usuario usuario) {
		return new UsuarioLogado(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
	}

}
