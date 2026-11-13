# Optimización del almacenamiento de la inserción lazy

Este informe corresponde al experimento aislado de almacenamiento. Posteriormente
se aplica la corrección de nombres descrita en [KEY_STRING_REPRESENTATIONS.md](KEY_STRING_REPRESENTATIONS.md).
Los binarios y tiempos de las tablas de este informe son anteriores a esa
corrección de nombres y no se han sustituido por mediciones de la versión posterior.

## Alcance

Este experimento compara **solo dos variantes**: la revisión `9cf2211` sin
modificar y esa misma revisión con las dos optimizaciones descritas aquí.
Las mediciones son nuevas y se guardan en
`../report/storage-optimizations-20261112/` del repositorio principal. No se
reutilizan los tiempos del experimento de visitantes concurrentes como resultados
de esta comparación.

Ambas variantes compilan hasta ocho módulos en paralelo y conservan las visitas
al grafo por turnos. No se modifica `ParallelCompilation`, `CompilationScheduler`,
`ProgQuery`, los visitantes, las claves de tipos ni las cachés auxiliares del AST
y del PDG. No se añaden cerrojos, alias de nodos, buffers por tarea ni opciones de
visitas concurrentes.

Al iniciar el trabajo, el submódulo estaba en `6f9ab19`, anterior al paralelismo
de compilaciones aceptado y incompatible con la interfaz que utiliza Core.
La base confirmada para el experimento es `9cf2211`, que recupera las compilaciones
paralelas con visitas ordenadas.

## Cambios de código

### 1. Reutilizar relaciones al sustituir una declaración

`DefinitionCache.putDefinition()` utiliza el nuevo
`Neo4jLazyNode.transferRelationshipsTo()` cuando ambos nodos son del almacenamiento
lazy del driver. La implementación anterior creaba cada relación otra vez, copiaba
sus propiedades y eliminaba la relación original. La nueva conserva el objeto y
su mapa de propiedades, y actualiza únicamente los extremos que apuntaban al nodo
sustituido.

Las adyacencias del nodo nuevo se actualizan y las del antiguo se vacían al
terminar el traslado. Las listas de sus vecinos siguen conteniendo el mismo objeto
de relación, cuyos extremos ya se han actualizado. Se recorren relaciones únicas
para que una autorrelación, presente dos veces en la lista de adyacencias, se
procese una sola vez. Las relaciones paralelas distintas se conservan.

Después se elimina el nodo antiguo del conjunto pendiente y se actualiza la
caché, como antes. No se introduce un alias del nodo antiguo al nuevo. Los otros
tipos de `NodeWrapper` conservan el algoritmo anterior de recreación de arcos.

### 2. Consolidar eliminaciones en una pasada

`InfoToInsert.deleteNode()` y `deleteRel()` registran los objetos en conjuntos
`HashSet`. Ya no buscan y desplazan elementos en los grandes `ArrayList` de
pendientes por cada eliminación.

`prepareInsertion()` compacta las listas con `removeIf`, conserva el orden de los
elementos supervivientes y vacía los conjuntos de eliminaciones. Se ejecuta antes
del log de recuentos y de repartir los lotes a Neo4j. También se utiliza en los
accesores y al preparar consultas, para que no expongan elementos eliminados.
La compactación puede repetirse y admite nuevas inserciones o eliminaciones después.

Las adyacencias se siguen actualizando inmediatamente cuando se elimina una
relación: se aplaza solo su retirada de la lista global pendiente de inserción.
Los conjuntos no necesitan ser concurrentes porque el escritor del grafo sigue
siendo único; cuando comienzan las transacciones de inserción ya no se modifica
el grafo en memoria.

## Pruebas locales

`LazyStorageRegressionTest` comprueba que se mantienen la identidad y propiedades
de los arcos, ambos extremos, las relaciones paralelas y las autorrelaciones; que
se actualizan las adyacencias y la caché; y que las eliminaciones repetidas y la
compactación repetida no alteran el resultado.

Además compara 50 grafos con arcos aleatorios y sustituciones sucesivas contra una
implementación del protocolo anterior de recreación de relaciones. Se comparan
los nodos y los arcos con extremos, tipos y propiedades.

También se ejecutan las regresiones existentes de visitas por turnos, orden de
resultados, tareas vacías, finalización ante fallos, firmas javac y parámetros de
compilación de ProgQuery y Core.

## Método de medición

Se utilizan los mismos proyectos RapidOcr, canal, Gephi y James y las opciones
de compilación extraídas de sus ejecuciones Maven completas. Las opciones se
reproducen en su orden original, con directorios nuevos para fuentes generados,
sin volver a medir el build Maven de las dependencias en cada repetición.

JVM JDK 22.0.2, heap máximo de 48 GiB, ocho compilaciones y ocho trabajadores de
escritura; mismo servidor Neo4j, base `oscar` y nombres de programa. Se borra el
grafo del programa antes de cada inserción. Las variantes se ejecutan en procesos
Java independientes con sus clases compiladas congeladas. Las inserciones
cronometradas no se solapan.

