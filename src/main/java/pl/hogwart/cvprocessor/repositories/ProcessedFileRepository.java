package pl.hogwart.cvprocessor.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.hogwart.cvprocessor.model.ProcessedFile;

public interface ProcessedFileRepository extends JpaRepository<ProcessedFile, String> {
}
