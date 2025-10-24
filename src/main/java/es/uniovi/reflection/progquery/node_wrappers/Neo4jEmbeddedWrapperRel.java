package es.uniovi.reflection.progquery.node_wrappers;

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import es.uniovi.reflection.progquery.database.relations.*;
import org.neo4j.graphdb.Relationship;

public class Neo4jEmbeddedWrapperRel implements RelationshipWrapper {
	private Relationship relationship;

	public Neo4jEmbeddedWrapperRel(Relationship relationship) {
		super();
		this.relationship = relationship;
	}

	@Override
	public void setProp(String name, Object value) {
		relationship.setProperty(name, value);
	}

	@Override
	public boolean hasProperty(String string) {
		return relationship.hasProperty(string);
	}

	@Override
	public Object getProperty(String string) {
		return relationship.getProperty(string);
	}

	@Override
	public Set<Entry<String, Object>> getAllProperties() {
		return relationship.getAllProperties().entrySet();
	}

	@Override
	public RelationTypesInterface getType() {
		return nameToEnum(relationship.getType().name());
	}

	static RelationTypesInterface nameToEnum(String name) {
		List<Function<String, RelationTypesInterface>> valueOfFuncs = List.of(
				ASTRelationTypes::valueOf,
				TypeRelations::valueOf,
				PDGRelationTypes::valueOf,
				CFGRelationTypes::valueOf,
				CGRelationTypes::valueOf,
				PGRelationTypes::valueOf,
				CDGRelationTypes::valueOf
		);
		for(Function<String, RelationTypesInterface> valueOf : valueOfFuncs)
			try {
				return valueOf.apply(name);
			} catch (IllegalArgumentException e) {
				continue;
			}
		throw new IllegalArgumentException("No enum constant for relation type name: " + name);
	}

	@Override
	public NodeWrapper getStartNode() {
		return new Neo4jEmbeddedWrapperNode(relationship.getStartNode());
	}

	@Override
	public NodeWrapper getEndNode() {
		return new Neo4jEmbeddedWrapperNode(relationship.getEndNode());
	}

	@Override
	public void delete() {
		relationship.delete();
		}

	@Override
	public String getTypeString() {
		return relationship.getType().name();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((relationship == null) ? 0 : relationship.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Neo4jEmbeddedWrapperRel other = (Neo4jEmbeddedWrapperRel) obj;
		if (relationship == null) {
			if (other.relationship != null)
				return false;
		} else if (!relationship.equals(other.relationship))
			return false;
		return true;
	}
}
