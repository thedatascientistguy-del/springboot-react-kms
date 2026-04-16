package com.example.kms.repository;

import com.example.kms.model.ConversionJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversionJobRepository extends JpaRepository<ConversionJob, UUID> {

    Optional<ConversionJob> findByDownloadToken(String downloadToken);

    List<ConversionJob> findByClient_EmailHash(String emailHash);
}
