package com.cambia.chaveapi;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gestão (criar/listar/revogar) e autenticação por chave de API — Incremento 79, decisões
 * D1–D10 em docs/decisoes.md. Público porque o filtro de autenticação, em
 * com.cambia.security, chama {@link #autenticar(String)}.
 */
@Service
public class ChaveApiService {

	// Expiração é uma DATA de negócio ("vale até dia X"), então "hoje" é o dia em São
	// Paulo — em UTC a chave expiraria às 21h do último dia, o que ninguém esperaria.
	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	private final ChaveApiRepository repository;
	private final UsuarioRepository usuarioRepository;
	private final Clock relogio;

	ChaveApiService(ChaveApiRepository repository, UsuarioRepository usuarioRepository, Clock relogio) {
		this.repository = repository;
		this.usuarioRepository = usuarioRepository;
		this.relogio = relogio;
	}

	ChaveApiCriadaResponse criar(ChaveApiRequest request, Long adminId) {
		Usuario usuario = usuarioRepository.findById(request.usuarioId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuário vinculado não existe"));
		// D3: menor privilégio — uma chave vazada nunca deve conseguir administrar o sistema.
		if (usuario.getPerfil() == Perfil.ADMIN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Chave de API não pode ser vinculada a um Admin — use um usuário Analista ou Consultor");
		}
		if (request.expiraEm() != null && request.expiraEm().isBefore(hoje())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de expiração não pode estar no passado");
		}

		String segredo = GeradorDeChaveApi.gerarSegredo();
		ChaveApi chave = repository.save(new ChaveApi(request.nome().trim(), usuario.getId(),
				GeradorDeChaveApi.prefixoDeExibicao(segredo), GeradorDeChaveApi.hash(segredo), relogio.instant(),
				adminId, request.expiraEm()));

		return new ChaveApiCriadaResponse(chave.getId(), chave.getNome(), chave.getPrefixo(), segredo,
				usuario.getId(), usuario.getNome(), chave.getCriadaEm(), chave.getExpiraEm());
	}

	List<ChaveApiResponse> listar() {
		LocalDate hoje = hoje();
		List<ChaveApi> chaves = repository.findAllByOrderByCriadaEmDescIdDesc();
		Set<Long> ids = new HashSet<>();
		for (ChaveApi chave : chaves) {
			ids.add(chave.getUsuarioId());
			ids.add(chave.getCriadaPorUsuarioId());
			ids.add(chave.getRevogadaPorUsuarioId());
		}
		// Uma consulta só pra todos os nomes, em vez de um findById por usuário distinto.
		Map<Long, String> nomes = new HashMap<>();
		ids.remove(null);
		usuarioRepository.findAllById(ids).forEach(usuario -> nomes.put(usuario.getId(), usuario.getNome()));
		return chaves.stream()
				.map(chave -> new ChaveApiResponse(chave.getId(), chave.getNome(), chave.getPrefixo(),
						chave.getUsuarioId(), nomes.get(chave.getUsuarioId()),
						nomes.get(chave.getCriadaPorUsuarioId()), chave.getCriadaEm(), chave.getExpiraEm(),
						chave.getUltimoUsoEm(), chave.getRevogadaEm(), nomes.get(chave.getRevogadaPorUsuarioId()),
						chave.isAtiva(hoje)))
				.toList();
	}

	void revogar(Long id, Long adminId) {
		ChaveApi chave = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chave de API não encontrada"));
		// D5: revogação é definitiva; revogar de novo seria apagar quem/quando revogou.
		if (chave.isRevogada()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de API já está revogada");
		}
		chave.revogar(adminId, relogio.instant());
		repository.save(chave);
	}

	/**
	 * @return o usuário em nome de quem a chave age, ou vazio se ela não existe, foi
	 * revogada ou expirou — o chamador não distingue os três casos de propósito (não dar a
	 * quem testa chaves a informação de qual delas já existiu).
	 */
	public Optional<Usuario> autenticar(String segredo) {
		if (!GeradorDeChaveApi.temFormatoValido(segredo)) {
			return Optional.empty();
		}
		Instant agora = relogio.instant();
		LocalDate hoje = LocalDate.ofInstant(agora, FUSO);
		return repository.findByHash(GeradorDeChaveApi.hash(segredo))
				.filter(chave -> chave.isAtiva(hoje))
				.flatMap(chave -> {
					if (chave.registrarUso(agora)) {
						repository.atualizarUltimoUso(chave.getId(), agora);
					}
					return usuarioRepository.findById(chave.getUsuarioId());
				})
				// D3 vale no uso, não só na criação: se o usuário vinculado foi promovido a
				// Admin depois, a chave deixa de valer em vez de herdar o perfil de Admin.
				.filter(usuario -> usuario.getPerfil() != Perfil.ADMIN);
	}

	private LocalDate hoje() {
		return LocalDate.ofInstant(relogio.instant(), FUSO);
	}

}
