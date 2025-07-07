# RPG to Java
Learning java as an rpgle developer


## Notes

Java was intended to create programs as a collection of objects interacting with each other through well defined interfaces.

Java was intended to keep the OOP independent of the hardware. That means run everywhere which is something needed for multiple OS where the sofware is dependent on the hardware. 

This is not a problem on the IBM I since everything compiled is independent from the specific arch.

The Java compiler generates bytecode, similar to python.

Parts requiring large amounts of compute power can be written in native machine code as RPG.

There are only three groups of primitive data types, namely, numeric types, character types, and Boolean types.

No alloc, malloc and free. `new` allocates memory for objects and when the objects has no more references, it is deleted. 

> A reference reffers to an allocated space.

Methods are loaded into the stack at execution and locar variables are liberated after finishing. Grabage collection is mostly for the heap data, similar to the `free` operation in **C**. In Java, this means that if a variable in the heap is only refenced by the executing method, it gets freed after execution finishes. Which is nice.

## Object oriented programming

No `structures` or `unions`, only `classes`, which makes sense.

Objects in object oriented programming langauges like java are made possible thanks to the dynamic nature of the heap. The stack stores a pointer to the object in the heap.

Functionla languages like `Lisp` or `Javascript` take this dynamic nature of the stack and leverage upon it to provide their `higher abstractions` natures. Python also uses the heap to alloctes its objects.

In Java, classes and methods are stored in the meta space or class data storage of the JVM. Instances of the classes are created in the heap and storage for method's local variables are alloated in the frame stack.

The class loader subsystem is responsible for: **loading**, **linking** and **initialization**

TODO: Ask about JAR files and CLASPATHS

`Static` means that the method or variable is part of the class bytecode stored at the **metadata space** and not part of any specific class instance where object are stored in the heap.

`Non Static` methods require an instance and operate on the instance context data wich is usually in the heap.

`javac` generates a compiled bytecode .class file. If this class references others `.class` files, they use the `classpath`, default is current directory.

Doing `java HeapStackExample` runs the `main` method of the `HeapStackExample` class in the `HeapStackExample.class` file.
The **JVM** loads the `.class` file into memory, creates the `Class` object `HeapStackExample` in the **metadata space** and calls the `main` method.

Seems like the **JVM** works purely on the concept of Classes.

This idea of class file is the base of the java modular object oriented ecosystem.

`java -cp` is used to explicity define the `classpath`. If not set the system uses the current dir or the `CLASSPATH` environment variable (if set).

A `Jar` file is a compressed file of `.class` files, images, configurations, and metadata.
```js
jar cvf myapp.jar HeapStackExample.class // Create myapp.jar with verbose option
```

A `Jar` file can also be included in the `classpath` to provide classes.
```js
java -cp ./myapp.jar HeapStackExample
java -cp ./myapp.jar:./lib.jar HeapStackExample // Multiple jar separated by :
```

To run the `jar` file it nees a `MANIFEST.MF` with a `Main-Class` similar to a **PEP**

`MANIFEST.MF` Example
```js
Main-Class: HeapStackExample
Class-Path: lib.jar otherlib.jar
```
Call it.
```js
java -jar myapp.jar
```

There are not multiple inheritance, **Interfaces** give a set of methods and constants the one or more classes will implement.

No operation overloading, create a class with it's corresponding variables and methods.

No more pointers since there are no more structures, arrays and strings, they are all objects. References are actually pointers under the hood with with safety guards.

Java satisfies the needs of client-server and distributed sofware where **objects** can be passed around networks.

An object's behavior is defined by its methods and the state by its variables
A class defines how an object is initialized: data(state) and methods (behavior)

Access to the object data or state is defined by the access methods which defines how to interact with the object. This allows more control over the class.

Since java implements singe inheritance, simliar classes can inheritance from the same super class with some tweaks, to keep them consistent and avoid multiple inheritance, **interfaces** are implemented. A class can implement multiple interfaces.

An interface defines methods, it does not implement them, that is leave to the class.

**Access control** defines how a class methods can be accessed by other classes: **private**, **proctected**, **public** and **friendly** (accesible within the same package)

A **Package** is just a colletion of classes and interfaces. They are usually stored on different directories in the file system.

