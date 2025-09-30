package es.uniovi.reflection.progquery.pg;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.cache.NotDuplicatingArcsDefCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.PGRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.typeInfo.keys.PackageKey;
import es.uniovi.reflection.progquery.typeInfo.keys.PackageKeyI;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;

import java.util.HashSet;
import java.util.Set;

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.IS_USER_CODE;
import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.NAME_PROP;

public class PackageManager {
    public static ThreadLocal<PackageManager> PACKAGE_MANAGER = new ThreadLocal<>();
    public final ProgramManager programManager;
    public Symbol.PackageSymbol currentPackage;
    private final DefinitionCache<PackageKeyI> packageCache;
    private final Set<Pair<PackageKeyI, PackageKeyI>> dependenciesSet;
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
                packageNode.setProperty(IS_USER_CODE, true);
                programManager.getCurrentProgram().createRelationshipTo(packageNode, PGRelationTypes.PROGRAM_DECLARES_PACKAGE);
                packageCache.updateToDefinition(packageKey, packageNode);
            } else
                packageNode = createPackage(packageSymbol, packageKey, true);
            return packageNode;
        }
    }
    public void handleNewDependency(Symbol.PackageSymbol dependent, Symbol.PackageSymbol dependency) {
        if (!dependent.equals(dependency)) {
            PackageKeyI dependentKey = PackageKey.newPackageKey(dependent), dependencyKey = PackageKey.newPackageKey(dependency);
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
        for (Pair<PackageKeyI, PackageKeyI> packageDep : dependenciesSet) {
            NodeWrapper dependencyPack = packageCache.get(packageDep.getSecond());
            if ((Boolean) dependencyPack.getProperty(IS_USER_CODE))
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

    private void addDependency(PackageKeyI dependent, PackageKeyI dependency) {
        dependenciesSet.add(Pair.create(dependent, dependency));
    }

    private NodeWrapper getPackageNode(PackageKeyI packageSymbol) {
        return packageCache.get(packageSymbol);
    }

    private NodeWrapper createPackage(Symbol.PackageSymbol packageSymbol, PackageKeyI packageKey, boolean isUserCode) {
        NodeWrapper packageNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.PACKAGE);
        if (isUserCode) {
            packageCache.putDefinition(packageKey, packageNode);
            programManager.getCurrentProgram().createRelationshipTo(packageNode, PGRelationTypes.PROGRAM_DECLARES_PACKAGE);
        } else
            packageCache.put(packageKey, packageNode);
        packageNode.setProperty(NAME_PROP, packageKey.getPackageName());
        packageNode.setProperty(IS_USER_CODE, isUserCode);
        moduleManager.setPackageModule(packageSymbol, packageNode);
        return packageNode;
    }

    private boolean hasDependency(PackageKeyI dependent, PackageKeyI dependency) {
        return dependenciesSet.contains(Pair.create(dependent, dependency));
    }
}
