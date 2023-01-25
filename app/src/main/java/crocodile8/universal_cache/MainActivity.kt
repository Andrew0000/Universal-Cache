package crocodile8.universal_cache

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private val taskInvocationCnt = AtomicInteger()
    private val longRunningTask = {
        Thread.sleep(1000)
        Log.i("test_", "test_ longRunningTask")
        taskInvocationCnt.incrementAndGet()
    }
    private val source = CachedSourceNoParams<Int>(longRunningTask)

    private lateinit var textView1: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView1 = findViewById(R.id.textView_1)

        launchParallelRequests(textView1)
    }

    @SuppressLint("SetTextI18n")
    private fun launchParallelRequests(textView1: TextView) {
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
}