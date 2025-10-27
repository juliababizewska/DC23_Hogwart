package pl.hogwart.cvprocessor.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.hogwart.cvprocessor.model.Candidate;

/**
 * For candidates database
 */
@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {}
