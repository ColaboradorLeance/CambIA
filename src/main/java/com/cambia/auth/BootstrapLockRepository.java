package com.cambia.auth;

import org.springframework.data.jpa.repository.JpaRepository;

interface BootstrapLockRepository extends JpaRepository<BootstrapLock, Integer> {
}
