package es.uniovi.reflection.progquery.mig;

import es.uniovi.reflection.progquery.ast.MethodSigInfo;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.ASTRelationTypes;
import es.uniovi.reflection.progquery.database.relations.CGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import es.uniovi.reflection.progquery.utils.JavacInfo;
import org.neo4j.graphdb.Direction;

import java.util.*;
import java.util.Map.Entry;

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.IS_ABSTRACT_PROP;
import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.IS_STATIC_PROP;

public class HierarchyAnalysis {
    private Map<NodeWrapper, InfoFromSubtypes> typeToInheritedInfo = new HashMap<>();
    private Set<NodeWrapper> trustableInv;
    private Map<NodeWrapper, MethodSigInfo> methodNodeToSigInfo;
    public HierarchyAnalysis(Set<NodeWrapper> superCalls, Map<NodeWrapper, MethodSigInfo> methodNodeToMethodSymbol) {
        this.trustableInv = superCalls;
        this.methodNodeToSigInfo = methodNodeToMethodSymbol;
    }

    private static class InfoFromSubtypes {
        Map<NodeWrapper, Set<NodeWrapper>> methodToTransitiveOverriders = new HashMap<>();
        Map<NodeWrapper, Map<MethodSigInfo, Set<NodeWrapper>>> subtypesToPossibleOverriders = new HashMap<>();
    }


