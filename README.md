# Kotlin Conversion of Scene Renderer v2

For a college class assignment, I converted the **Scene Renderer v2** program from Java to Kotlin as a way to practice using the Kotlin programming language.

The original Java program was created for my upcoming research paper, *"The Scalar Field Rendering Hypothesis: Reformulating the 3D Graphics Pipeline as 2D Spatial Arithmetic."*

The code files for the above can be found on my GitHub repository:

https://github.com/happysmaran/3D_2D_Pipeline_Test

I chose this program for the Kotlin port because it is complex enough to require some thought throughout the conversion process, while still not being an extremely large project like some of the others on my GitHub. It also benefited from originally being written in Java, meaning that the overall logic could remain largely identical. Additionally, Kotlin's interoperability with Java meant that I could continue using Java-based libraries such as `awt` and `Swing`.

## Conversion Process Overall

Overall, the process of porting the code from Java to Kotlin was quite smooth. Because Java and Kotlin share many similarities, things such as the program's logic and specific function calls to Java libraries did not pose much of an issue. But there were some hitches.

## The Bad (eeeee)

The main points of friction came from Kotlin's unique syntax and the differences between how certain features are implemented in Kotlin compared to Java.

I have already gone over these differences in more detail in the submitted document itself, but the main points are:

1. **Kotlin is to Java somewhat like British English is to American English.**  
   Many concepts are shared between the two, but there are enough differences in syntax and conventions to make them distinct.

2. **Many features are similar or identical to Java, while others are straight-up missing.**

3. **Kotlin does not have a `new` keyword.**  
   Unlike Java, objects can be instantiated directly by calling the class constructor:

   ```kotlin
   val example = Example()
   ```

4. **Kotlin classes behave more like classes in other languages such as C++, Python, and JavaScript.**  
   Java is somewhat of an outlier in how its class system behaves compared to these languages. Although Kotlin is not exactly like C/C++ either. It's in the middle.

5. **Kotlin generally follows a more simplified, Python-like style for certain things.**  
   For example, `for` loops are much more concise than their traditional Java equivalents.

6. **Kotlin supports using backticks to name variables, functions, and other identifiers.**  
   This means you can technically have something like:

   ```kotlin
   fun `whys this a thing`(variable: Int) Int {
       return variable + 5
   }
   ```

7. **Kotlin does not have an ending delimeter.** (like `;` in Java) This is not a nitpick, I just did not know where to write this.

It was not all bad though.

## The Good

There are several things I liked about Kotlin:

1. **Java interoperability**  
   Kotlin can use Java's standard libraries, including libraries such as **Swing**.

2. **Familiar logic**  
   Most logical systems work similarly to Java, making it relatively easy to understand how existing Java code translates into Kotlin.

3. **Less strict type declarations**  
   Kotlin allows you to use `val` and `var` without explicitly specifying a type in many cases:

   ```kotlin
   val example = "something idk"
   ```
   Kotlin will still consider the above as a string though, it is statically typed, like most languages.

## Conclusion

Overall, the conversion was a very good experience. The similarities between Java and Kotlin made it possible to preserve the original program's logic while adapting the code to Kotlin's syntax.

The main challenges were not related to the rendering system or the program's underlying logic, but rather to getting accustomed to Kotlin's unique syntax and figuring out which Java conventions did not carry over directly.

Very good.