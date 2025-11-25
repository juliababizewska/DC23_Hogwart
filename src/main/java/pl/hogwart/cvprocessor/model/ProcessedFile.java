package pl.hogwart.cvprocessor.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
public class ProcessedFile {
    @Id
    private String filename;
    private String processedAt;
    private String status;
}
