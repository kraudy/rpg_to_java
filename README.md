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

## xx

## xx

## xx