package pl.hogwart.cvprocessor.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "education")
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String school;
    private String period;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "education_descriptions", joinColumns = @JoinColumn(name = "education_id"))
    @Column(name = "description")
    private List<String> description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;
}
