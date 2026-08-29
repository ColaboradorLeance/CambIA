package com.cambia.auth;

interface MagicLinkSender {

	void enviar(String email, String token);

}
