package es.uniovi.reflection.progquery.utils.dataTransferClasses;

import es.uniovi.reflection.progquery.database.relations.PartialRelation;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;
import es.uniovi.reflection.progquery.database.relations.SimplePartialRelation;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

import java.util.Objects;

public class Pair<X, Y> {
	
	private final X x;
	private final Y y;
	
	private Pair(X x, Y y) {
		this.x = x;
		this.y = y;
	}
	
	public static <X,Y>  Pair<X,Y> create(X x, Y y)
	{
		return new Pair<>(x, y);
	}
	public X getFirst() {
		return x;
	}

	public Y getSecond() {
		return y;
	}


	public static <T extends RelationTypesInterface> Pair<PartialRelation<T>, Object> createPair(NodeWrapper node, T r,
			Object arg) {
		return Pair.create(new SimplePartialRelation<>(node, r), arg);
	}

	public static <T extends RelationTypesInterface> Pair<PartialRelation<T>, Object> createPair(NodeWrapper node, T r) {
		return Pair.create(new SimplePartialRelation<>(node, r), null);
	}

	public static <T extends RelationTypesInterface> Pair<PartialRelation<T>, Object> createPair(PartialRelation<T> rel) {
		return Pair.create(rel, null);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Pair<?, ?> pair))
			return false;
        return Objects.equals(x, pair.x) && Objects.equals(y, pair.y);
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y);
	}

	@Override
	public String toString() {
		return "Pair [x=" + x + ", y=" + y + "]";
	}

}
