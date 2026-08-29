package com.cambia.usuario;

public class UsuarioTestFactory {

	public static Usuario novo(String nome, String email, Perfil perfil) {
		return new Usuario(nome, email, perfil);
	}

}