**Abstract** classes lets you define like a blueprint to be inherited by other classes and implement its **abstract methods**

Java is created for the distributed world. Tha is why it compiles to an instruction set (similar to the **TIMI**) for a virtual machine (**JVM**)

Java data types: byte, short, int, long, float, double and char. The Java compiler is written in Java and the run-time system in ANSI C.

Quote of the day: *Programmers using "traditional" software development tools have become resigned to the artificial **edit-compile-link-load-throw-the-application-off-the-cliff-let-it-crash-and-start-all-over-again** style of current development practice.* Java is intended to catch bugs before the cliff.

Classes are loades as required.

In C++, adding methods to the super class requires all the inherited classes to be recompiled, this is not necessary in Java, as it automatically loads the new methods and variables into the inherited classes. The symbolic resolution of names is resolved as class are being linked and the layout of objects in memory is deffered at run time. After this, the Java interpreter goes at full speed. Updated classes with new methods and variables can be linked without affecting existing code. It just have to perform a name lookup. The run time object representation has the metadata to do this, it is not just a pointer.

**Thread safe** means that the application can be executed by multiple concurrenct threads of execution. **synchronized** is the keyword to declare methods that can't run concurrently on the same object. The running method has to finish por the lock to free up. This means that the object state (variables) can be changed by only one method at the time.

Java has a **JIT** compiler to translate bytecodes on the fly.

OOP operates on Objects as their data, not directly on data structures like procedural languages. This is a really important idea. The astraction of the data allows the abstraction of the operations made on the data.

# Dev Java

The **JRE** is no longer distributed by the **OpenJDK** or **Oracle**

J2EE, Java EE or Jakarta reffer to the Java Enterprise Edition. The Java EE (enterprise edition) differs from the JDK

At this point i switched to Ubuntu since PUB400 is at JDK 8.

Install Apache Commons Lang: `sudo apt-get install libcommons-lang3-java`

Run it like `java -cp /usr/share/java/commons-lang3-3.11.jar ReferenceNonJDKClass.java`

## Hello World

Deploy source files

Compile the source code
```js
javac HelloWorld.java
```

Call is

```js
Java HelloWorld
```

HeapStackExample
```js
javac HeapStackExample.java         // Compile class file 
java -cp ./ HeapStackExample.class  // Execute with curdir as classpath
```

## Java and RPG


## Java and IBM I

- Database access
- work with IFS Stream files
- Call programs (PCML)
- Execute non-interactive CL commands
- Work with data queues
- Work with out queues and spool files
- Access job and job logs.
- Use user spaces and data areas.

A new set of instructions were created for the **TIMI** to implement the **JVM**. The actual Java and JDK were implemented above. This was the original but had some compatibility problems.

Now, IBM Technology for Java is implemented on **PASE** and optimized for the PowerPC

Call java program
```js
RUNJVA
ADDENVVAR // Add env var
```

Java location `/QOpenSys/QIBM/ProdData/JavaVM/...`
Java home `t /QOpenSys/QIBM/ProdData/JavaVM/jdk.../64bit`

Should you use java for as your modernization language? Well, it depends: Is it the correct tool for the job? Do you need to manage complex components and focus on reusability, scalability, and flexibility? Maybe you should use an object-oriented language then. A paradigm is a way of thinking.

**Is Java hard to learn?** Well, if you are used to good ILE practices were you create small modules with one responsability then moving to OOP should be easy. Don't get stuck in framwork jungle, focuns on one at a time and learn the pattern (unerlying technique)

In summary
- Learning how to think in the OOP paradigm
- Learning the Java grammar and syntax
- Becoming familiar with the tools
- Understanding the frameworks for web and database access

## Java Debug

```js
// Compile the class with debug info
javac -g MyClass.java
// Find job from acs
WRKOBJLCK OBJ(QSYS/ROBKRAUDY) OBJTYPE(*USRPRF)
// Start srvjob
STRSRVJOB JOB(076853/QUSER/QZDASOINIT)
// You can debug it normally
STRDBG CLASS(myClass)
```


