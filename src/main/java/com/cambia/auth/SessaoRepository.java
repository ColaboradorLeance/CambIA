package com.cambia.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {

	Optional<Sessao> findByToken(String token);

}
