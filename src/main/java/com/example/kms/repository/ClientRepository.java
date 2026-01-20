package com.example.kms.repository;

import com.example.kms.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByEmailHash(String emailHash);
    Optional<Client> findByEmail(String email);
    Optional<Client> findByPhone(String phone);
}

