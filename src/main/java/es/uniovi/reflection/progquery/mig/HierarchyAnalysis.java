package es.uniovi.reflection.progquery.mig;

import es.uniovi.reflection.progquery.database.relations.CGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.node_wrappers.RelationshipWrapper;
import org.neo4j.graphdb.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class HierarchyAnalysis {
    private Map<NodeWrapper, InfoFromSubtypes> typeToInheritedInfo = new HashMap<>();
    private Set<NodeWrapper> trustableInv;
    public HierarchyAnalysis(Set<NodeWrapper> superCalls) {
        this.trustableInv = superCalls;
    }

    private static class InfoFromSubtypes {
        Map<String, Set<NodeWrapper>> transitiveOverridersMethods = new HashMap<>();
        Map<NodeWrapper, Map<String, NodeWrapper>> subtypesToLastOverrider = new HashMap<>();
    }

    public InfoFromSubtypes dynamicMethodCallAnalysis(NodeWrapper typeDec) {
        InfoFromSubtypes inheritedInfo = typeToInheritedInfo.get(typeDec);
        if (inheritedInfo != null)
            return inheritedInfo;

        inheritedInfo = new InfoFromSubtypes();

        for (RelationshipWrapper subTypeRel : typeDec
                .getRelationships(Direction.INCOMING, TypeRelations.IS_SUBTYPE_EXTENDS,
                        TypeRelations.IS_SUBTYPE_IMPLEMENTS)) {
            NodeWrapper subType = subTypeRel.getStartNode();
            InfoFromSubtypes infoFromSubtypes = dynamicMethodCallAnalysis(subType);
            inheritedInfo.subtypesToLastOverrider.putAll(infoFromSubtypes.subtypesToLastOverrider);
            Map<String, Set<NodeWrapper>> subtypeOverriders = infoFromSubtypes.transitiveOverridersMethods;
            for (Entry<String, Set<NodeWrapper>> entry : subtypeOverriders.entrySet()) {
                Set<NodeWrapper> groupedMethods = inheritedInfo.transitiveOverridersMethods.get(entry.getKey());
                if (groupedMethods == null)
                    inheritedInfo.transitiveOverridersMethods.put(entry.getKey(), groupedMethods = new HashSet<>());
                groupedMethods.addAll(entry.getValue());
            }
        }
        Iterable<RelationshipWrapper> declaredFields =
                typeDec.getRelationships(Direction.OUTGOING, RelationTypes.DECLARES_FIELD), declaredMethods =
                typeDec.getRelationships(Direction.OUTGOING, RelationTypes.DECLARES_METHOD);
        for (Entry<NodeWrapper, Map<String, NodeWrapper>> subTypeInfo : inheritedInfo.subtypesToLastOverrider
                .entrySet()) {
            fieldAnalysis(subTypeInfo.getKey(), declaredFields);
            for (RelationshipWrapper declaredMethodRel : declaredMethods) {
                NodeWrapper declaredMethod = declaredMethodRel.getEndNode();
                if ((boolean) declaredMethod.getProperty("isStatic"))
                    continue;
                String typedName =
                        declaredMethod.getProperty("fullyQualifiedName").toString().split(":")[1].split("\\)")[0];
                NodeWrapper overriderMethodInThisSubtype = subTypeInfo.getValue().get(typedName);
                if (overriderMethodInThisSubtype == null) {
                    subTypeInfo.getKey().createRelationshipTo(declaredMethod, TypeRelations.INHERITS_METHOD);
                } else {
                    subTypeInfo.getValue().get(typedName).createRelationshipTo(declaredMethod, TypeRelations.OVERRIDES);
                    inheritedInfo.subtypesToLastOverrider.get(subTypeInfo.getKey()).remove(typedName);
                }
            }
        }

        inheritedInfo.subtypesToLastOverrider.put(typeDec, new HashMap<>());
        for (RelationshipWrapper declaredMethodRel : declaredMethods) {
            NodeWrapper declaredMethod = declaredMethodRel.getEndNode();
            if ((boolean) declaredMethod.getProperty("isStatic"))
                continue;
            String typedName =
                    declaredMethod.getProperty("fullyQualifiedName").toString().split(":")[1].split("\\)")[0];
            inheritedInfo.subtypesToLastOverrider.get(typeDec).put(typedName, declaredMethod);

            Set<NodeWrapper> overriderMethods = inheritedInfo.transitiveOverridersMethods.get(typedName);
            boolean isAbstract = (boolean) declaredMethod.getProperty("isAbstract");

            Iterable<RelationshipWrapper> invocationRels =
                    declaredMethod.getRelationships(Direction.INCOMING, CGRelationTypes.REFERS_TO);
            if (overriderMethods == null)
                inheritedInfo.transitiveOverridersMethods.put(typedName, overriderMethods = new HashSet<>());

            else {
                boolean mayRefer = !isAbstract || overriderMethods.size() > 1;
                overriderMethods.forEach(ovMethod -> {
                    if (!(boolean) ovMethod.getProperty("isAbstract"))
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
            overriderMethods.add(declaredMethod);
        }

        typeToInheritedInfo.put(typeDec, inheritedInfo);
        return inheritedInfo;
    }

    private void fieldAnalysis(NodeWrapper subtype, Iterable<RelationshipWrapper> declaredFields) {
        for (RelationshipWrapper declaredFieldRel : declaredFields)
            if (!(Boolean) declaredFieldRel.getEndNode().getProperty("isStatic"))
                subtype.createRelationshipTo(declaredFieldRel.getEndNode(), TypeRelations.INHERITS_FIELD);
    }
}
