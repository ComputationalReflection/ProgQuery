package es.uniovi.reflection.progquery;

import com.sun.tools.javac.api.JavacTaskImpl;
import es.uniovi.reflection.progquery.database.DatabaseFachade;
import es.uniovi.reflection.progquery.database.EmbeddedInsertion;
import es.uniovi.reflection.progquery.database.Neo4jDriverLazyInsertion;
import es.uniovi.reflection.progquery.tasklisteners.GetStructuresAfterAnalyze;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProgQuery {
    public ProgQuery(String neo4j_host, String neo4j_port, String neo4j_user, String neo4j_password, String neo4j_database) { //TODO: Passing Max Operation Transaction
        DatabaseFachade.init(new Neo4jDriverLazyInsertion(neo4j_host, neo4j_port, neo4j_user, neo4j_password, neo4j_database, OptionsConfiguration.DEFAULT_MAX_OPERATIONS_TRANSACTION));
    }

    public ProgQuery(String neo4j_database_path) {
        DatabaseFachade.init(new EmbeddedInsertion(neo4j_database_path));
    }

    public void analyze(String javac_options, String userId, String programId) {
        String[] commandOptions = javac_options.split(" ");
        String[] sourceFolders = null;
        String options = "";
        for (int i = 0; i < commandOptions.length; i++) {
            if (commandOptions[i].equals("-sourcepath"))
                sourceFolders = commandOptions[++i].split(";");
            else
                options += commandOptions[i] + " ";
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        List<File> files = new ArrayList<>();
        for (String sourceFolder : sourceFolders)
            files.addAll(listFiles(sourceFolder));

        Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(files);

        JavacTaskImpl compilerTask = (JavacTaskImpl) compiler.getTask(null, null, diagnostics, Arrays.asList(options.split(" ")), null, sources);
        compilerTask.addTaskListener(new GetStructuresAfterAnalyze(compilerTask, programId, userId));
        compilerTask.call();

        if (diagnostics.getDiagnostics().size() > 0) {
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                if(diagnostic.getKind().equals(Diagnostic.Kind.ERROR))
                    System.err.format("Error on [%d,%d] in %s %s\n", diagnostic.getLineNumber(), diagnostic.getColumnNumber(),
                        diagnostic.getSource(), diagnostic.getMessage(null));
            }
        }
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
