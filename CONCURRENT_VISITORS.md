# Experimento de visitas concurrentes — 12 de noviembre de 2026

## Estado y criterio de aceptación

**Prototipo experimental; no apto para activar por defecto.** La ejecución de
visitantes en paralelo funciona y supera las pruebas de integridad concurrente,
pero no conserva siempre el grafo de la versión anterior. Se exige igualdad de
nodos, relaciones, diagnósticos y contenido del grafo; un tiempo menor con otro
grafo no es una optimización aceptada.

El modo habitual mantiene el orden previo de visitas. Para reproducir el
experimento se añade la propiedad de JVM `-Dprogquery.concurrentVisitors=true`,
antes del nombre de la clase principal. `-mcc=8` sigue limitando las compilaciones;
no activa por sí solo las visitas concurrentes. Con una sola tarea se conserva
el camino secuencial. No se ha realizado ningún commit.

## Cambios de código

| Estado | Tratamiento del prototipo |
| --- | --- |
| Contexto javac, símbolos, file manager, diagnósticos | Privados por compilación, como en la implementación anterior. |
| Estado PDG de método, variables locales, ramas y última asignación | `PDGProcessing.fork()` por tarea; se comparten solamente las cachés de campos y `this`. |
| Pila de invocaciones y paquete actual | `ThreadLocal` con limpieza al terminar la tarea. |
| Colecciones AST compartidas | Conjuntos y mapas concurrentes; análisis globales después de reunir todos los `Future`. |
| Cachés de declaraciones | Accesos sincronizados por caché. Las fábricas recursivas y la sustitución de declaraciones usan un cerrojo de declaraciones por análisis. |
| Propiedades y etiquetas | Monitor del elemento y copias para las lecturas que devuelven colecciones. |
| Adyacencias de una relación | Se bloquean los dos extremos por un orden interno estable, independiente del identificador de Neo4j. |
| Sustitución de declaración externa por fuente | Barrera de topología exclusiva breve; se redirigen las relaciones existentes y las referencias tardías hacia el nodo nuevo. |
| Nodos y relaciones pendientes | Buffer privado por tarea; conjuntos concurrentes de eliminaciones; consolidación después de terminar los escritores. |
| Orden de resultados y errores | Resultados en orden de entrada, conservación del primer error y espera de terminación antes de limpiar recursos. |

`ParallelCompilation.visitGraph()` deja ejecutar la acción sin esperar turno en
el modo experimental. El coordinador ya no permanece bloqueado durante la visita.
Los cerrojos están en las operaciones sobre los datos anteriores.

La concurrencia es entre unidades pertenecientes a tareas javac independientes.
No se ejecutan varios visitantes simultáneos sobre un mismo `JavacTask`/`Context`;
las unidades de una sola tarea siguen el flujo de eventos de su compilador.

### Alcance real de los bloqueos

Una creación de relación toma el lado compartido de `GraphAccess.topology` y los
monitores de los dos nodos, en orden de `lockOrder`. Relaciones con extremos
distintos pueden añadirse simultáneamente. El `finally` libera la barrera; la
salida de `synchronized` libera los monitores incluso si se lanza una excepción.

Una sustitución toma el lado exclusivo de la misma barrera. Espera a que terminen
las operaciones de adyacencia iniciadas y evita que comiencen otras mientras se
retocan los extremos. Después publica un alias del nodo antiguo hacia el nuevo.
Así, una referencia obtenida antes de la sustitución puede seguir creando arcos
hacia la declaración definitiva.

La fábrica recursiva de declaraciones todavía tiene exclusión por análisis:
crear tipos puede crear métodos, parámetros, paquetes y otros tipos. Repartir
esa operación entre cerrojos por clave sin tratar sus ciclos introduciría
interbloqueos. Este cerrojo no abarca el recorrido completo de una unidad,
pero limita el paralelismo al materializar muchas dependencias nuevas.

## Por qué la integridad concurrente no garantiza el mismo resultado

`DefinitionCache.getOrCreateType()` conserva la primera representación insertada
para una clave. `TypeDefinitionKey` identifica el nombre y el propietario;
`ModuleKey` contiene el nombre del módulo Java, no la versión del JAR ni el
classpath de la compilación. Dos módulos Maven pueden por tanto aportar
representaciones diferentes a una misma entrada.

