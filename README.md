# Universal Cache (Kotlin / Coroutines / Flow)

### Goal: Organise cache and a request sharing with Kotlin in easy way.

Some functions:  
✔️ Share ongoing request(s) by parameters as key.  
✔️ Cache results of every request (in-memory / custom cache).  
✔️ Provide convinient function to get cached or request new data based on passed arguments.  

The real-life reason and inspiration is to reduce network workload in big applications 
with a lot of independent components which may use same API-endpoints at the nearly same time.

# Usage:

Basic case:  

```
private val cachedSource = CachedSource<String, Int>(
    source = { params -> api.getSomething(params) }
)

scope.launch {
    cachedSource.get(FromCache.CACHED_THEN_LOAD, maxAge = 5_000)
        .collect {
            // Use received values
        }
}
```
Main entities:  
**CachedSource** - is a wrapper for your data source (usually api call or db-request). Provides Flow of data or errors in several ways.  
**FromCache** - is an enum with request options like **FromCache.IF_HAVE** and other.  
**Requester** - is a wrapper that can share ongoing request(s). It's used in **CachedSource** but you can use it independently.  
**Cache** - is an interface for a how you store your cache. There is default in-memory implementation **MemoryCache**.  

# Setup:  

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

