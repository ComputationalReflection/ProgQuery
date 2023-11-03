package es.uniovi.reflection.progquery.tasklisteners;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ModuleTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskEvent.Kind;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import es.uniovi.reflection.progquery.CompilationScheduler;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.CDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.PartialRelation;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.typeInfo.PackageInfo;
import es.uniovi.reflection.progquery.utils.GraphUtils;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GetStructuresAfterAnalyze implements TaskListener {
    private final JavacTask task;
    private Map<JavaFileObject, Integer> classCounter = new HashMap<JavaFileObject, Integer>();
    private CompilationScheduler scheduler;
    private boolean started = false;
    private Pair<PartialRelation<RelationTypesInterface>, Object> argument;
    private final Set<JavaFileObject> sourcesToCompile;

    public GetStructuresAfterAnalyze(JavacTask task, CompilationScheduler scheduler, Set<JavaFileObject> sources) {
        this.task = task;
        this.scheduler = scheduler;
        this.sourcesToCompile = new HashSet<>();
        this.sourcesToCompile.addAll(sources);
    }

    private boolean mustBeCompiled(CompilationUnitTree cu) {
        return sourcesToCompile == null || sourcesToCompile.contains(cu.getSourceFile());
    }

    @Override
    public void finished(TaskEvent arg0) {
        if (arg0.getKind() == Kind.PARSE || arg0.getKind() == Kind.ANALYZE) {
            CompilationUnitTree cuTree = arg0.getCompilationUnit();
            if (mustBeCompiled(cuTree))
                if (arg0.getKind() == Kind.PARSE)
                    classCounter.put(cuTree.getSourceFile(), cuTree.getTypeDecls().size());
                else {
                    started = true;
                    int currentTypeCounter = classCounter.get(cuTree.getSourceFile());
                    if (cuTree.getTypeDecls().size() == 0)
                        firstScanIfNoTypeDecls(cuTree);
                    else {
                        boolean firstClass = classCounter.get(cuTree.getSourceFile()) == cuTree.getTypeDecls().size();
                        int nextTypeDecIndex = 0;
                        if (cuTree.getTypeDecls().size() > 1) {
                            String[] tydcSplit = arg0.getTypeElement().toString().split("\\.");
                            String simpleTypeName = tydcSplit.length > 0 ? tydcSplit[tydcSplit.length - 1] :
                                    arg0.getTypeElement().toString();
                            boolean found = false;
                            for (int i = 0; i < cuTree.getTypeDecls().size(); i++) {
                                if (cuTree.getTypeDecls().get(i) instanceof JCTree.JCSkip) {
                                    if (firstClass) {
                                        GraphUtils.connectWithParent(DatabaseFacade.CURRENT_DB_FACHADE
                                                        .createSkeletonNode(cuTree.getTypeDecls().get(i),
                                                                NodeTypes.EMPTY_STATEMENT),
                                                argument.getFirst().getStartingNode(), RelationTypes.ENCLOSES);
                                        currentTypeCounter--;
                                    }
                                    continue;
                                }
                                if (((ClassTree) cuTree.getTypeDecls().get(i)).getSimpleName()
                                        .contentEquals(simpleTypeName)) {
                                    nextTypeDecIndex = i;
                                    found = true;
                                    if (!firstClass)
                                        break;
                                }
                            }
                            if (!found)
                                throw new IllegalStateException(
                                        "NO TYPE DEC FOUND IN CU MATCHING JAVAC CURRENT " + simpleTypeName);
                        }
                        classCounter.put(cuTree.getSourceFile(), --currentTypeCounter);
                        if (firstClass)
                            firstScan(cuTree, cuTree.getTypeDecls().get(nextTypeDecIndex));
                        else
                            scan((ClassTree) cuTree.getTypeDecls().get(nextTypeDecIndex), false, cuTree);
                    }

                    if (currentTypeCounter <= 0) {
                        classCounter.remove(cuTree.getSourceFile());
                        if (sourcesToCompile != null)
                            sourcesToCompile.remove(cuTree.getSourceFile());
                    }
                }
        }
    }

    private void firstScanIfNoTypeDecls(CompilationUnitTree u) {
        JavacInfo.setJavacInfo(new JavacInfo(u, task));
        String fileName = u.getSourceFile().toUri().toString();
        NodeWrapper compilationUnitNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(u, NodeTypes.COMPILATION_UNIT);
        addPackageInfo(((JCCompilationUnit) u).packge, compilationUnitNode);
        compilationUnitNode.setProperty("fileName", fileName);
        argument = Pair.createPair(compilationUnitNode, null);
    }

    private NodeWrapper addPackageInfo(Symbol currentPackage, NodeWrapper compilationUnitNode) {
        PackageInfo.PACKAGE_INFO.currentPackage = currentPackage;
        NodeWrapper packageNode = PackageInfo.PACKAGE_INFO.putDeclaredPackage(currentPackage);
        packageNode.createRelationshipTo(compilationUnitNode, CDGRelationTypes.PACKAGE_HAS_COMPILATION_UNIT);
        return packageNode;
    }

    private void firstScan(CompilationUnitTree cu, Tree typeDeclaration) {
        if (!JavacInfo.isInitialized())
            JavacInfo.setJavacInfo(new JavacInfo(cu, task));
        String fileName = cu.getSourceFile().getName();
        NodeWrapper compilationUnitNode =
                DatabaseFacade.CURRENT_DB_FACHADE.createSkeletonNode(cu, NodeTypes.COMPILATION_UNIT);
        addPackageInfo(((JCCompilationUnit) cu).packge, compilationUnitNode);
        compilationUnitNode.setProperty("fileName", fileName);
        argument = Pair.createPair(compilationUnitNode, null);
        if (typeDeclaration instanceof ModuleTree)
            return;
        scan((ClassTree) typeDeclaration, true, cu);
    }

    private void scan(ClassTree typeDeclaration, boolean first, CompilationUnitTree cu) {
        new ASTTypesVisitor(typeDeclaration, first, scheduler.getPdgUtils(), scheduler.getAst(),
                argument.getFirst().getStartingNode()).scan(cu, argument);
    }

    @Override
    public void started(TaskEvent arg0) {
        if (arg0.getKind() == Kind.GENERATE && started)
            if (classCounter.size() == 0) {
                started = false;
            }
    }
}
