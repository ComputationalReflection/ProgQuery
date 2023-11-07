package es.uniovi.reflection.progquery.database.relations;

public enum CDGRelationTypes  implements RelationTypesInterface {
	DEPENDS_ON_PACKAGE,
	DEPENDS_ON_NON_DECLARED_PACKAGE,
	PACKAGE_HAS_COMPILATION_UNIT,
	PROGRAM_DECLARES_PACKAGE,
	USES_TYPE_DEF,
	HAS_INNER_TYPE_DEF
}
