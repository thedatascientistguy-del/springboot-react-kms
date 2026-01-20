package com.example.kms.repository;

import com.example.kms.model.EncryptedData;
import com.example.kms.model.Client;
import com.example.kms.model.DataType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncryptedDataRepository extends JpaRepository<EncryptedData, Long> {
    List<EncryptedData> findByClient(Client client);

    // NEW: fetch records by client and storage type
    List<EncryptedData> findByClientAndDataType(Client client, DataType dataType);
}
