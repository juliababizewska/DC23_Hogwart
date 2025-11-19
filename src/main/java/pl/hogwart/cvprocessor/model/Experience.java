package pl.hogwart.cvprocessor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "experience")
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String period;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "experience_tasks", joinColumns = @JoinColumn(name = "experience_id"))
    @Column(name = "task")
    private List<String> tasks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;
}