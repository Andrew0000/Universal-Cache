# Universal-Cache (Kotlin / Coroutines / Flow)

The goal: Organise cache and a request sharing in Kotlin applications in easy way.

Some functions:
✔️ Share ongoing request(s) by parameters as key.
✔️ Cache results of every request (in-memory / custom cache).
✔️ Provide convinient function to get cached or request new data based on passed arguments.

The real-life reason and inspiration is to reduce network workload in big applications 
with a lot of independent components which may use same API-endpoints at the nearly same time.

// Artifact publication - TODO

Tupical usage:

```
private val cachedSource = CachedSource<String, Int>(
    source = { params -> api.getSomething(params) }
)

lifecycleScope.launch {
    cachedSource.get(FromCache.CACHED_THEN_LOAD, maxAge = 5_000)
        .collect {
            // Use received values
        }
}
```