En canal se han observado diferencias en métodos de Elasticsearch y Guava,
declaraciones sintéticas de enums, parámetros genéricos y la representación de
capturas. Por ejemplo, el grafo anterior contiene `Joiner.withKeyValueSeparator(char)`
y una ejecución concurrente no lo contiene. Esto no se corrige haciendo atómica
una operación sobre un mapa: el resultado sigue dependiendo de quién la ejecuta
primero. También cambia qué miembros externos se materializan antes de que el
visitante registre los fuentes.

Para garantizar equivalencia hace falta definir y aplicar una prioridad
determinista de procedencias y materialización, incluida la reconciliación de
miembros, parámetros y arcos creados de manera recursiva. Otra posibilidad es
recoger por tarea operaciones semánticas y consolidarlas en orden. Introducir la
versión del JAR en la clave puede ser útil para modelar dependencias, pero cambiaría
la identidad y los recuentos: no satisface por sí solo el requisito de este trabajo.

## Metodología

La referencia es la versión aceptada con ocho compiladores y visitas ordenadas,
no la versión inicial con un solo compilador. Se congelaron sus clases compiladas
antes de editar. Se usaron el mismo JDK 22.0.2, heap máximo de 48 GiB, ocho
compilaciones, ocho transacciones de escritura, fuentes y servidor Neo4j.
El equipo tiene 8 núcleos físicos y 16 procesadores lógicos.

Cada prueba ejecuta Core con `-mvn`; elimina previamente el grafo del programa.
Se desactivaron por igual la caché Develocity local/remota y la comprobación
Scalafix para permitir la compilación de James. El tiempo de inserción es
`Total Elapsed Time (millis)`: incluye compilaciones, análisis finales y escritura
en Neo4j; excluye el build Maven y el borrado inicial. El tiempo total del proceso
se registra aparte. Las pruebas cronometradas no utilizan depurador.

Además del resumen se extraen nodos y arcos con etiquetas, propiedades y extremos.
Se comparan multiconjuntos de huellas SHA-256, sin los identificadores internos
de Neo4j ni `timestamp`; se normalizan los números de captura de javac y los
hashes impresos por `toString()` de claves de tipos. No se normalizan nombres
de métodos, tipos, etiquetas o arcos para ocultar diferencias.

La comprobación es más fuerte que contar nodos y arcos, aunque no es una prueba
formal de isomorfismo: nodos con propiedades idénticas comparten huella. En James,
el subgrafo alcanzable del programa ya tenía en la referencia dos nodos y una
relación menos que el total pendiente de inserción; ambas medidas se mantienen
separadas.

La referencia de `progquery` es el commit
`9cf2211e6c5b6907df044d0a87ce10e48ee9572c`. Los datos completos, clases congeladas, scripts y evidencia del depurador están
en `../report/concurrent-visitors-20261112/` del repositorio principal, excluido
de Git. Los resultados numéricos y la validación final figuran abajo.

## Resultados de las ejecuciones completas

Una ejecución por proyecto y variante, sin depurador. La columna de reducción
es **tiempo observado**, no una mejora aceptada cuando el grafo cambia.

| Programa | Referencia: visitas ordenadas | Prototipo: visitas concurrentes | Reducción observada | Equivalencia del grafo |
| --- | ---: | ---: | ---: | --- |
| RapidOcr | 5,204 s | 4,074 s | 21,7 % | Huellas de nodos y relaciones iguales |
| canal | 53,469 s | 39,466 s | 26,2 % | **Falla** |
| Gephi | 114,906 s | 61,021 s | 46,9 % | **Falla** |
| James | 311,724 s | 101,930 s | 67,3 % | **Falla** |

La única pareja que cumple la comprobación del grafo es RapidOcr. Al ser una
ejecución corta y una sola medición por variante, no permite afirmar una ganancia
estable del 21,7 %. No se han seguido acumulando repeticiones para convertir en
una mediana tiempos de variantes que incumplen la equivalencia.

