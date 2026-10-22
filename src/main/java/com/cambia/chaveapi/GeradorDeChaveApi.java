package com.cambia.chaveapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Segredo e hash das chaves de API (Incremento 79). Público porque o filtro de
 * autenticação, em com.cambia.security, precisa calcular o hash do header recebido.
 *
 * <p>SHA-256 puro (sem sal, sem bcrypt/argon2) é deliberado: o segredo tem 256 bits de
 * entropia, então não há dicionário nem força bruta viável — funções lentas só fazem
 * sentido pra segredos de baixa entropia, como senhas humanas. E um hash determinístico é
 * o que permite achar a chave por índice único, sem varrer a tabela.
 */
public final class GeradorDeChaveApi {

	static final String PREFIXO = "cambia_";
	private static final int BYTES_DE_ENTROPIA = 32;
	private static final int CARACTERES_EXIBIDOS = 6;
	private static final SecureRandom ALEATORIO = new SecureRandom();
	private static final Pattern FORMATO = Pattern.compile("^cambia_[A-Za-z0-9_-]{43}$");

	private GeradorDeChaveApi() {
	}

	public static String gerarSegredo() {
		byte[] bytes = new byte[BYTES_DE_ENTROPIA];
		ALEATORIO.nextBytes(bytes);
		return PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String segredo) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 é obrigatório em toda JVM (especificação do MessageDigest).
			throw new IllegalStateException(e);
		}
	}

	/** Rótulo seguro de exibir: o prefixo fixo + 6 caracteres do segredo, insuficiente pra usar a chave. */
	public static String prefixoDeExibicao(String segredo) {
		return segredo.substring(0, PREFIXO.length() + CARACTERES_EXIBIDOS);
	}

	/**
	 * Descarta, sem consultar o banco, o que nem tem o formato de uma chave gerada aqui —
	 * header vazio, lixo, tentativa de injeção —, antes de calcular hash e fazer o lookup.
	 */
	static boolean temFormatoValido(String segredo) {
		return segredo != null && FORMATO.matcher(segredo).matches();
	}

}
