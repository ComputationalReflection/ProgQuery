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
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CompilationScheduler {
    private static final boolean MERGING_ALLOWED = true;
    private PDGProcessing pdgUtils;
    private final ThreadLocal<PDGProcessing> taskPdg = new ThreadLocal<>();
    private ASTAuxiliarStorage ast;
    private final ConcurrentLinkedQueue<StandardJavaFileManager> fileManagers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Path> temporaryOutputs = new ConcurrentLinkedQueue<>();

    private CompilationScheduler(ASTAuxiliarStorage ast) {
        this.ast = ast;
        pdgUtils = new PDGProcessing(ast);
    }

    public CompilationScheduler(ASTAuxiliarStorage ast, String neo4j_host, String neo4j_port, String neo4j_user,
                                String neo4j_password, String neo4j_database, String max_operations_transaction,
                                String max_concurrent_write_transactions, String programID, String userID) {
        this(ast);
        ProgQuery.LOGGER.info(
                String.format("New Compilation Scheduler: %s:%s:%s:%s:%s:%s", neo4j_host, neo4j_port, neo4j_user,
                        neo4j_database, userID, programID));
        DatabaseFacade.init(
                new Neo4jDriverLazyInsertion(neo4j_host, neo4j_port, neo4j_user, neo4j_password, neo4j_database,
                        max_operations_transaction, max_concurrent_write_transactions));
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
        return newCompilationTask(javac_options, null, -1);
    }

    CompilationResult newCompilationTask(String javac_options, ParallelCompilation parallel, int taskIndex) {
        if (parallel != null) taskPdg.set(pdgUtils.fork());
        CompilationResult result = new CompilationResult(javac_options);
        try {
            ProgQuery.LOGGER.info(String.format("New Compilation Task: %s", javac_options));
            List<String> options = parseOptions(javac_options);
            options.removeIf(option -> option.startsWith("-Xep")
                    || option.startsWith("-Xplugin:ErrorProne")
                    || option.startsWith("-AGeneratedDocumentationPath=")
                    || option.startsWith("-ADocumentation."));

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

            files.sort(java.util.Comparator.comparing(File::getAbsolutePath));

            List<String> task_options = new ArrayList<>();
            for (String option : options.stream().filter(o -> o.startsWith("-") && !o.startsWith("-sourcepath"))
                    .collect(Collectors.toList()))
                task_options.addAll(Arrays.asList(option.split(" ")));

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"));
            fileManagers.add(fileManager);
            Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(files);
            result.setCompiledFiles(files);
            if (files.isEmpty()) {
                ProgQuery.LOGGER.info("Skipping Compilation Task, no sources to compile.");
                return result;
            }

            // Keep dependency outputs produced by Maven immutable during analysis.
            // Retain the standard file manager: wrapping it changes javac's
            // platform-option diagnostics (including the source-8 bootstrap warning).
            if (parallel != null) {
                Path output = Files.createTempDirectory("progquery-compile-");
                temporaryOutputs.add(output);
                int outputOption = task_options.indexOf("-d");
                if (outputOption >= 0) {
                    Path originalOutput = Path.of(task_options.get(outputOption + 1));
                    // Annotation processors (e.g. NetBeans @Messages) read
                    // resources from CLASS_OUTPUT. Preserve that input, while
                    // keeping generated classes/resources private to this task.
                    if (Files.isDirectory(originalOutput)) {
                        try (Stream<Path> walk = Files.walk(originalOutput)) {
                            for (Path resource : walk.filter(Files::isRegularFile)
                                    .filter(path -> !path.toString().endsWith(".class")).toList()) {
                                Path destination = output.resolve(originalOutput.relativize(resource));
                                Files.createDirectories(destination.getParent());
                                Files.copy(resource, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    task_options.set(outputOption + 1, output.toString());
                } else {
                    task_options.add("-d");
                    task_options.add(output.toString());
                }
            }
            JavacTaskImpl compilerTask = (JavacTaskImpl) compiler.getTask(null, fileManager, diagnostics, task_options, null, sources);
            JavacInfo.currentJavacInfo.set(new JavacInfo(compilerTask));
            GetStructuresAfterAnalyze listener = new GetStructuresAfterAnalyze(compilerTask, this,
                    StreamSupport.stream(sources.spliterator(), false).collect(Collectors.toSet()));
            compilerTask.addTaskListener(parallel == null ? listener : new TaskListener() {
                @Override
                public void finished(TaskEvent event) {
                    if (event.getKind() == TaskEvent.Kind.ANALYZE
                            && listener.handlesCompilationUnit(event.getCompilationUnit()))
                        parallel.visitGraph(taskIndex, () -> {
                            listener.finished(event);
                            // Later desugaring/generation does not modify the graph.
                            if (listener.isGraphComplete())
                                parallel.complete(taskIndex);
                        });
                    else
                        listener.finished(event);
                }

                @Override
                public void started(TaskEvent event) {
                    listener.started(event);
                }
            });
            runPQCompilationTask(compilerTask);
            result.addDiagnostics(showErrors(diagnostics));
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to prepare compilation", e);
        } finally {
            taskPdg.remove();
            ast.releaseTaskState();
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
        PDGProcessing local = taskPdg.get();
        return local == null ? pdgUtils : local;
    }

    public ASTAuxiliarStorage getAst() {
        return ast;
    }

    public void finalizeInsertion() {
        ProgQuery.LOGGER.info("Finishing insertion ...");
        ProgQuery.LOGGER.info("Creating StoredPackageDeps ...");
        createStoredPackageDeps();
        ProgQuery.LOGGER.info("Dynamic Method Call Analysis ...");
        dynamicMethodCallAnalysis();
        ProgQuery.LOGGER.info("Interprocedural PDG Analysis ...");
        interproceduralPDGAnalysis();
        ProgQuery.LOGGER.info("Finalizing Analysis ...");
        initializationAnalysis();
        ProgQuery.LOGGER.info("Writing graph in database...");
        shutdownDatabase();
        ProgQuery.LOGGER.info("... insertion completed!!");
    }

    public void shutdownDatabase() {
        DatabaseFacade.CURRENT_INSERTION_STRATEGY.endAnalysis();
    }

    public void closeFileManagers() {
        UncheckedIOException closeFailure = null;
        for (StandardJavaFileManager fileManager : fileManagers) {
            try {
                fileManager.close();
            } catch (IOException exception) {
                if (closeFailure == null)
                    closeFailure = new UncheckedIOException("Unable to close the Java file manager", exception);
            }
        }
        fileManagers.clear();
        for (Path output : temporaryOutputs) {
            try {
                List<Path> paths;
                try (Stream<Path> walk = Files.walk(output)) {
                    paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
                }
                for (Path path : paths)
                    Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (closeFailure == null)
                    closeFailure = new UncheckedIOException("Unable to delete compilation output " + output, exception);
            }
        }
        temporaryOutputs.clear();
        if (closeFailure != null)
            throw closeFailure;
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
