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

    private String fullname;
    private String email;
    private String phone;
    private String profile;
    private String footer;
    private String position;
    private boolean meetsRequirements;
    private double score;
    private String sourceFile;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "skill")
    private List<String> skills;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_languages", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "languages")
    private List<String> languages;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_education", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "education")
    private List<String> education;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_achievements", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "achievements")
    private List<String> achievements;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_interests", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "interests")
    private List<String> interests;

//    @ElementCollection
//    @CollectionTable(name = "candidate_experience", joinColumns = @JoinColumn(name = "candidate_id"))
//    private List<Experience> experience;

    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Experience> experience;

}