El tiempo principal es `Total Elapsed Time (millis)`: compilaciones, visitas,
análisis finales y escritura en Neo4j. Excluye el borrado previo y el build Maven.
El tiempo total del proceso, que incluye el borrado, se registra aparte y no se
mezcla con el tiempo principal. El pico de working set corresponde al cliente
Java, no a la memoria del servidor Neo4j.

Además de los contadores se extraen huellas de los multiconjuntos de nodos y
relaciones con etiquetas, propiedades, tipos de arcos y extremos. Se omiten
identificadores internos y `timestamp`, y se normalizan números de captura y las
cadenas opacas de claves de tipos que ya se trataron en el experimento anterior.
Las diferencias de la auditoría se conservan para examinarlas; no se presupone
equivalencia únicamente porque coincidan los recuentos.

A continuación figuran los resultados y las comprobaciones finales.

<!-- MEASURED_RESULTS -->

## Resultados finales

Son mediciones nuevas del 12 de noviembre de 2026. Hay cuatro ejecuciones por variante para RapidOcr, canal y Gephi, y dos para James. El orden se alterna: original/optimizada en las parejas impares y optimizada/original en las pares. Se muestran las medianas; para dos ejecuciones la mediana es el punto medio de ambas.

| Programa | Ejecuciones por variante | Original | Con las dos optimizaciones | Reducción | Aceleración |
| --- | ---: | ---: | ---: | ---: | ---: |
| RapidOcr | 4 | 4,257 s | 4,212 s | 1,1 % | 1,01x |
| canal | 4 | 46,239 s | 36,930 s | 20,1 % | 1,25x |
| gephi | 4 | 108,838 s | 65,851 s | 39,5 % | 1,65x |
| james | 2 | 287,236 s | 133,782 s | 53,4 % | 2,15x |

No hay visitantes concurrentes en ninguna columna. Las únicas diferencias de código de producción son las dos optimizaciones de almacenamiento. Los tiempos corresponden al conjunto de ambas; no se ha medido por separado la contribución individual de cada corrección.

La reducción es consistente en canal, Gephi y James: en estas mediciones, incluso la ejecución optimizada más lenta de cada proyecto es más rápida que su referencia más rápida. En RapidOcr, la diferencia ronda el 1 % y los intervalos se solapan; no se considera una mejora apreciable.

### Todas las mediciones individuales

Cada lista contiene repeticiones del mismo binario, en segundos y en orden de ejecución. No son configuraciones adicionales.

| Programa | Original: repeticiones | Optimizada: repeticiones |
| --- | --- | --- |
| RapidOcr | 4,737; 4,266; 4,247; 4,139 | 4,235; 4,189; 4,182; 4,375 |
| canal | 46,764; 45,859; 46,619; 45,235 | 38,793; 34,749; 35,066; 39,730 |
| gephi | 111,418; 107,070; 103,303; 110,605 | 64,138; 67,564; 59,823; 69,643 |
| james | 288,167; 286,304 | 136,526; 131,038 |

### Contadores iguales en todas las ejecuciones

| Programa | Nodos | Relaciones | Tareas | Ficheros | Diagnósticos |
| --- | ---: | ---: | ---: | ---: | ---: |
| RapidOcr | 5414 | 11875 | 14 | 28 | 56 |
| canal | 381628 | 957121 | 37 | 573 | 0 |
| gephi | 635464 | 1815169 | 62 | 2754 | 1564 |
| james | 1208884 | 3102764 | 243 | 3672 | 9 |

Todas las ejecuciones terminan con `Success=true`. Se comprueban también igualdad de versión javac declarada y del indicador de avisos. Los objetivos declarados son 1.8 para RapidOcr/canal, 17 para Gephi y la combinación `21, 11,21` para James; la JVM del analizador es siempre JDK 22.0.2.

### Comparación del contenido del grafo

- RapidOcr, canal y Gephi: las huellas completas de nodos y relaciones coinciden entre la referencia y la variante optimizada.
- James: los recuentos y la estructura coinciden. La auditoría principal conserva cuatro diferencias en `fullyQualifiedName` de variables de tipo, con formato `CallableKey@<hash>:T` o `:I`. Se estableció una correspondencia entre esas cuatro variables y se verificaron sus 52 relaciones incidentes: todos los demás nodos y todos los arcos coinciden.
- Se repitió además la auditoría entre dos ejecuciones del binario original de James, sin optimizaciones. Aparece el mismo defecto de nombres con hashes; la comprobación adicional confirma la igualdad estructural. Esto evita atribuir a estas correcciones una inestabilidad de nombres ya existente.
- No se declara igualdad literal de esas cuatro propiedades. El defecto de representación está descrito y tiene una propuesta separada en [KEY_STRING_REPRESENTATIONS.md](KEY_STRING_REPRESENTATIONS.md); no se han aplicado cambios de nombres en este experimento.
- James tiene 1208882 nodos y 3102763 relaciones alcanzables desde el programa, frente a los 1208884/3102764 pendientes totales. La diferencia de dos nodos y una relación ya existe en la referencia y no cambia con las optimizaciones.

