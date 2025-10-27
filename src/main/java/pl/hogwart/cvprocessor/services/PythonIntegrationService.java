package pl.hogwart.cvprocessor.services;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Class responsible for running python script
 */
@Service
public class PythonIntegrationService {

    public String runPythonScript(String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "./python-extractor/extract_cv.py", filePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // the example script extract_cv.py just sends JSON data to stdout for now

            // getting result from stout
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                output.append(line);

            process.waitFor();
            return output.toString(); // return obtained JSON data
        } catch (Exception e) {
            return "Error running Python script: " + e.getMessage();
        }
    }
}
