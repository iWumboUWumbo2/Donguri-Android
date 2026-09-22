package world.wumbo.donguri.bbs

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import world.wumbo.donguri.bbs.model.Post
import world.wumbo.donguri.bbs.net.PostService
import world.wumbo.donguri.bbs.text.asPlainPostText

/// Reporting objectionable posts.
///
/// Donguri displays user-generated content it does not host or moderate, so
/// both stores expect a way for users to report it. This composes a mail to
/// the app's support address with enough context to identify the post
/// upstream; nothing is sent automatically.
object ReportService {
    /// Also publish this as the support address on the store listing — the
    /// guidelines expect the two to match.
    const val SUPPORT_EMAIL = "john.connery3223@gmail.com"

    fun report(
        context: Context,
        post: Post,
        index: Int,
        boardUrl: String,
        threadId: Long,
        threadTitle: String?,
    ) {
        val subject = "[Donguri] 不適切な書き込みの通報 / Content report"
        val body = buildString {
            appendLine("不適切な書き込みを通報します。")
            appendLine("Reporting a post as objectionable.")
            appendLine()
            appendLine("── 対象 / Reported post ──────────────")
            appendLine("スレッド / Thread: ${threadTitle ?: "-"}")
            appendLine("URL: ${PostService.archiveUrl(boardUrl, threadId)}")
            appendLine("レス番号 / Post: >>${index + 1}")
            appendLine("名前 / Name: ${post.name}")
            appendLine("ID: ${post.id ?: "-"}")
            appendLine("日時 / Date: ${post.date}")
            appendLine()
            appendLine("── 本文 / Content ───────────────────")
            appendLine(post.text.asPlainPostText().take(1000))
            appendLine()
            appendLine("── 通報理由 / Reason ────────────────")
            appendLine("(ここに理由をご記入ください / please describe the problem here)")
        }

        // ACTION_SENDTO with a mailto: URI resolves only to mail apps, which is
        // what keeps this out of the general share sheet.
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$SUPPORT_EMAIL".toUri()
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { context.startActivity(intent) }
    }
}
