package pl.hogwart.cvprocessor.services;

import org.springframework.stereotype.Service;
import pl.hogwart.cvprocessor.model.PythonResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Class responsible for running python script
 */
@Service
public class PythonIntegrationService {

    public PythonResult runPythonScript() {
        PythonResult result = new PythonResult();

        try {
            ProcessBuilder pb = new ProcessBuilder("python", "./python-extractor/cv_parser.py");
            Process process = pb.start();

            BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            String line;
            while ((line = stdOut.readLine()) != null)
                out.append(line).append("\n");

            while ((line = stdErr.readLine()) != null)
                err.append(line).append("\n");

            int exitCode = process.waitFor();

            result.setSuccess(exitCode == 0);

            if (exitCode == 0) {
                result.setMessage("Python script finished successfully.");
                result.addLog(out.toString());
            } else {
                result.setMessage("Python script failed.");
                result.addLog(err.toString());
                System.err.println(err);
            }
            return result;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Error running script: " + e.getMessage());
            e.printStackTrace();
            return result;
        }
    }
}