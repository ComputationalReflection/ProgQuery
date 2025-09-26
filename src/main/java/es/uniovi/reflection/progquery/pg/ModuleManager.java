package es.uniovi.reflection.progquery.pg;

import com.sun.tools.javac.code.Symbol;
import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.cache.NotDuplicatingArcsDefCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.PGRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.typeInfo.keys.ModuleKey;
import es.uniovi.reflection.progquery.visitors.ASTTypesVisitor;

import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import java.util.List;

import static es.uniovi.reflection.progquery.database.nodes.NodeTypes.OPENS_DIRECTIVE;
import static es.uniovi.reflection.progquery.pg.PackageManager.PACKAGE_MANAGER;

public class ModuleManager {
    //The simple cache is used because we retrieve the node and modify the isUserCode property instead of creating a
    // new node
    private final DefinitionCache<ModuleKey> moduleCache = new NotDuplicatingArcsDefCache<>();
    private final ProgramManager programManager;
    private final ASTAuxiliarStorage ast;

    public ModuleManager(ProgramManager programManager, ASTAuxiliarStorage ast) {
        this.programManager = programManager;
        this.ast = ast;
    }

    void setPackageModule(Symbol.PackageSymbol packageSymbol, NodeWrapper packageNode) {
        Symbol.ModuleSymbol moduleSymbol = packageSymbol.modle;
        if (moduleSymbol==null || moduleSymbol.isUnnamed())
            return;
        NodeWrapper moduleNode = getOrCreateExternalModule(moduleSymbol);
        moduleNode.createRelationshipTo(packageNode, PGRelationTypes.MODULE_CONTAINS_PACKAGE);
    }

    NodeWrapper createUserModule(Symbol.ModuleSymbol moduleSymbol) {
        ModuleKey moduleKey = new ModuleKey(moduleSymbol);
        NodeWrapper moduleNode = moduleCache.get(moduleKey);
        if (moduleNode == null) {
            moduleNode = createModule(moduleSymbol, true);
            moduleCache.putDefinition(moduleKey, moduleNode);
        } else {
            moduleCache.updateToDefinition(moduleKey, moduleNode);
            moduleNode.setProperty(ASTTypesVisitor.IS_USER_CODE_PROP, true);
        }
        if (!moduleNode.hasProperty("version"))
            moduleNode.setProperty("version", moduleSymbol.version.toString());

        programManager.getCurrentProgram().createRelationshipTo(moduleNode, PGRelationTypes.PROGRAM_DECLARES_MODULE);
        addDirectiveRelationships(moduleSymbol, moduleNode);
        return moduleNode;
    }

    private NodeWrapper getOrCreateExternalModule(Symbol.ModuleSymbol moduleSymbol) {
        ModuleKey moduleKey = new ModuleKey(moduleSymbol);
        if (moduleCache.containsKey(moduleKey))
            return moduleCache.get(moduleKey);

        NodeWrapper module = createModule(moduleSymbol, false);
        moduleCache.put(moduleKey, module);
        return module;
    }

