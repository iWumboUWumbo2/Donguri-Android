package world.wumbo.donguri.bbs.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import world.wumbo.donguri.bbs.model.BbsThread
import world.wumbo.donguri.bbs.text.htmlDecoded
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadService @Inject constructor() {

    suspend fun fetchThreads(boardUrl: String): List<BbsThread> = withContext(Dispatchers.IO) {
        val url = resolveAgainstBoard(boardUrl, "subject.txt")
        val body = httpGet(url).toString(SHIFT_JIS)
        parseSubjectTxt(body)
    }

    internal companion object {
        private val SUBJECT_LINE = Regex("""(\d+)\.dat<>(.*)\((\d+)\)\s*""")

        fun parseSubjectTxt(body: String): List<BbsThread> =
            body.lineSequence().mapNotNull { line ->
                val match = SUBJECT_LINE.matchEntire(line) ?: return@mapNotNull null
                val id = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val responseCount = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                BbsThread(
                    id = id,
                    title = match.groupValues[2].trim().htmlDecoded(),
                    responseCount = responseCount,
                )
            }.toList()
    }
}