El prototipo incluye también cambios de almacenamiento y sustitución de
declaraciones: retiene y redirige las relaciones existentes, en vez de destruirlas
y recrearlas, y acumula eliminaciones en conjuntos. Por ello, los porcentajes de
esta tabla **no aíslan la contribución de quitar el turno global**. Es necesario
comparar además la misma implementación nueva con visitas ordenadas; los controles
de corrección descritos al final proporcionan ese tercer modo, pero no constituyen
una campaña de tiempos repetidos y equilibrados.

### Nodos y relaciones preparados para inserción

| Programa | Nodos anteriores | Nodos concurrentes | Diferencia | Relaciones anteriores | Relaciones concurrentes | Diferencia |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RapidOcr | 5414 | 5414 | 0 | 11875 | 11875 | 0 |
| canal | 381628 | 381642 | +14 | 957121 | 957220 | +99 |
| Gephi | 635464 | 635467 | +3 | 1815169 | 1815374 | +205 |
| James | 1208884 | 1208910 | +26 | 3102764 | 3102846 | +82 |

Las diferencias netas esconden también eliminaciones o sustituciones: en canal
la comparación de propiedades detecta 19 nodos añadidos y 5 retirados; en Gephi,
9 añadidos y 6 retirados; en James, 31 añadidos y 5 retirados. Dos nodos con igual nombre pueden diferir en etiquetas
o propiedades, por lo que contar únicamente los totales no basta.

### Compilaciones y concurrencia

Los siguientes contadores son iguales en la referencia y el prototipo.
`Success=true` en todas las ejecuciones significa que terminaron, no que fueran
semánticamente equivalentes.

| Programa | Tareas | Ficheros compilados | Diagnósticos | Versión javac declarada por las tareas | Máximo de visitantes simultáneos |
| --- | ---: | ---: | ---: | --- | ---: |
| RapidOcr | 14 | 28 | 56 | 1.8 | 6 |
| canal | 37 | 573 | 0 | 1.8 | 7 |
| Gephi | 62 | 2754 | 1564 | 17 | 8 |
| James | 243 | 3672 | 9 | 21, 11,21 | 8 |

La versión de esta tabla es el objetivo recogido por el resumen del analizador;
la JVM que ejecuta el proceso es JDK 22.0.2 en ambos modos. La instrumentación de
concurrencia incrementa un contador al entrar en la acción del visitante y lo
decrementa en `finally`: no mide solamente tareas esperando en el executor.

### Tiempo total y memoria del cliente

| Programa | Proceso anterior, incluido Maven | Proceso concurrente, incluido Maven | Pico anterior (MiB) | Pico concurrente (MiB) |
| --- | ---: | ---: | ---: | ---: |
| RapidOcr | 17,644 s | 15,055 s | 483 | 497 |
| canal | 114,358 s | 95,150 s | 5499 | 5926 |
| Gephi | 358,337 s | 268,705 s | 9190 | 9437 |
| James | 959,012 s | 713,229 s | 17535 | 20465 |

La memoria es el pico aproximado de working set del proceso Java cliente,
muestreado cada segundo; no mide el servidor Neo4j ni suma el proceso Maven.
El prototipo no mejora este indicador: en James sube unos 2930 MiB, un 16,7 %.
Los controles de corrección de otros programas se realizaron durante el Maven
previo de James y terminaron antes de su intervalo de inserción; por ello los
tiempos de proceso completo son orientativos y no una comparación aislada del
coste de Maven. Los tiempos de inserción principales no se solaparon entre sí.

### Diferencias semánticas relevantes

| Relación | canal: diferencia | Gephi: diferencia |
| --- | ---: | ---: |
| `ITS_TYPE_IS` | +27 | +2 |
| `ENUM_DECLARES_ELEMENT` | +25 | 0 |
| `INHERITS_METHOD` | +24 | +55 |
| `GENERIC_TYPE_PARAM` | +7 | +3 |
| `TYPE_PARAM_EXTENDS` | +7 | +3 |
| `DECLARES_METHOD` | +7 | 0 |
| `USES_TYPE_DEC` | +2 | +3 |
| `MAY_REFER_TO` | 0 | +134 |
| `OVERRIDES` | 0 | +5 |

Por tanto, no son diferencias limitadas a identificadores internos o al orden de
inserción. Afectan a los resultados consultables de herencia y resolución de
llamadas. Los detalles de nodos están en `canal-node-diff.txt` y
`gephi-node-diff.txt`, y los histogramas por tipo de relación en los CSV del informe.

