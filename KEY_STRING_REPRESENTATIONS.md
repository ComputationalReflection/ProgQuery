# Nombres de claves sin hashes de objetos

La corrección está implementada: se añaden `toString()` explícitos a las doce
clases identificadas y se presenta `ParameterizedTypeKey` como tipo con argumentos.
No se modifican `equals()` ni `hashCode()`. Las mediciones del informe de
almacenamiento corresponden a los binarios congelados antes de esta corrección;
siguen comparando exclusivamente las dos optimizaciones acordadas.

## Propiedades afectadas

La representación heredada `Clase@hash` se localizó en
**`fullyQualifiedName`** (`NodeProperties.FULL_NAME`).
En `TypeVisitor.visitTypeVariable()` se construye mediante:

```java
DatabaseFacade.getTypeProperties(t.toString(), key.toString())
```

El segundo argumento se persiste como `fullyQualifiedName`; el primero se
persiste como `simpleName`.

| Nodo / clave | Origen del defecto anterior | Ejemplo observado antes de corregirlo |
| --- | --- | --- |
| `TYPE_VARIABLE`, `TypeVariableKey` | Su `toString()` concatena `ownerKey`. Si es `CallableKey`, esta hereda el de `Object`. | `...keys.CallableKey@b3461cbc:I` |
| `TYPE_VARIABLE`, `CapturedTypeKey` | No tiene `toString()` propio. | `...keys.type.CapturedTypeKey@<hexadecimal>` |
| Nombre de tipo compuesto | `visitUnion()` concatena el `fullyQualifiedName` de sus alternativas. Es la misma propiedad, con una posible propagación del problema. | Posible por composición; no confirmado como caso almacenado adicional. |

No se ha encontrado esa ruta de claves en `simpleName`, `completeName`, `name`,
`actualType` o `resultingType`: se obtienen de nombres y tipos de javac o de getters
específicos. Sin embargo, se identificó otra vía: el texto diagnóstico `capture#123`
de javac también utiliza un hash. En el JDK 22 instalado,
`Type.CapturedType.toString()` calcula el número así:

```java
(hashCode() & 0xFFFFFFFFL) % Printer.PRIME
```

La explicación inicial que lo distinguía como un número ajeno a los hashes era
incorrecta. Esta vía se ha corregido también. Se observó en `simpleName`,
`fullyQualifiedName` y `actualType`.

Las extracciones de auditoría normalizan los hashes de `keys.type.*` como `@hash`.
Ese texto es una normalización del auditor; el valor persistido se originó en el
`toString()` heredado. Los hashes de `CallableKey` se conservaron en las huellas
principales y aparecen expresamente en los ficheros de diferencias.

`InfoToInsert` no genera estas cadenas: recibe y serializa propiedades ya creadas.
La corrección debe hacerse en sus productores, no eliminando `@...` durante la
inserción.

## Correcciones directas aplicadas

### CallableKey

Se reutiliza la firma que ya se emplea para el nombre completo de los métodos:

```java
@Override
public String toString() {
    return getFullyQualifiedName();
}
```

Así, `TypeVariableKey.toString()` deja de producir `CallableKey@<hash>:T` y pasa a
producir, por ejemplo, `com.example.C:identity<T>(T)T:T`, de acuerdo con el formato
que devuelva la firma existente. La firma conserva la información de sobrecarga;
no se debe sustituir por el nombre simple del método.

### CapturedTypeKey

Se representan los tres componentes que ya utiliza su igualdad:

```java
@Override
public String toString() {
    return "capture(wildcard=" + wildcardTypeKey
            + ",upper=" + bound
            + ",lower=" + lower + ")";
}
```

Para que esta implementación no imprima hashes de sus componentes, deben tener
también una representación explícita todas las claves anidadas siguientes.

## Claves con un toString explícito añadido

