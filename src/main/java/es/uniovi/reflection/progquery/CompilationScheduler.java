package es.uniovi.reflection.progquery;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.EmbeddedInsertion;
import es.uniovi.reflection.progquery.database.Neo4jDriverLazyInsertion;
import es.uniovi.reflection.progquery.database.manager.NEO4JManager;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.pg.PackageManager;
import es.uniovi.reflection.progquery.tasklisteners.GetStructuresAfterAnalyze;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.visitors.PDGProcessing;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CompilationScheduler {
    private static final boolean MERGING_ALLOWED = true;
    private PDGProcessing pdgUtils = new PDGProcessing();
    private ASTAuxiliarStorage ast;
    private JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private CompilationScheduler(ASTAuxiliarStorage ast) {
        this.ast = ast;
    }

    public CompilationScheduler(ASTAuxiliarStorage ast, String neo4j_host, String neo4j_port, String neo4j_user,
                                String neo4j_password, String neo4j_database, String max_operations_transaction,
                                String programID, String userID) {
        this(ast);
        ProgQuery.LOGGER.info(
                String.format("New Compilation Scheduler: %s:%s:%s:%s:%s:%s", neo4j_host, neo4j_port, neo4j_user,
                        neo4j_database, userID, programID));
        DatabaseFacade.init(
                new Neo4jDriverLazyInsertion(neo4j_host, neo4j_port, neo4j_user, neo4j_password, neo4j_database,
                        max_operations_transaction));
        setCurrentProgram(programID, userID);
    }

    public CompilationScheduler(ASTAuxiliarStorage ast, String neo4j_database_path, String neo4j_database,
                                String programID, String userID) {
        this(ast);
        ProgQuery.LOGGER.info(
                String.format("New Compilation Scheduler: %s:%s:%s:%s", neo4j_database_path, neo4j_database, userID,
                        programID));
        DatabaseFacade.init(new EmbeddedInsertion(neo4j_database_path, neo4j_database));
        setCurrentProgram(programID, userID);
    }

    private String getOptionValue(List<String> options, String option){
        String value = options.stream().filter(o -> o.startsWith(option)).findFirst().orElse("");
        if (value.isEmpty()) {
            value = options.stream().filter(o -> o.startsWith("-" + option)).findFirst().orElse("");
            if (value.isEmpty())
                return value;
        }
        return value.substring(option.length()+1);
    }

    public CompilationResult newCompilationTask(String javac_options) {
        CompilationResult result = new CompilationResult(javac_options);
        try {
            Instant buildStart = Instant.now();
            ProgQuery.LOGGER.info(String.format("New Compilation Task: %s", javac_options));
            List<String> options = parseOptions(javac_options);

            String javac_version = getOptionValue(options,"-release");
            if (javac_version.isEmpty())
                javac_version = getOptionValue(options,"-target");
            result.setJavacVersion(javac_version);

            String sourcepath = getOptionValue(options,"-sourcepath");
            List<File> files = new ArrayList<>();
            if (!sourcepath.isEmpty()) {
                for (String sourceFolder : sourcepath.split(File.pathSeparator))
                    files.addAll(listFiles(new File(sourceFolder).getCanonicalPath()));
            }

            for (String sourceFile : options.stream().filter(o -> !o.startsWith("-")).filter(o -> o.endsWith(".java"))
                    .collect(Collectors.toList()))
                files.add(Paths.get(new File(sourceFile).getCanonicalPath()).toAbsolutePath().toFile());

            List<String> task_options = new ArrayList<>();
            for (String option : options.stream().filter(o -> o.startsWith("-") && !o.startsWith("-sourcepath"))
                    .collect(Collectors.toList()))
                task_options.addAll(Arrays.asList(option.split(" ")));

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"));
            Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(files);
            result.setCompiledFiles(files.size());
            if (files.isEmpty()) {
                ProgQuery.LOGGER.info("Skipping Compilation Task, no sources to compile.");
                return result;
            }

            JavacTaskImpl compilerTask =
                    (JavacTaskImpl) compiler.getTask(null, null, diagnostics, task_options, null, sources);
            JavacInfo.currentJavacInfo.set(new JavacInfo(compilerTask));
            addListener(compilerTask, StreamSupport.stream(sources.spliterator(), false).collect(Collectors.toSet()));
            runPQCompilationTask(compilerTask);
            result.addDiagnostics(showErrors(diagnostics));
            result.setElapsedTime(Duration.between(buildStart, Instant.now()).toMillis());
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            return result;
        }
    }

    public static List<String> parseOptions(String options) {
        List<String> result = new ArrayList<>();
        String partial = "";
        for (String option : options.split(" ")) {
            if (option.startsWith("-")) {
                if (!partial.isEmpty())
                    result.add(partial);
                partial = option;
            } else if (partial.isEmpty()) {
                result.add(option);
            } else {
                result.add(partial + " " + option);
                partial = "";
            }
        }
        if (!partial.isEmpty())
            result.add(partial);
        return result;
    }

    public static List<File> listFiles(String path) {
        if (Files.exists(Paths.get(path))) {
            try (Stream<Path> walk = Files.walk(Paths.get(path))) {
                return walk.filter(Files::isRegularFile).filter(f -> f.getFileName().toString().endsWith(".java"))
                        .map(f -> f.toAbsolutePath().toFile()).collect(Collectors.toList());
            } catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<File>();
            }
        }
        return new ArrayList<File>();
    }

    public void addListener(JavacTask compilerTask, Set<JavaFileObject> sources) {
        GetStructuresAfterAnalyze pqListener = new GetStructuresAfterAnalyze(compilerTask, this, sources);
        compilerTask.addTaskListener(pqListener);
    }

    public PDGProcessing getPdgUtils() {
        return pdgUtils;
    }

    public ASTAuxiliarStorage getAst() {
        return ast;
    }

    public void finalizeInsertion() {
        ProgQuery.LOGGER.info("Finishing insertion ...");
        pdgUtils.createNotDeclaredAttrRels(ast);
        createStoredPackageDeps();
        dynamicMethodCallAnalysis();
        interproceduralPDGAnalysis();
        initializationAnalysis();
        shutdownDatabase();
    }

    public void shutdownDatabase() {
        DatabaseFacade.CURRENT_INSERTION_STRATEGY.endAnalysis();
    }

    private void setCurrentProgram(String programID, String userID) {
        DatabaseFacade.CURRENT_INSERTION_STRATEGY.startAnalysis();
        if (MERGING_ALLOWED) {
            NodeWrapper retrievedProgram = null;
            try (NEO4JManager manager = DatabaseFacade.CURRENT_INSERTION_STRATEGY.getManager()) {
                retrievedProgram = manager.getProgramFromDB(programID, userID);
            }
            if (retrievedProgram != null) {
                PackageManager.PACKAGE_MANAGER.get().programManager.setCurrentProgram(retrievedProgram);
                return;
            }
        }
        PackageManager.PACKAGE_MANAGER.get().programManager.createCurrentProgram(programID, userID);
    }

    private void runPQCompilationTask(JavacTask compilerTask) {
        compilerTask.call();
    }

    private List<String> showErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<String> errors = new ArrayList<>();
        if (diagnostics.getDiagnostics().size() > 0) {
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind().equals(Diagnostic.Kind.WARNING) ||
                        diagnostic.getKind().equals(Diagnostic.Kind.ERROR)) {
                    String error = "";
                    try {
                        error = String.format("%s on [%d,%d] in %s %s\n", diagnostic.getKind().toString(),
                                diagnostic.getLineNumber(), diagnostic.getColumnNumber(), diagnostic.getSource(),
                                diagnostic.getMessage(null));
                    } catch (Exception e) {
                        error = String.format("%s in %s %s\n", diagnostic.getKind().toString(), diagnostic.getSource(),
                                diagnostic.getMessage(null));
                    }
                    errors.add(error);
                    System.err.println(error);
                }
            }
        }
        return errors;
    }

    private void createStoredPackageDeps() {
        PackageManager.PACKAGE_MANAGER.get().createStoredPackageDeps();
    }

    private void createAllParamsToMethodsPDGRels() {
        ast.createAllParamsToMethodsPDGRels();
    }

    private void initializationAnalysis() {
        ast.doInitializationAnalysis();
    }

    private void interproceduralPDGAnalysis() {
        ast.doInterproceduralPDGAnalysis();
        createAllParamsToMethodsPDGRels();
    }

    private void dynamicMethodCallAnalysis() {
        ast.doDynamicMethodCallAnalysis();
    }
}
