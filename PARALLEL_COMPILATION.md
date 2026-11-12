# Parallel compilation

`-max_concurrent_compilations=<number>` (short form `-mcc=<number>`) controls the
maximum number of simultaneous compilation tasks. The default is **8**. Use
`-mcc=1` for sequential execution. Values must be positive integers; the worker
count is also limited by the number of tasks. Embedded/local mode stays sequential.

The option is independent of `-max_concurrent_write_transactions` / `-mcwt`.
Core forwards it to ProgQuery. The API exposes the same setting as
`codeanalysis.core.max_concurrent_compilations`, defaulting to `8` when absent.
Existing server constructors retain their signatures and default to eight;
new overloads accept the compiler limit after the write concurrency limit.

## Shared state and ordering

Each task has its own compiler, javac context, file manager, diagnostics and
`JavacInfo`. Graph visits retain input module order and a single writer, protecting
the existing declaration caches, AST/CFG/PDG, package manager and pending insertion
store. Compilers can work concurrently outside these callbacks. The monitor only
protects ownership handoff and failure notification; no monitor is held while a
compiler or graph visitor executes.

The graph slot is released after the last requested source is visited, allowing
bytecode generation to overlap the next module's visits. Empty tasks release their
slot on completion. Final analyses and database insertion run on the coordinator
after all workers finish. Worker thread-local bindings are removed on exit, and
failures wake waiting workers and preserve the original error. Teardown waits for
every worker to stop before closing file managers or clearing shared state.

## Dependencies and annotation processors

Dependencies must already be compiled. Core's `-mvn` workflow runs a complete
`mvn clean compile` before starting analysis; the parallel tasks read those existing
dependency classes. This is not a dependency scheduler for tasks that need each
other's newly generated outputs.

Every parallel task writes classes to its own temporary output directory, leaving
Maven's classpath outputs unchanged. Existing non-class resources are copied into
the private output because annotation processors may read `CLASS_OUTPUT` resources.
Generated-source directories must be independent between concurrent tasks.
File managers are closed and temporary class outputs deleted after all tasks stop.

## Verification

Executable regression tests under `src/test/java` cover structural method
signatures, compiler limit parsing/defaults and eight-worker ordering, shared
bindings, empty tasks, early ownership release and failure shutdown. Build with
`mvn test-compile` and run their main classes with the same javac exports as ProgQuery.
Core has an additional executable compiler-option parsing regression.

On JDK 22.0.2 with an i7-7820X (16 logical CPUs) and eight Neo4j write workers,
the measured insertion times, excluding Maven and deletion, were:

| Project | Sequential | Eight compilation workers | Reduction |
| --- | ---: | ---: | ---: |
| RapidOcr | 4544 ms | 4225 ms | 7.0% |
| Canal | 55828 ms | 47986 ms | 14.0% |
| Gephi | 106378 ms | 95384 ms | 10.3% |
| James | 345082 ms | 280765 ms | 18.6% |

The first three rows are medians of three runs per mode with a 10 GiB client heap;
James is one run per mode with a 48 GiB heap. Counters matched in both modes.
RapidOcr timings overlap, so its small gain should not be generalized. Eight
workers is the default, not a claim that it is optimal for every workload.

## Aspectos técnicos

Los cambios permiten solapar las compilaciones de los módulos conservando el
orden de las modificaciones del grafo. La construcción compartida del AST/CFG/PDG
mantiene un único escritor; no se han introducido bloqueos independientes por nodo
o por caché.

- **Configuración y entrada (`ProgQueryParameters`, Core y API).** Se añadió
  `max_concurrent_compilations`, con alias `mcc`, valor predeterminado 8 y validación
  de entero positivo. Los nuevos constructores de `ProgQueryClient` y `ProgQuery`
  propagan el límite por instancia; los anteriores conservan su firma y delegan
  con el valor predeterminado. El modo local utiliza un trabajador.

- **Coordinación (`ProgQuery` y `ParallelCompilation`).** El bucle secuencial se
  sustituye, cuando corresponde, por un pool fijo limitado al número de tareas.
  Cada compilación devuelve un `Future`; los resultados se recogen en el orden
  original de módulos. Los análisis globales y la escritura en Neo4j comienzan
  después de terminar todos los trabajadores. Con un trabajador se mantiene el
  camino secuencial.

- **Estado privado y compartido.** `CompilationScheduler` crea por tarea su
  compilador, contexto de javac, file manager, diagnósticos y `JavacInfo`.
  `ParallelCompilation` enlaza en los `ThreadLocal` de cada trabajador las mismas
  cachés de declaraciones, `PackageManager`, `InfoToInsert` y `DatabaseFacade`
  del coordinador. El AST y `PDGProcessing` siguen perteneciendo al scheduler
  compartido. Al terminar se eliminan los enlaces del trabajador y se recupera
  en el coordinador el último `JavacInfo` disponible.

- **Acceso al grafo (`GetStructuresAfterAnalyze`).** Las visitas de los eventos
  `ANALYZE` correspondientes a los fuentes solicitados pasan por `visitGraph`.
  `graphOwner` identifica el módulo con turno; el array `completed` permite
  avanzar sobre tareas terminadas, incluidas las vacías. `synchronized`, `wait`
  y `notifyAll` protegen la espera y el relevo, pero el visitante se ejecuta fuera
  del monitor. El módulo conserva el turno entre visitas y lo cede al completar
  sus fuentes, permitiendo que su generación de bytecode se solape con el siguiente.

- **Salidas y recursos (`CompilationScheduler`).** Las tareas paralelas escriben
  sus clases en un directorio temporal propio mediante `-d`. Se copian los recursos
  existentes de `CLASS_OUTPUT` que necesitan algunos procesadores de anotaciones,
  conservando las clases de Maven usadas como dependencias. Los file managers y
  directorios temporales se registran en `ConcurrentLinkedQueue`; su cierre y
  limpieza se realizan cuando ya no quedan trabajadores activos.

- **Fallos y liberación.** El primer error se conserva y despierta a los
  trabajadores que esperan turno. El executor se interrumpe y se espera su
  terminación antes de liberar el estado compartido. En `ProgQuery`, un `finally`
  garantiza el reinicio de las cachés aunque falle el cierre de recursos.
  Las regresiones cubren orden de visitas y resultados, exclusión de escritores,
  tareas vacías, cesión temprana del turno, fallos y configuración del paralelismo.

## Experimento posterior: visitantes concurrentes (12-11-2026)

La sección anterior documenta el cambio aceptado previamente. El refactor
experimental posterior separa el estado privado de las tareas y protege las
operaciones compartidas por caché, elemento y extremos de relación. Las visitas
concurrentes quedan desactivadas por defecto: las pruebas de canal, Gephi y James
no conservan los mismos grafos. `-mcc=8` mantiene el comportamiento ordenado;
la propiedad JVM `-Dprogquery.concurrentVisitors=true` permite reproducir el
prototipo experimental.

El [informe del experimento](CONCURRENT_VISITORS.md) detalla los cambios de código,
los bloqueos, los tiempos y las diferencias de nodos y relaciones. Sus porcentajes
observados no deben interpretarse como mejoras aceptadas cuando cambia el grafo.