    private NodeWrapper createModule(Symbol.ModuleSymbol moduleSymbol, boolean isUserCode) {
        NodeWrapper moduleNode = DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.MODULE);
        moduleNode.setProperty("name", moduleSymbol.name.toString());
        if (moduleSymbol.version != null)
            moduleNode.setProperty("version", moduleSymbol.version.toString());
        moduleNode.setProperty("isOpened", moduleSymbol.isOpen());
        moduleNode.setProperty(ASTTypesVisitor.IS_USER_CODE_PROP, isUserCode);
        return moduleNode;
    }

    private void addDirectiveRelationships(Symbol.ModuleSymbol moduleSymbol, NodeWrapper moduleNode) {
        for (ModuleElement.RequiresDirective requires : moduleSymbol.requires) {
            RelationshipWrapper requiresRel = moduleNode.createRelationshipTo(
                    getOrCreateExternalModule((Symbol.ModuleSymbol) requires.getDependency()),
                    PGRelationTypes.MODULE_REQUIRES);
            requiresRel.setProperty("isTransitive", requires.isTransitive());
            requiresRel.setProperty("onlyCompileTime", requires.isStatic());
        }
        for (ModuleElement.ExportsDirective exports : moduleSymbol.exports)
            addOpensOrExports(moduleNode, exports.getPackage(), exports.getTargetModules(), DirectiveType.EXPORTS);

        for (ModuleElement.ProvidesDirective provides : moduleSymbol.provides) {
            NodeWrapper providesNode =
                    DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(NodeTypes.PROVIDES_DIRECTIVE);
            moduleNode.createRelationshipTo(providesNode, PGRelationTypes.MODULE_PROVIDES);
            NodeWrapper providedType = DefinitionCache.getOrCreateType(provides.getService().asType(), ast);
            providesNode.createRelationshipTo(providedType, PGRelationTypes.PROVIDES_SERVICE);
            for (TypeElement providedImpl: provides.getImplementations())
                providesNode.createRelationshipTo(DefinitionCache.getOrCreateType(providedImpl.asType(), ast),
                        PGRelationTypes.PROVIDES_IMPL);
        }
        for (ModuleElement.UsesDirective uses : moduleSymbol.uses) {
            moduleNode.createRelationshipTo(DefinitionCache.getOrCreateType(uses.getService().asType(), ast),
                    PGRelationTypes.MODULE_USES_SERVICE);
        }
        for(ModuleElement.OpensDirective opens: moduleSymbol.opens)
            addOpensOrExports(moduleNode, opens.getPackage(), opens.getTargetModules(), DirectiveType.OPENS);
    }
    private static enum DirectiveType {EXPORTS {
        @Override
        NodeTypes getNodeType() {
            return NodeTypes.EXPORTS_DIRECTIVE;
        }

        @Override
        PGRelationTypes getModuleRelationType() {
            return PGRelationTypes.MODULE_EXPORTS;
        }

        @Override
        PGRelationTypes getPackageRelationType() {
            return PGRelationTypes.EXPORTS_PACKAGE;
        }

        @Override
        PGRelationTypes getOnlyToRelationType() {
            return PGRelationTypes.EXPORTS_ONLY_TO;
        }
    }, OPENS {
        @Override
        NodeTypes getNodeType() {
            return OPENS_DIRECTIVE;
        }

        @Override
        PGRelationTypes getModuleRelationType() {
            return PGRelationTypes.MODULE_OPENS;
        }

        @Override
        PGRelationTypes getPackageRelationType() {
            return PGRelationTypes.OPENS_PACKAGE;
        }

        @Override
        PGRelationTypes getOnlyToRelationType() {
            return PGRelationTypes.OPENS_ONLY_TO;
        }
    };
        abstract NodeTypes getNodeType();
        abstract PGRelationTypes getModuleRelationType();
        abstract PGRelationTypes getPackageRelationType();
        abstract PGRelationTypes getOnlyToRelationType();
    }
    private void addOpensOrExports(NodeWrapper moduleNode, PackageElement packageElement, List<? extends ModuleElement> targetModules, DirectiveType directiveType){
        NodeWrapper exportsNode =
                DatabaseFacade.CURRENT_DB_FACADE.get().createNodeWithoutExplicitTree(directiveType.getNodeType());
        moduleNode.createRelationshipTo(exportsNode, directiveType.getModuleRelationType());
        NodeWrapper packageNode =
                PACKAGE_MANAGER.get().getOrCreateExternalPackage((Symbol.PackageSymbol) packageElement);
        exportsNode.createRelationshipTo(packageNode, directiveType.getPackageRelationType());
        boolean isRestricted = targetModules != null;
        exportsNode.setProperty("isRestricted", isRestricted);
        if (isRestricted)
            for (ModuleElement targetModule : targetModules)
                exportsNode.createRelationshipTo(getOrCreateExternalModule((Symbol.ModuleSymbol) targetModule),
                        directiveType.getOnlyToRelationType());
    }
}
