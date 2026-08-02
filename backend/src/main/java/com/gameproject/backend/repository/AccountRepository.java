package com.gameproject.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameproject.backend.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByUsername(String username);

    Optional<Account> findByUsername(String username);

    Optional<Account> findByAuthToken(String authToken);
}