| Clase | Representación implementada |
| --- | --- |
| `CallableKey` | `getFullyQualifiedName()` |
| `CapturedTypeKey` | `capture(wildcard=...,upper=...,lower=...)` |
| `ArrayTypeKey` | `typeOf + "[]"` |
| `PrimitiveTypeKey` | `kind`: `int`, `boolean`, etc. |
| `WildcardKey` | `wildcard(super=...,extends=...)`, conservando ambos límites |
| `CompoundTypeKey` | `intersection(types=[...])` o `union(types=[...])`, conservando el orden de la lista |
| `MethodTypeKey` | `method(constructor=...,typeVars=[...],receiver=...,params=[...],returns=...,throws=[...])` |
| `VoidTypeKey` | `void` |
| `NullTypeKey` | `null` |
| `ErrorTypeKey` | `<error>` |
| `UnknownTypeKey` | `<unknown>` |
| `NoPackageKey` | `getPackageName()`, actualmente `<Root Package>` |

No todos estos casos están observados como propiedades del grafo: algunos son
componentes posibles de otras claves. Cerrarlos evita que una representación
estructural nueva vuelva a introducir un `Clase@hash` por composición.

`TypeVariableKey`, `TypeDefinitionKey` y `ParameterizedTypeKey` ya tienen
`toString()`, pero componen los de sus miembros. La corrección de los miembros
elimina esos hashes. `ParameterizedTypeKey` presenta ahora
`genericType<arg1,arg2,...>` en lugar de su envoltorio diagnóstico anterior;
tolera componentes ausentes usando `<absent>`.
`FieldKey` hereda el de `ComponentKey`, no el de `Object`; no requiere un override
adicional para este problema. `NoneTypeKey` ya devuelve `NONE`.

## Descripciones de javac sin números de captura

`TypeNames.stable()` sustituye el token diagnóstico `capture#n` por `capture`,
manteniendo el comodín y los límites que siguen al token. Reconoce y conserva
literales entre comillas, incluso con escapes, para no modificar valores de
anotaciones de tipos. No se aplica a los literales de los programas ni a nombres
arbitrarios de usuario o programa.

Se utiliza en las fábricas de propiedades de tipos de `DatabaseFacade`, las
propiedades `actualType` de `GraphUtils`, los nombres de tipos array en
`ASTTypesVisitor`, las descripciones de unión/parámetros en `TypeVisitor` y los
getters de nombres de `CallableKey`. Los campos internos de las claves conservan
sus valores anteriores y `equals()`/`hashCode()` siguen sin cambios. No se modifica
el texto que `KeyTypeVisitor` inspecciona para reconocer las capturas de javac.

## Contrato y validación

- No modificar `equals()` ni `hashCode()` al corregir la representación textual.
- No usar `hashCode()`, `identityHashCode()` ni números de objeto para formar nombres.
- Las variables de tipo deben imprimir propietario y nombre, sin expandir otra vez
  sus límites: esto evita recursión en casos como `T extends Comparable<T>`.
- Una representación estructural debe conservar los campos relevantes y el orden
  de las listas. No debe reducirse un método a su nombre o una captura a `?`.
- Si se exige distinguir ámbitos locales o anónimos en nombres persistidos, su
  identidad debe proceder de origen/posición y propietario estables, no de hashes.
- Probar primitivas, arrays, comodines, capturas, límites recursivos, métodos
  sobrecargados y claves equivalentes creadas en dos contextos javac independientes.
- Revisar las propiedades de nombres y tipos generadas por el analizador y exigir
  ausencia de `...keys.*@<hex>`, sin alterar literales de texto del programa analizado.
- Repetir los contadores y las comprobaciones de herencia y llamadas: el código
  utiliza `fullyQualifiedName` para ordenar algunos análisis globales, por lo que
  no conviene asumir que sustituir cadenas nunca puede afectar a su ejecución.

Esta corrección se aplica separadamente del experimento de almacenamiento,
para no mezclar cambios de nombres con las dos optimizaciones medidas.

## Verificación

`KeyStringRegressionTest` comprueba 34 representaciones en dos contextos javac
independientes: métodos sobrecargados, genéricos, arrays, comodines, capturas,
intersecciones y uniones, límites recursivos, constructores, receptor ausente y
componentes nulos. Verifica que no se herede `Object.toString()`, no se impriman
hashes, no se modifique el hash de las claves al formatearlas y se obtengan las
mismas cadenas entre contextos. Se mantienen las regresiones anteriores.

