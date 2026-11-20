package com.java.dev.jim.personal.repository;

import com.java.dev.jim.personal.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Optional<ImportBatch> findByFileHash(String fileHash);
}
