# APK Link Gradle Plugin

A Gradle plugin for Android projects that converts APK files to JAR libraries and automatically adds them as compile only dependencies to your project.

## Installation 

Add the jitpack.io repository to `settings.gradle`

```kotlin
pluginManagement {
    includeBuild("patcher")

    repositories {
        ...
        maven(url = "https://jitpack.io")
    }
}
```
Apply the plugin in your `build.gradle.kts`

```kotlin
id("dev.marcelsoftware.apklink-gradle") version "1.0.0"
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

### Processing the APK and Using the Dependency

1. Run the processApk task to convert the APK to a JAR
2. After the task completes, trigger a Gradle sync in your IDE to make the new dependency available.
> **Important**: Whenever you change the APK source or need to update the JAR, you must run the `processApk` task again and resync your project.

## License
MIT License

## Credits
This plugin uses dex2jar for APK to JAR conversion.




