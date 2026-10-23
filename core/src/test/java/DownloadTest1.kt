import com.github.knightwood.downloader.core.model.DownloadTaskConf
import com.github.knightwood.downloader.core.task.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * 直接生成DownloadTask并下载
 */
class DownloadTest1 {
    val scope = CoroutineScope(Dispatchers.IO)

    operator fun invoke() {
        val task = DownloadTask(
            id = 1,
            conf = generateTaskConf(),
        )
        scope.launch {
            task.execute()
        }
        scope.launch {
            delay(3.seconds)
            task.pause()
            delay(3.seconds)
            task.resume()
            delay(3.seconds)
            task.cancel()
            task.saveState()
        }
    }

    private fun generateTaskConf(): DownloadTaskConf {
        return DownloadTaskConf(
            url = "http://www.example.com",
            md5 = null,
        )
    }
}