    public InfoFromSubtypes dynamicMethodCallAnalysis(NodeWrapper typeDec) {
        InfoFromSubtypes inheritedInfo = typeToInheritedInfo.get(typeDec);
        if (inheritedInfo != null)
            return inheritedInfo;

        inheritedInfo = new InfoFromSubtypes();

        List<RelationshipWrapper> subtypeRels = new ArrayList<>();
        subtypeRels.addAll(typeDec.getRelationships(Direction.INCOMING, TypeRelations.EXTENDS_CLASS,
                TypeRelations.IMPLEMENTS_INTERFACE));
        if(typeDec.hasLabel(NodeTypes.GENERIC_TYPE_DEC))
            for(RelationshipWrapper parameterizesType: typeDec.getRelationships(Direction.INCOMING, TypeRelations.PARAMETERIZES_TYPE))
                subtypeRels.addAll(parameterizesType.getStartNode().getRelationships(Direction.INCOMING, TypeRelations.EXTENDS_CLASS,
                        TypeRelations.IMPLEMENTS_INTERFACE));

        for (RelationshipWrapper subTypeRel : subtypeRels) {
            NodeWrapper subType = subTypeRel.getStartNode();
            InfoFromSubtypes infoFromSubtype = dynamicMethodCallAnalysis(subType);
            inheritedInfo.subtypesToPossibleOverriders.putAll(infoFromSubtype.subtypesToPossibleOverriders);
            inheritedInfo.methodToTransitiveOverriders.putAll(infoFromSubtype.methodToTransitiveOverriders);
//            Map<NodeWrapper, Set<NodeWrapper>> subtypeOverriders = infoFromSubtype.transitiveOverridersMethods;
//            for (Entry<NodeWrapper, Set<NodeWrapper>> entry : subtypeOverriders.entrySet()) {
//                Set<NodeWrapper> groupedMethods = inheritedInfo.transitiveOverridersMethods.get(entry.getKey());
//                if (groupedMethods == null)
//                    inheritedInfo.transitiveOverridersMethods.put(entry.getKey(), groupedMethods = new HashSet<>());
//                groupedMethods.addAll(entry.getValue());
//            }
        }

        Iterable<RelationshipWrapper> declaredFields =
                typeDec.getRelationships(Direction.OUTGOING, ASTRelationTypes.DECLARES_FIELD), declaredMethods =
                typeDec.getRelationships(Direction.OUTGOING, ASTRelationTypes.DECLARES_METHOD);
        for (Entry<NodeWrapper, Map<MethodSigInfo, Set<NodeWrapper>>> subTypeInfo :
                inheritedInfo.subtypesToPossibleOverriders.entrySet()) {
            fieldAnalysis(subTypeInfo.getKey(), declaredFields);
            for (RelationshipWrapper declaredMethodRel : declaredMethods) {
                NodeWrapper declaredMethod = declaredMethodRel.getEndNode();
                if ((boolean) declaredMethod.getProperty(IS_STATIC_PROP))
                    continue;
                MethodSigInfo methodSigInfo = methodNodeToSigInfo.get(declaredMethod);
                if(methodSigInfo==null)
                    continue;

                Set<NodeWrapper> possibleOverridersForSignature = subTypeInfo.getValue().get(methodSigInfo);
                NodeWrapper overrider = null;
                if (possibleOverridersForSignature != null)
                    for (NodeWrapper possibleOverrider : possibleOverridersForSignature) {
                        MethodSigInfo possibleOverriderSigInfo = methodNodeToSigInfo.get(possibleOverrider);
                        if(possibleOverriderSigInfo==null)
                            continue;
                        if (JavacInfo.isSubSignatureOwn(possibleOverriderSigInfo.getExecutableType(),
                                methodSigInfo.getExecutableType())) {
                            overrider = possibleOverrider;
                            break;
                        }
                    }
                if (overrider != null) {
                    overrider.createRelationshipTo(declaredMethod, TypeRelations.OVERRIDES);
                    possibleOverridersForSignature.remove(overrider);
                    inheritedInfo.methodToTransitiveOverriders.putIfAbsent(declaredMethod, new HashSet<>());
                    inheritedInfo.methodToTransitiveOverriders.get(declaredMethod).add(overrider);
                    Set<NodeWrapper> transitiveOverriders = inheritedInfo.methodToTransitiveOverriders.get(overrider);
                    if(transitiveOverriders!=null)
                        inheritedInfo.methodToTransitiveOverriders.get(declaredMethod).addAll(transitiveOverriders);
                    inheritedInfo.methodToTransitiveOverriders.remove(overrider);
                } else
                    subTypeInfo.getKey().createRelationshipTo(declaredMethod, TypeRelations.INHERITS_METHOD);
            }
        }

        Map<MethodSigInfo,Set<NodeWrapper>> possibleOverridersForType = new HashMap<>();
        inheritedInfo.subtypesToPossibleOverriders.put(typeDec, possibleOverridersForType);
        for (RelationshipWrapper declaredMethodRel : declaredMethods) {
            NodeWrapper declaredMethod = declaredMethodRel.getEndNode();
            if ((boolean) declaredMethod.getProperty(IS_STATIC_PROP))
                continue;
            MethodSigInfo methodSigInfo = methodNodeToSigInfo.get(declaredMethod);
            if(methodSigInfo==null)
                continue;

             Set<NodeWrapper> possibleOverriders= possibleOverridersForType.get(methodSigInfo);
            if(possibleOverriders==null)
                possibleOverridersForType.put(methodSigInfo, possibleOverriders=new HashSet<>());
            possibleOverriders.add(declaredMethod);

            Set<NodeWrapper> overriderMethods = inheritedInfo.methodToTransitiveOverriders.get(declaredMethod);
            boolean isAbstract = (boolean) declaredMethod.getProperty(IS_ABSTRACT_PROP);

            Iterable<RelationshipWrapper> invocationRels =
                    declaredMethod.getRelationships(Direction.INCOMING, CGRelationTypes.REFERS_TO);
            if (overriderMethods != null){
                boolean mayRefer = !isAbstract || overriderMethods.size() > 1;
                overriderMethods.forEach(ovMethod -> {
                    if (!(boolean) ovMethod.getProperty(IS_ABSTRACT_PROP))
                        invocationRels.forEach(r -> {
                            if (!trustableInv.contains(r.getStartNode()))
                                r.getStartNode().createRelationshipTo(ovMethod,
                                        mayRefer ? CGRelationTypes.MAY_REFER_TO : CGRelationTypes.REFERS_TO);
                        });
                });
                if (!isAbstract)
                    invocationRels.forEach(r -> {
                        if (!trustableInv.contains(r.getStartNode())) {
                            r.getStartNode().createRelationshipTo(declaredMethod, CGRelationTypes.MAY_REFER_TO);
                            r.delete();
                        }
                    });
            }
            if (isAbstract)
                invocationRels.forEach(r -> r.delete());
//            overriderMethods.add(declaredMethod);
        }

        typeToInheritedInfo.put(typeDec, inheritedInfo);
        return inheritedInfo;
    }

    private void fieldAnalysis(NodeWrapper subtype, Iterable<RelationshipWrapper> declaredFields) {
        for (RelationshipWrapper declaredFieldRel : declaredFields)
            if (!(Boolean) declaredFieldRel.getEndNode().getProperty(IS_STATIC_PROP))
                subtype.createRelationshipTo(declaredFieldRel.getEndNode(), TypeRelations.INHERITS_FIELD);
    }



}
