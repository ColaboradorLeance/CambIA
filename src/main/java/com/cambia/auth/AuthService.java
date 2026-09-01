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
	private final MagicLinkEnvioAssincrono envioAssincrono;

	AuthService(UsuarioRepository usuarioRepository, MagicLinkTokenRepository magicLinkTokenRepository,
			SessaoRepository sessaoRepository, BootstrapLockRepository bootstrapLockRepository,
			MagicLinkEnvioAssincrono envioAssincrono) {
		this.usuarioRepository = usuarioRepository;
		this.magicLinkTokenRepository = magicLinkTokenRepository;
		this.sessaoRepository = sessaoRepository;
		this.bootstrapLockRepository = bootstrapLockRepository;
		this.envioAssincrono = envioAssincrono;
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

	// Achado de revisão de segurança: o envio roda em segundo plano (MagicLinkEnvioAssincrono)
	// pra não deixar o tempo de resposta variar conforme o e-mail existe ou não — a parte
	// mais lenta (o SMTP de verdade) deixa de bloquear a resposta HTTP.
	//
	// Achado de revisão de segurança ("login CSRF"): vinculo (pode ser null — ver
	// AuthController.usaCookieDeVinculo) é gravado junto do token pra ser exigido de volta
	// na hora de verificar, ver MagicLinkToken.vinculoCompativel.
	void solicitarLink(String email, String vinculo) {
		usuarioRepository.findByEmail(email).ifPresent(usuario -> {
			String token = UUID.randomUUID().toString();
			MagicLinkToken magicLinkToken = new MagicLinkToken(usuario.getId(), token,
					Instant.now().plus(VALIDADE_MAGIC_LINK), vinculo);
			magicLinkTokenRepository.save(magicLinkToken);
			envioAssincrono.enviar(email, token);
		});
	}

	// Achado de revisão de segurança ("login CSRF" em GET /auth/verify): sem isso, um
	// atacante conseguia pedir seu próprio link mágico e induzir a vítima (clicando num
	// link ou colando um código repassado por engenharia social) a completá-lo — a vítima
	// era autenticada NA CONTA DO ATACANTE sem perceber, e qualquer dado que digitasse
	// depois ficava visível pro atacante ao voltar pra própria conta. vinculoDoCookie
	// precisa bater com o vinculo gravado junto do token (ver solicitarLink) — só o
	// navegador que pediu o link consegue completá-lo.
	LoginResponse verificar(String token, String vinculoDoCookie) {
		MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByToken(token)
				.filter(t -> t.isValido(Instant.now()))
				.filter(t -> t.vinculoCompativel(vinculoDoCookie))
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
