package com.cambia.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, Long> {

	Optional<MagicLinkToken> findByToken(String token);

	Optional<MagicLinkToken> findByUsuarioId(Long usuarioId);

}
