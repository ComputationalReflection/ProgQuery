package es.uniovi.reflection.progquery;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InsertResult {
    private List<CompilationResult> compilationResults;
    private long elapsedTime;
    private long totalNumberOfNodes;
    private long totalNumberOfRelationships;

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

    public void setTotalNumberOfNodes(long totalNumberOfNodes) { this.totalNumberOfNodes = totalNumberOfNodes; }
    public long getTotalNumberOfNodes() { return totalNumberOfNodes; }

    public void setTotalNumberOfRelationships(long totalNumberOfRelationships) { this.totalNumberOfRelationships = totalNumberOfRelationships; }
    public long getTotalNumberOfRelationships() { return totalNumberOfRelationships; }

    public String getJavacVersion() {
        return compilationResults.stream()
                .map(CompilationResult::getJavacVersion)
                .distinct()
                .collect(Collectors.joining(","));
    }

    @Override
    public String toString() {
        return new StringBuilder("Insertion Summary")
                .append("\n\tSuccess: ").append(isSuccess())
                .append("\n\tCompilation Tasks: ").append(getCompilationResults().size())
                .append("\n\tJavac Version: ").append(getJavacVersion())
                .append("\n\tTotal Number of Files Compiled: ").append(getTotalCompiledFiles())
                .append("\n\tTotal Number of Diagnostics: ").append(getTotalDiagnostics())
                .append("\n\tTotal Number of Nodes: ").append(getTotalNumberOfNodes())
                .append("\n\tTotal Number of Relationships: ").append(getTotalNumberOfRelationships())
                .append("\n\tHas Warnings: ").append(hasWarnings())
                .append("\n\tTotal Elapsed Time (millis): ").append(getElapsedTime())
                .append('\n')
                .toString();
    }
}
