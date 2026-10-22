package com.cambia.chaveapi;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremento 79 (U1–U4 de docs/chaves-api-plano.md): formato, entropia e hash do segredo
 * das chaves de API. Unitário — sem Spring, sem banco.
 */
class GeradorDeChaveApiTests {

	@Test
	void segredoTemPrefixoEEntropia() {
		String segredo = GeradorDeChaveApi.gerarSegredo();

		// 32 bytes aleatórios em base64url sem padding = 43 caracteres, + "cambia_" (7).
		assertEquals(50, segredo.length());
		assertTrue(segredo.matches("^cambia_[A-Za-z0-9_-]{43}$"), segredo);
	}

	@Test
	void doisSegredosNuncaRepetem() {
		Set<String> segredos = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			segredos.add(GeradorDeChaveApi.gerarSegredo());
		}
		assertEquals(1000, segredos.size());
	}

	@Test
	void hashEhDeterministicoEHex() {
		String segredo = GeradorDeChaveApi.gerarSegredo();
		String outro = GeradorDeChaveApi.gerarSegredo();

		assertEquals(GeradorDeChaveApi.hash(segredo), GeradorDeChaveApi.hash(segredo));
		assertTrue(GeradorDeChaveApi.hash(segredo).matches("^[0-9a-f]{64}$"));
		assertNotEquals(GeradorDeChaveApi.hash(segredo), GeradorDeChaveApi.hash(outro));
	}

	@Test
	void prefixoDeExibicaoTemSeisCaracteresDoSegredo() {
		String segredo = GeradorDeChaveApi.gerarSegredo();

		assertEquals("cambia_" + segredo.substring(7, 13), GeradorDeChaveApi.prefixoDeExibicao(segredo));
	}

}
