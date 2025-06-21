# APK Link Gradle Plugin

A Gradle plugin for Android projects that converts APK files to JAR libraries and automatically adds them as compile only dependencies to your project.


[![](https://jitpack.io/v/dev.marcelsoftware/ApkLink-Gradle.svg)](https://jitpack.io/#dev.marcelsoftware/ApkLink-Gradle)
## Memory Requirements

Due to the resource-intensive nature of APK processing, it's recommended to allocate at least 4GB of memory to Gradle:

Add the following to your `gradle.properties` file:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

## Installation 

Add the jitpack.io repository to `settings.gradle`

```kotlin
pluginManagement {

    repositories {
        ...
        maven(url = "https://jitpack.io")
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.toString() == "dev.marcelsoftware.apklink-gradle") {
                useModule("dev.marcelsoftware:ApkLink-Gradle:${requested.version}")
            }
        }
    }
}
```

Apply the plugin in your `build.gradle.kts`

```kotlin
id("dev.marcelsoftware.apklink-gradle") version "1.0.2"
```

## Usage

Configure the plugin in your `build.gradle.kts`

```kotlin
apklink {
    // Configuration goes here
}
```

## Configuration

Using a local apk file

```kotlin
apklink {
    apkFile = file("path/to/your/app.apk")
    // Optional: specify a custom output location for the JAR
    outputFile = file("${buildDir}/libs/app.jar")
}
```

Downloading from APKPure

```kotlin
apklink {
    apkpure {
        packageName = "com.example.app"
        // Optional: specify a specific version
        version = "1.2.3"
    }
}
```

Downloading from a URL
```kotlin
apklink {
    url {
        url = "https://example.com/download/app.apk"
        // Optional: specify a custom filename
        fileName = "app.apk"
    }
}
```

## License
MIT License

## Credits
This plugin uses dex2jar for APK to JAR conversion.




