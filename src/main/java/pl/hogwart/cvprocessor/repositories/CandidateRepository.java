package pl.hogwart.cvprocessor.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.hogwart.cvprocessor.model.Candidate;

import java.util.Optional;

/**
 * For candidates database
 */
@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findBySourceFile(String sourceFile);
}
