package es.uniovi.reflection.progquery;

import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

public class CompilationResult {
    private List<String> diagnostics;
    private String javacOptions;
    private String javacVersion;
    private int compiledFiles;

    public CompilationResult(String javacOptions) {
        this.javacOptions = javacOptions;
        this.javacVersion = System.getProperty("java.version");
        this.diagnostics = new ArrayList<>();
    }

    public void setJavacVersion(String javacVersion) { this.javacVersion = javacVersion; }
    public void addDiagnostics(List<String> diagnostics) { this.diagnostics.addAll(diagnostics); }
    public void setCompiledFiles(int compiledFiles) { this.compiledFiles = compiledFiles; }
    public int getCompiledFiles() { return compiledFiles; }
    public String getJavacVersion() { return javacVersion; }
    public String getJavacOptions() { return javacOptions; }
    public List<String> getDiagnostics() { return diagnostics; }
    public boolean isSuccess() { return diagnostics!=null && diagnostics.stream().filter(error -> error.startsWith(Diagnostic.Kind.ERROR.toString())).count() == 0; }
    public boolean hasWarnings() { return isSuccess() && diagnostics.size() > 0; }
}
