package es.uniovi.reflection.progquery;

public class FailedInsertResult extends InsertResult {
    public FailedInsertResult(String diagnostic) {
        CompilationResult result = new CompilationResult("");
        result.addDiagnostics(java.util.Collections.singletonList(diagnostic));
        addCompilationResult(result);
    }
}
