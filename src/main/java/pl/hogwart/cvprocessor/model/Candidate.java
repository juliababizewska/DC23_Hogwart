package pl.hogwart.cvprocessor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 *  A template class representing a candidate
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "candidates")
public class Candidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String full_name;
    private String email;
    private String phone;
    private String position;
    private boolean meetsRequirements;
    private double score;
    private String sourceFile;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String profile;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "skill")
    private List<String> skills;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_languages", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "languages")
    private List<String> languages;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_achievements", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "achievements")
    private List<String> achievements;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_interests", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "interests")
    private List<String> interests;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_footer", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "footer", columnDefinition = "TEXT")
    private List<String> footer;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Education> education;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Experience> experience;

}

