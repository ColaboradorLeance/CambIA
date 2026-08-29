package com.cambia.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.cambia.usuario.Perfil;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService {

	private static final Duration VALIDADE_MAGIC_LINK = Duration.ofMinutes(15);
	private static final Duration VALIDADE_SESSAO = Duration.ofHours(8);

	private final UsuarioRepository usuarioRepository;
	private final MagicLinkTokenRepository magicLinkTokenRepository;
	private final SessaoRepository sessaoRepository;
	private final MagicLinkSender sender;

	AuthService(UsuarioRepository usuarioRepository, MagicLinkTokenRepository magicLinkTokenRepository,
			SessaoRepository sessaoRepository, MagicLinkSender sender) {
		this.usuarioRepository = usuarioRepository;
		this.magicLinkTokenRepository = magicLinkTokenRepository;
		this.sessaoRepository = sessaoRepository;
		this.sender = sender;
	}

	UsuarioLogado bootstrapAdmin(BootstrapAdminRequest request) {
		if (usuarioRepository.count() > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Já existem usuários cadastrados; use um usuário Admin existente para criar novos usuários");
		}
		Usuario admin = usuarioRepository.save(new Usuario(request.nome(), request.email(), Perfil.ADMIN));
		return UsuarioLogado.from(admin);
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
