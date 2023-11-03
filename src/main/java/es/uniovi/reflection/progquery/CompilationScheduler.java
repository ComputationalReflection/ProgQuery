package es.uniovi.reflection.progquery;

import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.EmbeddedInsertion;
import es.uniovi.reflection.progquery.database.Neo4jDriverLazyInsertion;
import es.uniovi.reflection.progquery.database.manager.NEO4JManager;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.tasklisteners.GetStructuresAfterAnalyze;
import es.uniovi.reflection.progquery.typeInfo.PackageInfo;
import es.uniovi.reflection.progquery.visitors.PDGProcessing;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CompilationScheduler {
    private PDGProcessing pdgUtils = new PDGProcessing();
    private ASTAuxiliarStorage ast = new ASTAuxiliarStorage();
    private JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    private static final boolean MERGING_ALLOWED = true;

    public CompilationScheduler(String neo4j_host, String neo4j_port, String neo4j_user, String neo4j_password, String neo4j_database, String programID, String userID) { //TODO: Passing Max Operation Transaction
        ProgQuery.LOGGER.info(String.format("New Compilation Scheduler: %s:%s:%s:%s:%s:%s",neo4j_host, neo4j_port, neo4j_user, neo4j_database,userID,programID));
        DatabaseFacade.init(new Neo4jDriverLazyInsertion(neo4j_host, neo4j_port, neo4j_user, neo4j_password, neo4j_database, OptionsConfiguration.DEFAULT_MAX_OPERATIONS_TRANSACTION));
        setCurrentProgram(programID,userID);
    }

    public CompilationScheduler(String neo4j_database_path, String programID, String userID) {
        ProgQuery.LOGGER.info(String.format("New Compilation Scheduler: %s:%s:%s",neo4j_database_path,userID,programID));
        DatabaseFacade.init(new EmbeddedInsertion(neo4j_database_path));
        setCurrentProgram(programID,userID);
    }

    private void setCurrentProgram(String programID, String userID) {
        DatabaseFacade.CURRENT_INSERTION_STRATEGY.startAnalysis();
        if (MERGING_ALLOWED) {
            NodeWrapper retrievedProgram = null;
            try (NEO4JManager manager = DatabaseFacade.CURRENT_INSERTION_STRATEGY.getManager()) {
                retrievedProgram = manager.getProgramFromDB(programID, userID);
            }
            if (retrievedProgram != null) {
                PackageInfo.setCurrentProgram(retrievedProgram);
                return;
            }
        }
        PackageInfo.createCurrentProgram(programID, userID);
    }

    public void newCompilationTask(String javac_options) {
        ProgQuery.LOGGER.info(String.format("New Compilation Task: %s", javac_options));
        List<String> options = parseOptions(javac_options);
        String sourcepath = options.stream().filter(o-> o.startsWith("-sourcepath")).findFirst().orElse("");
        List<File> files = new ArrayList<>();
        if(!sourcepath.isEmpty()) {
            for (String sourceFolder : sourcepath.substring(12).split(File.pathSeparator))
                files.addAll(listFiles(sourceFolder));
        }

        for (String sourceFile:options.stream().filter(o->!o.startsWith("-")).filter( o-> o.endsWith(".java")).collect(Collectors.toList()))
            files.add(Paths.get(sourceFile).toAbsolutePath().toFile());

        List<String> task_options = new ArrayList<>();
        for(String option:options.stream().filter(o-> o.startsWith("-") && !o.startsWith("-sourcepath")).collect(Collectors.toList()))
            task_options.addAll(Arrays.asList(option.split(" ")));

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"));
        Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(files);

        JavacTaskImpl compilerTask =
                (JavacTaskImpl) compiler.getTask(null, null, diagnostics, task_options , null, sources);

        addListener(compilerTask, StreamSupport.stream(sources.spliterator(), false).collect(Collectors.toSet()));
        runPQCompilationTask(compilerTask);
        showErrors(diagnostics);
    }

    public static List<String> parseOptions(String options){
        List<String> result = new ArrayList<>();
        String partial = "";
        for (String option:options.split(" ")) {
            if (option.startsWith("-")) {
                if (!partial.isEmpty())
                    result.add(partial);
                partial = option;
            }
            else if(partial.isEmpty()) {
                result.add(option);
            }
            else  {
                result.add(partial + " " + option);
                partial = "";
            }
        }
        return result;
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

    public void addListener(JavacTask compilerTask, Set<JavaFileObject> sources) {
        GetStructuresAfterAnalyze pqListener = new GetStructuresAfterAnalyze(compilerTask, this, sources);
        compilerTask.addTaskListener(pqListener);
    }

    private void runPQCompilationTask(JavacTask compilerTask) {
        compilerTask.call();
    }

    public PDGProcessing getPdgUtils() {
        return pdgUtils;
    }

    public ASTAuxiliarStorage getAst() {
        return ast;
    }

    public void endAnalysis() {
        ProgQuery.LOGGER.info("Finishing analysis...");
        pdgUtils.createNotDeclaredAttrRels(ast);
        createStoredPackageDeps();
        dynamicMethodCallAnalysis();
        interproceduralPDGAnalysis();
        initializationAnalysis();
        shutdownDatabase();
    }

    private void showErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        if (diagnostics.getDiagnostics().size() > 0) {
            for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
                if(diagnostic.getKind().equals(Diagnostic.Kind.ERROR))
                    System.err.format("Error on [%d,%d] in %s %s\n", diagnostic.getLineNumber(), diagnostic.getColumnNumber(),
                            diagnostic.getSource(), diagnostic.getMessage(null));
            }
        }
    }

    private void createStoredPackageDeps() {
        PackageInfo.PACKAGE_INFO.createStoredPackageDeps();
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

    public void shutdownDatabase() {
        DatabaseFacade.CURRENT_INSERTION_STRATEGY.endAnalysis();
    }
}
