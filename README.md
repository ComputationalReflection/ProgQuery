# ProgQuery
[ProgQuery](https://www.reflection.uniovi.es/bigcode/download/2020/ieee-access/) is a system to extract syntactic and semantic information from source code programs and store it in a Neo4j graph database for posterior analysis. 
For each program or compilation unit, ProgQuery extracts the following graph structures:
- Abstract Syntax Tree
- Call Graph
- Type Graph
- Control Flow Graph
- Program Dependency Graph
- Class Dependency Graph
- Package Graph
# Build Instructions

## Step 1: Install Maven
ProgQuery will need Maven in order to solve the binaries dependencies needed to make it work. Unless you have Maven already installed, you have to do so. You can download it [here](https://maven.apache.org/download.cgi) and follow its installation process in the [following link](https://maven.apache.org/install.html).

You also need to set M2_HOME as environment variable in your system since it is required by Static Code Analysis tool. Otherwise, an exception will be raised.

## Step 2: Clone the ProgQuery repository and generate the .jar file
Next step consists on cloning the repository of ProgQuery project and generating .jar . Once you have cloned the repository in your computer, move to the cloned local repository you have just created and execute the following command:
```shell
mvn clean compile package
````
After its execution, this command will have generated the .jar file called _ProgQuery-3.0.0.jar_ inside the maven default output folder _target_.

By means of this process for getting the .jar, the users are allowed to extract information from source code programs and store it in a Neo4j graph database. 

## Step 3: Using ProgQuery CLI to run the analysis
With the generated .jar, the user can use different commands provided by the tool:

### Command for executing the analysis using local Neo4j graph database
```shell
java -jar ProgQuery-3.0.0.jar -user="<user_id>" -program="<program_id>" -neo4j_mode="local" -neo4j_database_path="<database_path>" -javac_options="<javac_options>"
````
* `-user`: (Mandatory param) It specifies User id.
* `-program`: (Mandatory param) It specifies Program id.
* `-neo4j_mode`: (Mandatory param) It specifies the Neo4j mode: local or server.
* `-neo4j_database_path`: (Mandatory param, when Neo4j mode local is used) It specifies the path to the directory where the database will be stored.
* `-javac_options`: (Mandatory param) It specifies the options used to run the Java compiler p.e. `-d .\Example\target\classes -classpath .\Example\target\classes; -sourcepath .\Example\src\main\java; -g -nowarn -target 8 -source 8`  

- After the compilation process a single overlapped graph containing these 7 structures is included in a Neo4j graph database.

### Help command
```shell
java -jar ProgQuery-3.0.0.jar -help
````
This command displays all the information about the parameters that can be used with .jar file.

## References<a name="references"></a>
<a id="1">[1]</a>
Oscar Rodriguez-Prieto, Alan Mycroft, [Francisco Ortin](https://reflection.uniovi.es/ortin/index.html).
[An efficient and scalable platform for Java source code analysis using overlaid graph representations](https://doi.org/10.1109/ACCESS.2020.2987631).
IEEE Access, volume 8, pp. 72239-72260.
December 2020.

<a id="2">[2]</a>
Oscar Rodriguez-Prieto.
[Big Code infrastructure for building tools to improve software development](https://reflection.uniovi.es/ortin/theses/oscar.pdf).
Ph.D. Thesis at the Computer Science Department of the [University of Oviedo](https://www.uniovi.es).
Ph.D. Supervisor, Dr. [Francisco Ortin](https://reflection.uniovi.es/ortin/index.html).
June 2020.

## More information
* https://www.reflection.uniovi.es