# RPG to Java
Learning java as an rpgle developer


## Notes

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

## xx

## xx

## xx