package com.cambia.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService {

	private static final Duration VALIDADE_MAGIC_LINK = Duration.ofMinutes(15);
	private static final Duration VALIDADE_SESSAO = Duration.ofHours(8);
	private static final int ID_TRAVA_BOOTSTRAP = 1;
	private static final String MENSAGEM_JA_INICIALIZADO =
			"Já existem usuários cadastrados; use um usuário Admin existente para criar novos usuários";

	private final UsuarioRepository usuarioRepository;
	private final MagicLinkTokenRepository magicLinkTokenRepository;
	private final SessaoRepository sessaoRepository;
	private final BootstrapLockRepository bootstrapLockRepository;
	private final MagicLinkSender sender;

	AuthService(UsuarioRepository usuarioRepository, MagicLinkTokenRepository magicLinkTokenRepository,
			SessaoRepository sessaoRepository, BootstrapLockRepository bootstrapLockRepository,
			MagicLinkSender sender) {
		this.usuarioRepository = usuarioRepository;
		this.magicLinkTokenRepository = magicLinkTokenRepository;
		this.sessaoRepository = sessaoRepository;
		this.bootstrapLockRepository = bootstrapLockRepository;
		this.sender = sender;
	}

	// Achado de revisão de segurança: "count() > 0, depois insere" sozinho não é atômico —
	// duas requisições concorrentes no instante em que ainda não existe nenhum usuário
	// podiam, as duas, passar pela checagem e criar um Admin cada uma. A trava por chave
	// primária (bootstrap_lock, id fixo) fecha essa janela: só uma requisição consegue
	// inserir a linha; a outra recebe uma violação de chave e vira 409, sem depender de
	// timing. A checagem por count() continua existindo pra cobrir o caso de já existir
	// usuário cadastrado por outro caminho (não passou por aqui).
	UsuarioLogado bootstrapAdmin(BootstrapAdminRequest request) {
		if (usuarioRepository.count() > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, MENSAGEM_JA_INICIALIZADO);
		}
		try {
			bootstrapLockRepository.saveAndFlush(new BootstrapLock(ID_TRAVA_BOOTSTRAP));
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, MENSAGEM_JA_INICIALIZADO);
		}
		Usuario admin = usuarioRepository.save(new Usuario(request.nome(), request.email(), Perfil.ADMIN));
		return UsuarioLogado.from(admin);
	}

	// Achado de revisão de segurança: o "logout" só limpava o token no navegador — a
	// sessão continuava válida no servidor até a expiração (8h). Chamado por
	// DELETE /auth/sessao; idempotente (não erra se o token já não existir/for inválido).
	void encerrarSessao(String token) {
		sessaoRepository.findByToken(token).ifPresent(sessaoRepository::delete);
	}

	void solicitarLink(String email) {
		usuarioRepository.findByEmail(email).ifPresent(usuario -> {
			String token = UUID.randomUUID().toString();
			MagicLinkToken magicLinkToken = new MagicLinkToken(usuario.getId(), token,
					Instant.now().plus(VALIDADE_MAGIC_LINK));
			magicLinkTokenRepository.save(magicLinkToken);
			sender.enviar(email, token);
		});
	}

	LoginResponse verificar(String token) {
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByToken(token)
				.filter(t -> t.isValido(Instant.now()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Link inválido ou expirado"));

		magicLinkToken.marcarUsado();
		magicLinkTokenRepository.save(magicLinkToken);

		Usuario usuario = usuarioRepository.findById(magicLinkToken.getUsuarioId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));

		Sessao sessao = new Sessao(usuario.getId(), UUID.randomUUID().toString(),
				Instant.now().plus(VALIDADE_SESSAO));
		sessaoRepository.save(sessao);

		return new LoginResponse(sessao.getToken(), UsuarioLogado.from(usuario));
	}

}
