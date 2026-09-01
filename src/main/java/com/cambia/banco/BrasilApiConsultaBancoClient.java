package com.cambia.banco;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Consulta a BrasilAPI (https://brasilapi.com.br/api/banks/v1) — fonte pública,
 * gratuita, sem chave de acesso, mantida sincronizada com a lista de participantes
 * do Banco Central. Qualquer falha (código não encontrado, fora do ar, timeout) é
 * tratada como "sem resultado" (Optional vazio), nunca propagada como erro — ver
 * {@link ConsultaBancoClient}.
 */
@Component
class BrasilApiConsultaBancoClient implements ConsultaBancoClient {

	private static final String BASE_URL = "https://brasilapi.com.br/api/banks/v1";

	private final RestClient restClient;

	BrasilApiConsultaBancoClient() {
		this(RestClient.builder());
	}

	BrasilApiConsultaBancoClient(RestClient.Builder builder) {
		this.restClient = builder.baseUrl(BASE_URL).build();
	}

	@Override
	public Optional<String> buscarNomePorCompe(String codigoCompe) {
		try {
			Resposta resposta = restClient.get().uri("/{codigo}", codigoCompe).retrieve().body(Resposta.class);
			return nomeDe(resposta);
		} catch (RestClientException e) {
			return Optional.empty();
		}
	}

	@Override
	public Optional<String> buscarNomePorIspb(String ispb) {
		try {
			// A BrasilAPI não tem um endpoint de busca direta por ISPB — só o código COMPE
			// via path variable. Busca na lista completa e filtra pelo ISPB.
			List<Resposta> bancos = restClient.get().uri("").retrieve().body(RESPOSTA_LISTA);
			if (bancos == null) {
				return Optional.empty();
			}
			return bancos.stream()
					.filter(b -> ispb.equals(b.ispb()))
					.findFirst()
					.flatMap(BrasilApiConsultaBancoClient::nomeDe);
		} catch (RestClientException e) {
			return Optional.empty();
		}
	}

	private static Optional<String> nomeDe(Resposta resposta) {
		if (resposta == null) {
			return Optional.empty();
		}
		String nome = temTexto(resposta.fullName()) ? resposta.fullName() : resposta.name();
		return temTexto(nome) ? Optional.of(nome) : Optional.empty();
	}

	private static boolean temTexto(String valor) {
		return valor != null && !valor.isBlank();
	}

	private static final org.springframework.core.ParameterizedTypeReference<List<Resposta>> RESPOSTA_LISTA =
			new org.springframework.core.ParameterizedTypeReference<>() {
			};

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Resposta(
			String ispb,
			String name,
			@JsonProperty("fullName") String fullName) {
	}

}