## Validación y decisión

Se han superado el build Maven y el build de IntelliJ, además de:

- 30 ejecuciones de la regresión del modo ordenado: orden de visitas/resultados,
  enlaces compartidos, tareas vacías, cesión temprana y terminación ante errores.
- 30 ejecuciones con una barrera dentro de los visitantes que exige la entrada
  simultánea de ocho trabajadores; detectaría la serialización del recorrido.
- 10 ejecuciones con ocho escritores creando 2400 nodos locales y 4800 arcos,
  mientras se sustituye una declaración compartida y se eliminan 800 arcos dos
  veces. Se comprueban los 2401 nodos y 4001 arcos finales, ambos extremos de cada
  relación, la autorrelación y las referencias tardías al nodo sustituido.
- 29 casos de firmas entre contextos javac, incluso tras liberar sus tablas de
  nombres, y las regresiones de parámetros de compilación de ProgQuery y Core.

Se conservó la regresión anterior y se añadió `ConcurrentGraphRegressionTest`;
no se sustituyó la comprobación del modo seguro por una prueba del prototipo.
Los logpoints temporales del agente se retiraron y no se modificaron los puntos
de ruptura del usuario.

### Control de la misma implementación con visitas ordenadas

Se repitió la inserción usando las opciones de javac capturadas del Maven de
referencia, en su orden original, sin volver a ejecutar Maven. Cada repetición
borró primero el grafo del programa y utilizó salidas de procesadores de
anotaciones nuevas para evitar incorporar fuentes generados de una prueba previa.
Estos controles comprueban la compatibilidad del refactor de almacenamiento y
estado privado cuando no se altera el orden semántico de visitas.

| Programa | Inserción del control ordenado | Nodos | Relaciones |
| --- | ---: | ---: | ---: |
| RapidOcr | 5,099 s | 5414 | 11875 |
| canal | 50,149 s | 381628 | 957121 |
| Gephi | 85,107 s | 635464 | 1815169 |
| James | 115,532 s | 1208884 | 3102764 |

Los controles conservan también tareas, ficheros, diagnósticos y versiones.
Las huellas completas de RapidOcr, canal y Gephi son iguales a las de referencia.
En James quedan cuatro propiedades `fullyQualifiedName` distintas, todas de
variables de tipo y con formato `...CallableKey@<hash>:T` o `:I`.
`TypeVariableKey.toString()` utiliza el `toString()` heredado de `CallableKey`;
la cadena imprime su hash y puede incorporar la identidad de símbolos de ámbitos
locales. Esas clases de claves no se modificaron en este experimento.

No se ha ocultado esta diferencia en la huella principal. Una comprobación
adicional estableció una correspondencia entre esas cuatro variables, conservando
nombre, etiquetas y todas las demás propiedades, y verificó sus 52 relaciones
incidentes contra las huellas originales. Con esa correspondencia, **todos los
demás nodos y todos los arcos coinciden**. La evidencia está en
`james-ordered-control-node-diff.txt` y `james-ordered-identity-audit.txt`.
Esto demuestra compatibilidad estructural del control; no igualdad literal de
esas cuatro cadenas. El programa mantiene 1208882 nodos y 3102763 relaciones
alcanzables desde su nodo de programa, igual que en la referencia.

En particular, los 101,930 s del James concurrente deben contrastarse también
con estos 115,532 s, no solo con los 311,724 s del motor anterior. La diferencia
observada entre ambas variantes del motor nuevo es un 11,8 %, y la variante
concurrente sigue teniendo un grafo distinto. Los otros cambios del prototipo
explican que haya mejoras observadas incluso conservando el turno de visitas;
harían falta medidas repetidas y separadas para cuantificar cada contribución.
Los controles no se utilizan para certificar una ganancia de rendimiento.

**Decisión:** no activar las visitas concurrentes de forma general ni aceptar sus
porcentajes como mejoras equivalentes. La separación de estado y los bloqueos
constituyen una base de trabajo, pero todavía falta la materialización determinista
de declaraciones. No está completada una solución que produzca exactamente el
mismo grafo en modo concurrente para los cuatro programas.
