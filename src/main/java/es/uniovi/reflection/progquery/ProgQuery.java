package es.uniovi.reflection.progquery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProgQuery {
    private CompilationScheduler compilationScheduler;
    private String programId;
    private String userId;
    public ProgQuery(String neo4j_host, String neo4j_port, String neo4j_user, String neo4j_password, String neo4j_database, String userId, String programId) {
        this.userId = userId;
        this.programId = programId;
        compilationScheduler = new CompilationScheduler(neo4j_host,neo4j_port,neo4j_user,neo4j_password,neo4j_database,programId,userId);
    }

    public ProgQuery(String neo4j_database_path, String userId, String programId) {
        this.userId = userId;
        this.programId = programId;
        compilationScheduler = new CompilationScheduler(neo4j_database_path,programId,userId);
    }

    public void analyze(List<String> javac_options_list) {
        for (String javac_options:javac_options_list)
            compilationScheduler.newCompilationTask(javac_options);
        compilationScheduler.endAnalysis();
    }
    public static List<File> listFiles(String path) {
        try (Stream<Path> walk = Files.walk(Paths.get(path))) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith(".java"))
                    .map(f -> f.toAbsolutePath().toFile())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<File>();
        }
    }
}
