# Universal Cache (Kotlin / Coroutines / Flow)

### Goal: Make cache and a request sharing with Kotlin in a simple way.

Some functions:  
✔️ Share ongoing request(s) by parameters as key.  
✔️ Cache results of every request (in-memory / custom cache).  
✔️ Provide a convenient function to get cached or request new data based on passed arguments.  

The real-life reason and inspiration is to reduce network workload in big applications 
with a lot of independent components that may use the same API-endpoints at the nearly same time.

# Usage:

Basic case:  

```kotlin
val cachedSource = CachedSource<String, Int>(
    source = { params -> api.getSomething(params) }
)

scope.launch {
    cachedSource.get("some request parameter", FromCache.CACHED_THEN_LOAD, maxAge = 5_000)
        .catch { 
            // Handle error if needed
        }
        .collect {
            // Use received value(s)
        }
}
```

### CachedSource  
Wrapper for your data source (usually API call or DB-request). Provides Flow of data or errors in several ways.  
Construct it with your data source and optional parameters:  
```
val cachedSource = CachedSource<P, T>(
    source = { api.getSomething() },
    cache = ...,
    timeProvider = ...,
    dispatcher = ...,
)
```
Use `.get()` or `.getRaw()` function with a variety of options to get a Flow of cached / loaded data:
```
cachedSource
    .get(
        params = ...,
        fromCache = ...,
        shareOngoingRequest = ...,
        maxAge = ...,
        additionalKey = ...,
    )
    .collect {
        ...
    }
```
Use `.updates` and `.errors` (SharedFlow) to observe all updates and errors without `.get()`:
```
cachedSource.updates
    .collect { (params, result) ->
        ...
    }
```

### FromCache
Enum with request options:
```
FromCache.NEVER
FromCache.IF_FAILED
FromCache.IF_HAVE
FromCache.ONLY
FromCache.CACHED_THEN_LOAD
```

### Requester  
Wrapper that can share ongoing request(s) if there is any in progress with the given params.  
It's used in **CachedSource** but you can use it independently.  
```
val requester = Requester(source)
...
requester.requestShared("some request params")
    .collect {
        ...
    }
```

### Cache  
Interface for a how you store your cache. There is default in-memory implementation **MemoryCache**.  
Main functions:
```
get(params: P, additionalKey: Any?): CachedData<T>?

put(value: T, params: P, additionalKey: Any?, time: Long)
```

# Setup:  

### Kotlin Multiplatform. Versions after 1.1.*  

[![Maven Central](https://img.shields.io/maven-central/v/io.github.andrew0000/universal-cache)](https://mvnrepository.com/artifact/io.github.andrew0000/universal-cache)  

Add `implementation 'io.github.andrew0000:universal-cache:$latest_version'` to the module-level `build.gradle` 

### Old. Android only. Versions prior to 1.1.*  

[![](https://jitpack.io/v/Andrew0000/Universal-Cache.svg)](https://jitpack.io/#Andrew0000/Universal-Cache)

1. Add `maven { url 'https://jitpack.io' }` to the `allprojects` or `dependencyResolutionManagement` section in top-level `build.gradle` or `settings.gradle`.  
For example (`settings.gradle`):
```
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
```
2. Add `implementation 'com.github.Andrew0000:Universal-Cache:$latest_version'` to the module-level `build.gradle`  