Las huellas comparan multiconjuntos de propiedades, etiquetas y extremos identificados por sus propiedades; son más exigentes que los recuentos, pero no una prueba formal de isomorfismo entre nodos indistinguibles. Se conservan las extracciones, los ficheros de diferencias y las comprobaciones adicionales, sin sobrescribir las huellas principales.

### Memoria del cliente

| Programa | Mediana del pico original (MiB) | Mediana del pico optimizado (MiB) |
| --- | ---: | ---: |
| RapidOcr | 535,0 | 547,0 |
| canal | 5159,0 | 5216,0 |
| gephi | 8267,5 | 9117,5 |
| james | 17137,5 | 16865,5 |

No se observa un ahorro general de memoria. En Gephi el pico mediano aumenta 850 MiB en estas ejecuciones. El marcado diferido conserva referencias hasta compactar y el working set también depende del GC; estas mediciones no aíslan la contribución de cada factor.

Son picos aproximados de working set del proceso Java cliente, muestreados cada segundo, no de la memoria de Neo4j. Los valores dependen también del momento de recolección de basura. El servidor es remoto: red, carga y cachés pueden introducir variación. Las repeticiones alternadas reducen el sesgo, pero no constituyen un estudio estadístico exhaustivo ni garantizan el mismo porcentaje en otros proyectos.

## Estado de la entrega

Build Maven y regresiones locales superados. La comprobación de las clases congeladas confirma que el coordinador, los visitantes y las claves son idénticos entre variantes. Las clases de referencia principales coinciden también con las archivadas esta mañana. Solo cambian cinco archivos de producción: `DefinitionCache`, `Neo4jLazyNode`, `Neo4jLazyRelationship`, `InfoToInsert` y `DriverLazyInsertionService`. Se añade la regresión de almacenamiento y esta documentación.

No se han incorporado las visitas concurrentes ni el resto del prototipo. No se ha realizado ningún commit. Los datos sin resumir, las clases congeladas y los scripts de reproducción están en `../report/storage-optimizations-20261112/`, excluido de Git.

## Prueba adicional de rendimiento: modeldb

Realizada el 12 de noviembre de 2026, con una ejecución completa por versión. Esta prueba mide exclusivamente rendimiento: no se auditan nodos, relaciones ni equivalencia del grafo.

| Versión | Commit | Análisis e inserción | Duración |
| --- | --- | ---: | --- |
| Actual, almacenamiento lazy optimizado y representaciones de claves corregidas | `34b89b2` | 191802 ms | 3 min 11,802 s |
| Referencia sin optimización del almacenamiento lazy | `9cf2211` | 1244837 ms | 20 min 44,837 s |
| `feature/parallel_visitors`, visitors concurrentes activados | `528374b` | 177927 ms | 2 min 57,927 s |

La versión actual reduce el tiempo un **84,59 %** respecto a `9cf2211` (**6,49 veces** más rápida). La rama de visitors paralelos tarda **13,875 s menos** que la actual, una reducción adicional del **7,23 %**. Este último dato no valida la corrección de esa rama descartada.

Se utilizó `D:\sources\modeldb`, usuario/programa `oscar/modeldb`, servidor remoto habitual, JDK 22, heap máximo de 48 GiB, límite de 8 compiladores y 8 transacciones de escritura concurrentes. Maven produjo cuatro tareas: se ejecutaron con hasta cuatro compiladores. Los lotes conservan 80000 operaciones. Las variantes históricas se compilaron en copias aisladas y se usó el mismo core y las mismas dependencias.

Maven `clean compile` se ejecutó una vez como preparación. Las tres variantes reprodujeron la misma lista ordenada de opciones javac, con salidas temporales de anotaciones independientes. La métrica de la tabla es `Total Elapsed Time (millis)` de `Insertion Summary`; incluye análisis, construcción e inserción remota, y excluye Maven, arranque y borrado previo. Se eliminó únicamente el grafo `oscar/modeldb` antes de cada ejecución.

| Ejecución válida | Inicio del proceso (hora local) | Fin del proceso | Tiempo completo, incluida limpieza |
| --- | --- | --- | ---: |
| Actual | 21:42:15 | 21:45:33 | 197645 ms |
| Referencia | 22:52:14 | 23:13:04 | 1249875 ms |
| Visitors paralelos | 23:13:04 | 23:18:47 | 342456 ms |

El primer intento de la referencia quedó interrumpido sin resumen final; se archivó aparte y no se contabiliza. Las tres ejecuciones válidas finalizaron con `Success=true` y código de salida 0. No se reiniciaron las cachés del servidor remoto. Al ser una muestra por versión, estos resultados describen estas ejecuciones y no una variabilidad estadística.

Los logs, JSON, clases congeladas, revisiones y metodología están en `../report/modeldb-performance-20261112/`. La copia de trabajo de producción se mantuvo en su commit actual, sin cambiar de rama ni modificar código.
