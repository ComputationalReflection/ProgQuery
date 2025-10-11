package es.uniovi.reflection.progquery;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InsertResult {
    private List<CompilationResult> compilationResults;

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

    public long getTotalElapsedTime() {
        return compilationResults.stream().mapToLong(CompilationResult::getElapsedTime).sum();
    }

    public int getTotalCompiledFiles() {
        return compilationResults.stream().mapToInt(CompilationResult::getCompiledFiles).sum();
    }

    public List<String> getAllDiagnostics() {
        return compilationResults.stream().flatMap(result -> result.getDiagnostics().stream()).toList();
    }

    public String getJavacVersion() {
        return compilationResults.stream()
                .map(CompilationResult::getJavacVersion)
                .distinct()
                .collect(Collectors.joining(","));
    }
}
