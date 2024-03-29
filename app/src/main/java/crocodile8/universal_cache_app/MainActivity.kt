package crocodile8.universal_cache_app

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import crocodile8.universal_cache.CachedSourceNoParams
import crocodile8.universal_cache.FromCache
import crocodile8.universal_cache.get
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * It's a sample application. Library code is in another module.
 */
@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private val taskInvocationCnt = AtomicInteger()
    private val longRunningTask = {
        Thread.sleep(1000)
        Log.i("test_", "test_ longRunningTask")
        taskInvocationCnt.incrementAndGet()
    }
    private val source = CachedSourceNoParams(longRunningTask)

    private lateinit var textView1: TextView
    private lateinit var textView2: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView1 = findViewById(R.id.textView_1)
        textView2 = findViewById(R.id.textView_2)

        launchAndObserveParallelRequests()
        observeUpdates()
    }

    private fun launchAndObserveParallelRequests() {
        lifecycleScope.launch {
            source.get(FromCache.IF_HAVE)
                .collect {
                    Log.i("test_", "test_ collect 1: $it")
                    textView1.text = "Ready, invocation count: $it"
                }
            source.get(FromCache.IF_HAVE)
                .collect {
                    Log.i("test_", "test_ collect 2: $it")
                    textView1.text = "Ready, invocation count: $it"
                }
            source.get(FromCache.IF_HAVE)
                .collect {
                    Log.i("test_", "test_ collect 3: $it")
                    textView1.text = "Ready, invocation count: $it"
                }
        }
    }

    private fun observeUpdates() {
        lifecycleScope.launch {
            source.updates.collect {
                Log.i("test_", "test_ collect updates: $it")
                textView2.text = "Update: $it"
            }
        }
    }

}
