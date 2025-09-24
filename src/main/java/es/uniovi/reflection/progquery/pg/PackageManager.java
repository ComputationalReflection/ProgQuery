package es.uniovi.reflection.progquery.pg;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.cache.NotDuplicatingArcsDefCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.PGRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.typeInfo.keys.ModuleKey;
import es.uniovi.reflection.progquery.typeInfo.keys.PackageKey;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

public class PackageManager {
    public static ThreadLocal<PackageManager> PACKAGE_MANAGER = new ThreadLocal<>();
    public final ProgramManager programManager;
    public Symbol.PackageSymbol currentPackage;
    private final DefinitionCache<PackageKey> packageCache;
    private final Set<Pair<PackageKey, PackageKey>> dependenciesSet;
    private final ModuleManager moduleManager;

    public PackageManager(ASTAuxiliarStorage ast) {
        this.programManager = new ProgramManager();
        this.packageCache = new NotDuplicatingArcsDefCache<>();
        this.dependenciesSet = new HashSet<>();
        this.moduleManager = new ModuleManager(programManager, ast);
    }

    public NodeWrapper putDeclaredPackage(Symbol.PackageSymbol packageSymbol) {
        PackageKey packageKey = new PackageKey(packageSymbol);
        if (packageCache.containsDef(packageKey))
            return getPackageNode(packageKey);
        else {
            NodeWrapper packageNode = getPackageNode(packageKey);
            if (packageNode != null) {
                packageNode.setProperty(ASTTypesVisitor.IS_USER_CODE_PROP, true);
                programManager.getCurrentProgram().createRelationshipTo(packageNode, PGRelationTypes.PROGRAM_DECLARES_PACKAGE);
                packageCache.updateToDefinition(packageKey, packageNode);
            } else
                packageNode = createPackage(packageSymbol, packageKey, true);
            return packageNode;
        }
    }
    public void handleNewDependency(Symbol.PackageSymbol dependent, Symbol.PackageSymbol dependency) {
        if (!dependent.equals(dependency)) {
            PackageKey dependentKey = new PackageKey(dependent), dependencyKey = new PackageKey(dependency);
            if(!hasDependency(dependentKey, dependencyKey)) {
                addDependency(dependentKey, dependencyKey);
                NodeWrapper dependentNode = getPackageNode(dependentKey), dependencyNode = getPackageNode(dependencyKey);
                if (dependentNode == null)
                    createPackage(dependent, dependentKey, false);

                if (dependencyNode == null)
                    createPackage(dependency, dependencyKey, false);
                dependenciesSet.add(Pair.create(dependentKey, dependencyKey));
            }
        }
    }

    public void createStoredPackageDeps() {
        for (Pair<PackageKey, PackageKey> packageDep : dependenciesSet) {
            NodeWrapper dependencyPack = packageCache.get(packageDep.getSecond());
            if ((Boolean) dependencyPack.getProperty(ASTTypesVisitor.IS_USER_CODE_PROP))
                packageCache.get(packageDep.getFirst())
                        .createRelationshipTo(dependencyPack, PGRelationTypes.DEPENDS_ON_PACKAGE);
            else
                packageCache.get(packageDep.getFirst())
                        .createRelationshipTo(dependencyPack, PGRelationTypes.DEPENDS_ON_EXTERNAL_PACKAGE);
        }
    }

    public NodeWrapper createUserModule(Symbol.ModuleSymbol modle) {
        return moduleManager.createUserModule(modle);
    }

    NodeWrapper getOrCreateExternalPackage(Symbol.PackageSymbol packageSymbol) {
        PackageKey packageKey = new PackageKey(packageSymbol);
        if (packageCache.containsKey(packageKey))
            return packageCache.get(packageKey);

      return createPackage(packageSymbol, packageKey, false);

    }

    private void addDependency(PackageKey dependent, PackageKey dependency) {
        dependenciesSet.add(Pair.create(dependent, dependency));
    }

    private NodeWrapper getPackageNode(PackageKey packageSymbol) {
        return packageCache.get(packageSymbol);
    }

    private NodeWrapper createPackage(Symbol.PackageSymbol packageSymbol, PackageKey packageKey, boolean isUserCode) {
        NodeWrapper packageNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.PACKAGE);
        if (isUserCode) {
            packageCache.putDefinition(packageKey, packageNode);
            programManager.getCurrentProgram().createRelationshipTo(packageNode, PGRelationTypes.PROGRAM_DECLARES_PACKAGE);
        } else
            packageCache.put(packageKey, packageNode);
        packageNode.setProperty("name", packageSymbol.toString());
        packageNode.setProperty(ASTTypesVisitor.IS_USER_CODE_PROP, isUserCode);
        moduleManager.setPackageModule(packageSymbol, packageNode);
        return packageNode;
    }

    private boolean hasDependency(PackageKey dependent, PackageKey dependency) {
        return dependenciesSet.contains(Pair.create(dependent, dependency));
    }
}