La validación de integración se guarda en `../report/key-strings-20261112/`.
La auditoría final **no elimina ni normaliza hashes Java en las huellas completas**.
Detecta los patrones de representación de objetos y números diagnósticos de captura en los metadatos
`fullyQualifiedName`, `simpleName`, `completeName`, `actualType`, `name` y
`resultingType`; no modifica literales de los programas.

Para comparar con los datos anteriores se calcula también una huella estructural
que omite el `fullyQualifiedName` de los nodos `TYPE_VARIABLE` y unifica el token
diagnóstico de captura antiguo con el nuevo, pues son los textos que se corrigen
intencionadamente. Se conservan y comparan todas las demás propiedades, etiquetas,
tipos de relación y extremos. Esta comprobación se conserva separada de las huellas
completas. Después se insertan dos veces los programas con el código final y se
exige igualdad de las huellas completas, incluidos los nuevos nombres: en esta
comparación final solo se omite `timestamp`, sin normalizar `capture#n` ni
`Clase@hash`. Se comprueba además que ambos patrones hayan desaparecido.

<!-- KEY_VALIDATION_RESULTS -->

## Resultados de integración

Se reinsertaron RapidOcr, canal, Gephi y James dos veces con la implementación final. La auditoría de esta fase compara las huellas completas omitiendo únicamente `timestamp`: no normaliza ni `Clase@hash` ni `capture#n`. Se exige además que ambos contadores sean cero en los metadatos inspeccionados.

| Programa | Propiedades Clase@hash antes | Después | Propiedades capture#n antes de su corrección | Después | Estable entre dos ejecuciones |
| --- | ---: | ---: | ---: | ---: | --- |
| rapidocr | 10 | 0 | 37 | 0 | Sí |
| canal | 213 | 0 | 758 | 0 | Sí |
| gephi | 329 | 0 | 1818 | 0 | Sí |
| james | 1216 | 0 | 7779 | 0 | Sí |

Los dos contadores anteriores corresponden a comprobaciones realizadas en etapas diferentes: primero se corrigieron las representaciones Object.toString y después se confirmó en el código del JDK el origen de los números de captura. Cuentan propiedades afectadas, no objetos ni capturas distintas. La búsqueda inicial de Clase@hash solo encontró fullyQualifiedName. La comprobación posterior de capture#n encontró también simpleName y actualType.

### Contadores conservados

| Programa | Nodos preparados | Relaciones preparadas | Tareas | Ficheros | Diagnósticos |
| --- | ---: | ---: | ---: | ---: | ---: |
| RapidOcr | 5414 | 11875 | 14 | 28 | 56 |
| canal | 381628 | 957121 | 37 | 573 | 0 |
| gephi | 635464 | 1815169 | 62 | 2754 | 1564 |
| james | 1208884 | 3102764 | 243 | 3672 | 9 |

Todos los resultados son Success=true. Se conservan los recuentos de nodos, arcos por tipo, etiquetas, ficheros, tareas, diagnósticos, versión declarada y avisos. Las huellas estructurales coinciden con las anteriores, permitiendo solo los cambios textuales de nombres previstos. Las huellas completas de las dos ejecuciones finales coinciden, incluidos los nombres nuevos y los extremos de los arcos. No se necesitan correspondencias especiales para las cuatro variables de James.

La auditoría cubre los subgrafos de los programas reinsertados. En James se mantienen los 1208882 nodos y 3102763 arcos alcanzables desde su programa; los dos nodos y un arco adicionales del contador de pendientes ya existían en la referencia. No se migraron ni borraron grafos de otros programas.

Se han superado el build Maven, las 34 representaciones de claves en contextos independientes y las pruebas del formateador con capturas reales, límites recursivos, ausencia de componentes, anotaciones y literales escapados. También se conservan las regresiones de firmas, almacenamiento y coordinación por turnos. Las comparaciones de huellas son de multiconjuntos de propiedades y extremos; no son una prueba formal de isomorfismo entre nodos indistinguibles.

Las compilaciones continúan en paralelo y las visitas al grafo siguen por turnos. No se cambia equals/hashCode ni se reactiva el prototipo de visitantes concurrentes. Los resultados y scripts están en ../report/key-strings-20261112/. No se ha realizado ningún commit.