| Parameter style | Database connection |  File that is required in the classpath |
|----------|----------|----------|
| JAVA    | JDBC | db2_classes.jar |
| JAVA    | SQLJ | db2_classes.jar, translator.zip, and runtime.zip |
| DB2GENERAL   | JDBC | db2_classes.jar and db2routines_classes.jar |
| DB2GENERAL   | SQLJ | db2_classes.jar, db2routines_classes.jar, translator.zip, and runtime.zip |

# Java and Stored Procedures

It is now possible to have one stored procedure that returns data in a result set that all languages can read:  RPG, Java, etc.

JDBC connection
```js
Connection con = DriverManager.getConnection("jdbc:default:connection");
Connection con = getConnection();
```

Java external procedure
```js
CREATE PROCEDURE SPWITHTWORESULTSETS (IN INTEGER, OUT VARCHAR(50))
EXTERNAL NAME MyClass!myJavaStoredProcedure
PARAMETER STYLE JAVA
RESULT SETS 2
LANGUAGE JAVA
```

This db2 Java procedure means that it could be called from an IWS end-point or maybe an RPG program.

```js
import java.sql.*;
public class SomeStoredProcs {
  public static void myJavaStoredProcedure(
    int myInputInteger,
    String[] myOutputString,
    ResultSet[] myFirstResultSet, 
    ResultSet[] mySecondResultSet) {
    // SP implementation
    ...
    myFirstResultSet[0] = stmt1.executeQuery(qry1); 
    ...
    mySecondResultSet[0] = stmt2.executeQuery(qry2); 
  }
}
```


```js
CREATE OR REPLACE PROCEDURE ROBKRAUDY2.SPJAVA ()
EXTERNAL NAME 'SpExample.returnTwoResultSets'
PARAMETER STYLE DB2GENERAL
RESULT SETS 2
LANGUAGE JAVA;

// Add ENVVAR to the job
ADDENVVAR ENVVAR(CLASSPATH) VALUE('/home/ROBKRAUDY/builds/rpg_to_java/source:/QIBM/ProdData/Java400/db2java.zip') REPLACE(*YES)

// Some useful commands
go CMDENVVAR
go CMDQSH
go CMDLNK

// Compilation
javac -cp /QIBM/ProdData/OS400/JT400/lib/jt400.jar SpExample.java
```

JDBC example

```js
CallableStatement cs = conn.prepareCall("CALL ROBKRAUDY2.SPJAVA()");
cs.execute();
ResultSet rs = cs.getResultSet(); // First result set
while (rs.next()) {
    // Process first result set
}
if (cs.getMoreResults()) {
    rs = cs.getResultSet(); // Second result set
    while (rs.next()) {
        // Process second result set
    }
}

```

SLQJ requires pre-compilation with `sqlj DB2SQLJCusInCity2.sqlj`. This creates a `.ser` file, 2 `.class` files and a `.class` file for each iterator.

```js

// Compilation
javac DB2SQLJCusInCity2.java
```

```js
// Location of the classes
ls /QIBM/ProdData/OS400/Java400/ext
```

The creted functions should be at `/QIBM/UserData/OS400/SQLLib/Function` which may be a problem since we don't have access. All Java stored procedure classes must reside in the /QIBM/UserData/OS400/SQLLib/Function directory.

Created procedures are stored in SYSROUTINES and SYSPARMS tables.

`CRTJVAPGM` can be used to created optimized java programs with native IBM i instructions.
```js
CRTJVAPGM CLSF(Db2CusInCity.class) OPTIMIZE(40)
```

Create Jar
```js
jar -cvf SpExample.jar SpExample*.class

// Install at /QIBM/UserData/OS400/SQLLib/Function
// jar-url, jar-id           
CALL SQLJ.INSTALL_JAR('file:/home/ROBKRAUDY/builds/rpg_to_java/source/SpExample.jar', 'MySpExample_jar', 0);
// Remove from /QIBM/UserData/OS400/SQLLib/Function
CALL SQLJ.REMOVE_JAR('MySpExample_jar', 0)

CALL SQLJ.REPLACE_JAR('file:/home/ROBKRAUDY/builds/rpg_to_java/source/SpExample.jar', 'MySpExample_jar')

CALL SQLJ.UPDATEJARINFO('MySpExample_jar', ‘mypackage.myclass’, 'file:/home/ROBKRAUDY/builds/rpg_to_java/source/SpExample.jar')

CALL SQLJ.RECOVERJAR('MySpExample_jar')
```

