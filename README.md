# Universal Cache (Kotlin / Coroutines / Flow)

### Goal: Organise cache and a request sharing with Kotlin in easy way.

Some functions:  
✔️ Share ongoing request(s) by parameters as key.  
✔️ Cache results of every request (in-memory / custom cache).  
✔️ Provide convinient function to get cached or request new data based on passed arguments.  

The real-life reason and inspiration is to reduce network workload in big applications 
with a lot of independent components which may use same API-endpoints at the nearly same time.

// Artifact publication - TODO

[![](https://jitpack.io/v/Andrew0000/Universal-Cache.svg)](https://jitpack.io/#Andrew0000/Universal-Cache)

Typical usage:

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
