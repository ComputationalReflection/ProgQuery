package es.uniovi.reflection.progquery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InsertResult {
    private List<CompilationResult> compilationResults;
    private long elapsedTime;

    public InsertResult() {
        this.compilationResults = new ArrayList<>();
    }

    public void addCompilationResult(CompilationResult result) {
        this.compilationResults.add(result);
    }
    public List<CompilationResult> getCompilationResults() {
        return compilationResults;
    }

    public boolean isSuccess() {
        return compilationResults.stream().allMatch(CompilationResult::isSuccess);
    }

    public boolean hasWarnings() {
        return compilationResults.stream().anyMatch(CompilationResult::hasWarnings);
    }

    public int getTotalCompiledFiles() {
        return compilationResults.stream().mapToInt(result -> result.getCompiledFiles().size()).sum();
    }

    public List<File> getAllCompiledFiles() {
        return compilationResults.stream().flatMap(result -> result.getCompiledFiles().stream()).toList();
    }

    public int getTotalDiagnostics() {
        return compilationResults.stream().mapToInt(result -> result.getDiagnostics().size()).sum();
    }

    public List<String> getAllDiagnostics() {
        return compilationResults.stream().flatMap(result -> result.getDiagnostics().stream()).toList();
    }

    public void setElapsedTime(long elapsedTime) { this.elapsedTime = elapsedTime;}
    public long getElapsedTime() { return elapsedTime; }

    public String getJavacVersion() {
        return compilationResults.stream()
                .map(CompilationResult::getJavacVersion)
                .distinct()
                .collect(Collectors.joining(","));
    }
}
