# ClassTokenReplacer
Gradle plugin to replace string tokens in class files during compile time.

ClassTokenReplacer allows you to replace string placeholders in your code with actual values during the building process.
This can be used to replace tokens like ``${version}`` with the actual version of the project.

## Releases
### Gradle
To use ClassTokenReplacer in your Gradle project you can follow the instructions on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/net.raphimc.class-token-replacer).

## Usage
The plugin extends all source sets with a new extension called ``classTokenReplacer``. This allows you to specify replacements for your source sets:
```groovy
sourceSets {
    main {
        classTokenReplacer {
            property("\${version}", project.version)
        }
    }
}
```

That's it! Now all compiled class files in the main source set will be scanned for the token ``${version}`` and have it replaced with the value specified in the ``property`` method.
Be careful to choose placeholders in a way where don't accidentally match some other strings in your code.

## Contact
If you encounter any issues, please report them on the
[issue tracker](https://github.com/RaphiMC/ClassTokenReplacer/issues).  
If you just want to talk or need help implementing ClassTokenReplacer feel free to join my
[Discord](https://discord.gg/dCzT9XHEWu).
