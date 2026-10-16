package crocodile8.universal_cache_app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import crocodile8.universal_cache.CachedSourceNoParams
import crocodile8.universal_cache.FromCache
import crocodile8.universal_cache.get
import java.util.concurrent.atomic.AtomicInteger

/**
 * It's a sample application. Library code is in another module.
 */
@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAppTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}

@Preview
@Composable
private fun MainScreen() {
    Column(
        modifier = Modifier
            .safeContentPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val source = remember {
            val taskInvocationCnt = AtomicInteger()
            CachedSourceNoParams(
                source = {
                    // Loading emulation
                    Thread.sleep(1000)
                    Log.i("test_", "test_ longRunningTask")
                    taskInvocationCnt.incrementAndGet()
                }
            )
        }

        val text1 = remember { mutableStateOf("") }
        val text2 = remember { mutableStateOf("") }

        // Launch and observe parallel requests
        LaunchedEffect(Unit) {
            source.get(FromCache.IF_HAVE)
                .collect {
                    Log.i("test_", "test_ collect 1: $it")
                    text1.value = "Ready, invocation count: $it"
                }
            source.get(FromCache.IF_HAVE)
                .collect {
                    Log.i("test_", "test_ collect 2: $it")
                    text1.value = "Ready, invocation count: $it"
                }
            source.get(FromCache.IF_HAVE)
                .collect {
                    Log.i("test_", "test_ collect 3: $it")
                    text1.value = "Ready, invocation count: $it"
                }
        }

        // Observe updates
        LaunchedEffect(Unit) {
            source.updates.collect {
                Log.i("test_", "test_ collect updates: $it")
                text2.value = "Update: $it"
            }
        }

        Text(
            text = text1.value,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = text2.value,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
