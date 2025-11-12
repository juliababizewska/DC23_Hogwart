package pl.hogwart.cvprocessor.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.hogwart.cvprocessor.model.Applicant;
import pl.hogwart.cvprocessor.model.Candidate;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Class responsible for CV processing
 * Sends file to the pythonIntegrationService and maps received data to Candidate object
 */
@Service
public class CVService {

    private final PythonIntegrationService pythonService;
    private final CandidateService candidateService;
    private final CloudService cloudService;

    public CVService(PythonIntegrationService pythonService, CandidateService candidateService, CloudService cloudService) {
        this.pythonService = pythonService;
        this.candidateService = candidateService;
        this.cloudService = cloudService;
    }

    // Processes all PDF files from /static/ directory
    public void processAllCVs() {
        // cloud service test block
        List<Applicant> testList = cloudService.getCVs();
        boolean acceptanceFlag = false;
        boolean positionFlag = false;
        for (Applicant applicant : testList) {
            String tmp = applicant.getEmail();
            cloudService.sendResponse(tmp, tmp, positionFlag, acceptanceFlag);
            acceptanceFlag = !acceptanceFlag;
            positionFlag = !positionFlag;
            String[] testArray = {applicant.getPathToCV()};
            cloudService.sendFileToCloud(applicant.getEmail(), testArray);
        }
        // end of cloud service test block
        try (Stream<Path> files = Files.list(Paths.get("src/main/resources/static"))) {
            files.filter(f -> f.toString().endsWith(".pdf"))
                    .forEach(this::processCV);
        } catch (Exception e) {
            System.err.println("Error scanning directory: " + e.getMessage());
        }
    }

    public String processCV(Path filePath) {
        try {
            System.out.println("Processing file: " + filePath.getFileName());
            // run python script to process the file and get back candidate's data in JSON
            String json = pythonService.runPythonScript(filePath.toAbsolutePath().toString());

            // map JSON to the Candidate object - TODO: binding based on schema
            ObjectMapper mapper = new ObjectMapper();
            Candidate candidate = mapper.readValue(json, Candidate.class);

            candidateService.saveCandidate(candidate);
            System.out.println("Processed candidate: " + candidate.getName());
            return "Processed candidate: " + candidate.getName();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing CV " + filePath + ": " + e.getMessage();
        }
    }
}