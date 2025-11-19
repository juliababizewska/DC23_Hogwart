package pl.hogwart.cvprocessor.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class PythonResult {
    private boolean success;
    private String message;
    private List<String> logs= new ArrayList<>();

    public void addLog(String log) {
        logs.add(log);
    }

    public void addLogs(Collection<String> logList) {
        logs.addAll(logList);
    }
}
