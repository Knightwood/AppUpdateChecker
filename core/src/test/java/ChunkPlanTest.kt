import org.slf4j.LoggerFactory

class ChunkPlanTest(
    private val minChunkSize: Long = 1024 * 1024,
    private val chunkCount: Int = 10,
) {
    fun planChunks(totalLength: Long): List<Pair<Long, Long>> {
        assert(totalLength > minChunkSize) { "文件长度必须大于最小分块大小 $minChunkSize" }
        assert(chunkCount > 0) { "分块数量必须大于 0" }
        assert(totalLength >= chunkCount * minChunkSize) { "文件长度必须大于等于分块数量乘以最小分块大小" }
        val singleChunkLength = totalLength / chunkCount
        return (0 until chunkCount).map { i ->
            val start = i * singleChunkLength
            val end = if (i == chunkCount - 1) totalLength - 1 else (start + singleChunkLength - 1)
            start to end
        }
    }
}

private val logger = LoggerFactory.getLogger(ChunkPlanTest::class.java)
fun main() {
    val `1M` = 1L * 1024 * 1024
    //2.4M
    val `2_4M` = 2L * 1024 * 1024 + 400L*1024
    val chunkPlanTest = ChunkPlanTest()
    val chunks = chunkPlanTest.planChunks(`2_4M`)
    logger.info(chunks.toString())
}
