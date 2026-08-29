package com.cambia.usuario;

record UsuarioResponse(Long id, String nome, String email, Perfil perfil) {

	static UsuarioResponse from(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
	}

}
