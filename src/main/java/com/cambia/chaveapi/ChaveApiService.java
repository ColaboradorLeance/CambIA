package com.cambia.chaveapi;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
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
 * Gestão e autenticação por chave de API. Incremento 79 (D1–D10) com as mudanças do
 * Incremento 81 (R1–R3, K1–K11 em docs/decisoes.md): cada usuário cria, lista e revoga as
 * próprias chaves (K1), a chave age como o dono (K2), tem escopos (K4) e expira em até 1
 * ano (K5); o Admin vê todas e revoga qualquer uma, mas não cria para outros (K7). Público
 * porque o filtro de autenticação, em com.cambia.security, chama {@link #autenticar(String)},
 * e o histórico de ordens consulta {@link #nomesPorId(Collection)}.
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

	// ── Do próprio usuário (/perfil/chaves-api) ─────────────────────────────────────────

	ChaveApiCriadaResponse criar(PerfilChaveApiRequest request, Usuario dono) {
		Set<EscopoChaveApi> escopos = lerEscopos(request.escopos());
		// K6: a chave nunca faz mais do que o dono faria pela tela — Consultor só consulta.
		if (dono.getPerfil() == Perfil.CONSULTOR && escopos.contains(EscopoChaveApi.CRIAR_ORDENS)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Consultor não pode criar chave com o escopo Criar ordens — o perfil Consultor só consulta");
		}
		LocalDate hoje = hoje();
		LocalDate limite = hoje.plusYears(1);
		// K5 (resposta do cliente: "Deixe 1 ano"): obrigatória, padrão e máximo de 1 ano.
		LocalDate expiraEm = request.expiraEm() == null ? limite : request.expiraEm();
		if (expiraEm.isBefore(hoje)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de expiração não pode estar no passado");
		}
		if (expiraEm.isAfter(limite)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Data de expiração pode ser no máximo daqui a 1 ano (" + limite + ")");
		}

		String segredo = GeradorDeChaveApi.gerarSegredo();
		ChaveApi chave = repository.save(new ChaveApi(request.nome().trim(), dono.getId(),
				GeradorDeChaveApi.prefixoDeExibicao(segredo), GeradorDeChaveApi.hash(segredo), relogio.instant(),
				expiraEm, escopos));

		return new ChaveApiCriadaResponse(chave.getId(), chave.getNome(), chave.getPrefixo(), segredo,
				EscopoChaveApi.nomes(chave.getEscopos()), chave.getCriadaEm(), chave.getExpiraEm());
	}

	List<PerfilChaveApiResponse> listarDoUsuario(Long donoId) {
		LocalDate hoje = hoje();
		return repository.findByUsuarioIdOrderByCriadaEmDescIdDesc(donoId).stream()
				.map(chave -> new PerfilChaveApiResponse(chave.getId(), chave.getNome(), chave.getPrefixo(),
						EscopoChaveApi.nomes(chave.getEscopos()), chave.getCriadaEm(), chave.getExpiraEm(),
						chave.getUltimoUsoEm(), chave.getRevogadaEm(), chave.status(hoje)))
				.toList();
	}

	void revogarDoUsuario(Long id, Long donoId) {
		// 404 também para a chave de outro usuário: não revelar a quem tenta que ela existe.
		ChaveApi chave = repository.findByIdAndUsuarioId(id, donoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chave de API não encontrada"));
		revogar(chave, donoId);
	}

	// ── Do Admin (/chaves-api, K7) ───────────────────────────────────────────────────────

	List<ChaveApiResponse> listarTodas() {
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
						EscopoChaveApi.nomes(chave.getEscopos()), chave.getUsuarioId(), nomes.get(chave.getUsuarioId()),
						nomes.get(chave.getCriadaPorUsuarioId()), chave.getCriadaEm(), chave.getExpiraEm(),
						chave.getUltimoUsoEm(), chave.getRevogadaEm(), nomes.get(chave.getRevogadaPorUsuarioId()),
						chave.isAtiva(hoje), chave.status(hoje)))
				.toList();
	}

	void revogarQualquer(Long id, Long adminId) {
		ChaveApi chave = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chave de API não encontrada"));
		revogar(chave, adminId);
	}

	private void revogar(ChaveApi chave, Long quemRevoga) {
		// D5: revogação é definitiva; revogar de novo seria apagar quem/quando revogou. A
		// checagem vale também para duas revogações simultâneas (dono e Admin): é o UPDATE
		// condicional que decide, não a leitura feita antes.
		if (chave.isRevogada() || repository.revogarSeAtiva(chave.getId(), quemRevoga, relogio.instant()) == 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de API já está revogada");
		}
	}

	// ── Uso da chave ─────────────────────────────────────────────────────────────────────

	/**
	 * @return o dono e o que a chave pode, ou vazio se ela não existe, foi revogada ou
	 * expirou — o chamador não distingue os três casos de propósito (não dar a quem testa
	 * chaves a informação de qual delas já existiu).
	 *
	 * <p>Incremento 81 (K3): dono Admin não invalida mais a chave. O que limita uma chave é o
	 * escopo (K4) e a D7, conferidos no filtro.
	 */
	public Optional<ChaveAutenticada> autenticar(String segredo) {
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
					return usuarioRepository.findById(chave.getUsuarioId())
							.map(dono -> new ChaveAutenticada(dono, chave.getId(), chave.getNome(), chave.getEscopos()));
				});
	}

	/** Nomes das chaves por id, para o histórico de ordens mostrar qual chave agiu (K9). */
	public Map<Long, String> nomesPorId(Collection<Long> ids) {
		Map<Long, String> nomes = new HashMap<>();
		repository.findAllById(ids).forEach(chave -> nomes.put(chave.getId(), chave.getNome()));
		return nomes;
	}

	private static Set<EscopoChaveApi> lerEscopos(List<String> nomes) {
		if (nomes == null || nomes.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Escolha pelo menos um escopo: LEITURA (consultar) ou CRIAR_ORDENS (criar ordens)");
		}
		Set<EscopoChaveApi> escopos = EnumSet.noneOf(EscopoChaveApi.class);
		for (String nome : nomes) {
			try {
				escopos.add(EscopoChaveApi.valueOf(nome == null ? "" : nome.trim()));
			} catch (IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Escopo inválido: use LEITURA ou CRIAR_ORDENS");
			}
		}
		return escopos;
	}

	private LocalDate hoje() {
		return LocalDate.ofInstant(relogio.instant(), FUSO);
	}

}
