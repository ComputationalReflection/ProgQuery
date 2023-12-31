package es.uniovi.reflection.progquery.typeInfo;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import com.sun.tools.javac.code.Symbol;

import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.cache.NotDuplicatingArcsDefCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.CDGRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.utils.dataTransferClasses.Pair;

public class PackageInfo {
	public static NodeWrapper currentProgram;

	public static void createCurrentProgram(String programID, String userID) {
		currentProgram = DatabaseFacade.CURRENT_DB_FACHADE.createNodeWithoutExplicitTree(NodeTypes.PROGRAM);
		currentProgram.setProperty("ID", programID);
		currentProgram.setProperty("USER_ID", userID);
		currentProgram.setProperty("timestamp", ZonedDateTime.now().toString());
	}
	public static void setCurrentProgram(NodeWrapper currentProgram){
		PackageInfo.currentProgram=currentProgram;
	}

	public static PackageInfo PACKAGE_INFO = new PackageInfo();
	private final DefinitionCache<String> packageCache = new NotDuplicatingArcsDefCache<>();

	private final Set<Pair<Symbol, Symbol>> dependenciesSet = new HashSet<>();
	public Symbol currentPackage;

	private void addDependency(Symbol dependent, Symbol dependency) {
		dependenciesSet.add(Pair.create(dependent, dependency));
	}

	public NodeWrapper getPackageNode(Symbol packageSymbol) {
		return packageCache.get(packageSymbol.toString());
	}
	private NodeWrapper addPackage(Symbol s, boolean isDeclared) {
		NodeWrapper packageNode = DatabaseFacade.CURRENT_DB_FACHADE.createNodeWithoutExplicitTree(NodeTypes.PACKAGE);
		if (isDeclared) {
			packageCache.putDefinition(s.toString(), packageNode);
			currentProgram.createRelationshipTo(packageNode, CDGRelationTypes.PROGRAM_DECLARES_PACKAGE);
		} else
			packageCache.put(s.toString(), packageNode);
		packageNode.setProperty("name", s.toString());
		packageNode.setProperty("isDeclared", isDeclared);
		return packageNode;
	}

	public NodeWrapper putDeclaredPackage(Symbol packageSymbol) {

		if (packageCache.containsDef(packageSymbol.toString()))
			return getPackageNode(packageSymbol);
		else {
			NodeWrapper packageNode = getPackageNode(packageSymbol);
			if (packageNode != null) {
				packageNode.setProperty("isDeclared", true);
				currentProgram.createRelationshipTo(packageNode, CDGRelationTypes.PROGRAM_DECLARES_PACKAGE);
				packageCache.putDefinition(packageSymbol.toString(), packageNode);
			} else
				packageNode = addPackage(packageSymbol, true);
			return packageNode;
		}
	}

	private boolean hasDependency(Symbol dependent, Symbol dependency) {
		return dependenciesSet.contains(Pair.create(dependent, dependency));
	}

	public void handleNewDependency(Symbol dependent, Symbol dependency) {
		if (!dependent.equals(dependency) && !hasDependency(dependent, dependency)) {
			addDependency(dependent, dependency);
			NodeWrapper dependentNode = getPackageNode(dependent), denpendencyNode = getPackageNode(dependency);
			if (dependentNode == null)
				addPackage(dependent, false);

			if (denpendencyNode == null)
				addPackage(dependency, false);
			dependenciesSet.add(Pair.create(dependent, dependency));
		}
	}

	public void createStoredPackageDeps() {
		for (Pair<Symbol, Symbol> packageDep : dependenciesSet) {
			NodeWrapper dependencyPack = packageCache.get(packageDep.getSecond().toString());
			if ((Boolean) dependencyPack.getProperty("isDeclared"))
				packageCache.get(packageDep.getFirst().toString()).createRelationshipTo(dependencyPack,
						CDGRelationTypes.DEPENDS_ON_PACKAGE);
			else
				packageCache.get(packageDep.getFirst().toString()).createRelationshipTo(dependencyPack,
						CDGRelationTypes.DEPENDS_ON_NON_DECLARED_PACKAGE);
		}
	}
}