The install command creates `/QIBM/UserData/OS400/SQLLib/Function/jar/schema/MySpExample_jar.jar` where `schema` is the user profile.


Java SP example
```js
CREATE PROCEDURE DB2SQLJCUSINCITY(IN S CHAR(20), OUT I INTEGER)
LANGUAGE JAVA 
PARAMETER STYLE DB2GENERAL 
NOT FENCED
EXTERNAL NAME 'DB2SQLJCUSINCITY.DB2SQLJCUSINCITY'; 
```

# Web Serving

The web server is the http server that handles the request and serves static data back to the browser. If dynamic content is needed, the the web server invokes the aplication server for that (could be through a CGI methodology)

The Apache HTTP server is the foundation for the IBM HTTP Server (**IHS**). On IBM I, this is the only opiton for web serving.

The IBM Java Toolbox allows the application server to access ILE Objects and the DB. The application server acts as a middleware between back-end systems and the clients.

> Some application server languages: Java, PHP, and Ruby.


## WebSphere Application Server (WAS)

A Java based application server that can scale from the most simple to the most complex environments.

Management GUI: http://hostname:2001/HTTPAdmin

## Integrated Web Application Server (IAS) 

Java application server intended to be a minimal footprint.
Based on Lightweight Infrastructure (LWI) and Open Services Gateway initiative (OSGi) technology.

IAS is itended to run and develop simple web applications which can be migrated to a full WebSphere application server container.

Management GUI: http://hostname:2001/HTTPAdmin

## Tomcat

Open source web server and servlet container. It is a pure Java application and can be quickly and easily started on IBM I

## PHP: Zend Technologies

PHP is a Scripting language and can be more easly to learn for an RPG devoloper than the OOP nature of Java.

## Ruby

Another alternative, Ruby is an OOP language.

# JTOpen

```js
mvn archetype:generate -DgroupId=com.example -DartifactId=AS400JDBCExample -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
```

# IBM Toolbox for Java

The Jar should be under `/QIBM/ProdData/OS400/jt400/lib/`

Compile it like this 

```js
// Compile
javac -cp /QIBM/ProdData/OS400/jt400/lib/jt400.jar CmdCall.java
// Run it!. Note the ":." at the end to include the cur dir
java -cp /QIBM/ProdData/OS400/jt400/lib/jt400.jar:. CmdCall
```

Reading json [TestReadJson](source/json/TestReadJson.java)
I'm using the [JSON-java](https://github.com/stleary/JSON-java/tree/master) library 
And [Apache PDFBox](https://pdfbox.apache.org/download.html) to generate the pdf. It starts kinda slow the first time when it need to build the fonts.
```js
javac -cp /QIBM/ProdData/OS400/jt400/lib/jt400.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/json-20231013.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/pdfbox-app-3.0.5.jar TestReadJson.java

java -cp .:/QIBM/ProdData/OS400/jt400/lib/jt400.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/json-20231013.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/pdfbox-app-3.0.5.jar TestReadJson
```

Using only [Jackson JSON parser](https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core/2.2.3)

```js
run from /source

javac -cp /QIBM/ProdData/OS400/jt400/lib/jt400.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/json-20231013.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/jackson-core-2.19.1.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/jackson-databind-2.19.1.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/jackson-annotations-2.19.1.jar json2/PureJacksonParser.java 

java -cp .:/QIBM/ProdData/OS400/jt400/lib/jt400.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/json-20231013.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/jackson-core-2.19.1.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/jackson-databind-2.19.1.jar:/home/ROBKRAUDY/builds/rpg_to_java/source/jackson-annotations-2.19.1.jar json2.PureJacksonParser
```

Using **Apache PDFBox** can be a little awkard since you have to manually specify the format in code which is something i was already running away from (you know, print files on the O page of RPG).

Create logging table
```sql
CREATE TABLE ROBKRAUDY2.NOTIF_LOG (
  LOG_TIMESTAMP TIMESTAMP NOT NULL,
  LOG_ID INTEGER GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1, CYCLE),
  LOG_MESSAGE VARCHAR(100) NOT NULL,
  PRIMARY KEY (LOG_TIMESTAMP, LOG_ID)
)
```

##

##

##


