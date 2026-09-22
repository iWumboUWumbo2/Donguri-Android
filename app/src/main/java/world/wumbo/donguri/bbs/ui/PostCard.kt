package world.wumbo.donguri.bbs.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import world.wumbo.donguri.R
import world.wumbo.donguri.bbs.model.Post
import world.wumbo.donguri.bbs.text.asPlainPostText
import world.wumbo.donguri.ui.Chip
import world.wumbo.donguri.ui.PostNumberBadge

@Composable
fun PostCard(
    index: Int,
    post: Post,
    idCount: Int,
    tripCount: Int,
    isIdHighlighted: Boolean,
    isTripHighlighted: Boolean,
    isRowHighlighted: Boolean,
    onShowReplies: (List<Int>) -> Unit,
    onIdTap: (String) -> Unit,
    onTripTap: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onNgId: (String) -> Unit,
    onNgName: (String) -> Unit,
    onReport: (() -> Unit)? = null,
    onWordTap: (PostTextTap) -> Unit = {},
    highlightRange: IntRange? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    val background = if (isRowHighlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PostNumberBadge(index + 1)

            if (post.replies.isNotEmpty()) {
                Chip(
                    text = post.replies.size.toString(),
                    icon = Icons.AutoMirrored.Filled.Message,
                    filled = false,
                    modifier = Modifier.clickable { onShowReplies(post.replies) },
                )
            }

            NameLabel(
                post = post,
                isTripHighlighted = isTripHighlighted,
                onTripTap = onTripTap,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Box {
            PostText(
                text = post.text,
                highlightRange = highlightRange,
                onLinkClick = onLinkClick,
                onWordTap = onWordTap,
                onLongPress = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_post)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        copyToClipboard(context, post.text.asPlainPostText())
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.translate)) },
                    leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        translate(context, post.text.asPlainPostText())
                    },
                )
                post.id?.let { id ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ng_this_id)) },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onNgId(id)
                        },
                    )
                }
                if (post.name.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ng_this_name)) },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onNgName(post.name)
                        },
                    )
                }
                onReport?.let { report ->
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.report_post)) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            report()
                        },
                    )
                }
            }
        }

        if (post.imageUrls.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (url in post.imageUrls) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { fullscreenImageUrl = url },
                    )
                }
            }
        }

        fullscreenImageUrl?.let { url ->
            FullscreenImageViewer(url = url, onDismiss = { fullscreenImageUrl = null })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = post.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            post.id?.let { id ->
                Chip(
                    text = if (idCount > 1) "ID:$id($idCount)" else "ID:$id",
                    tint = if (isIdHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.clickable { onIdTap(id) },
                )
            }
        }
    }
}

@Composable
private fun NameLabel(
    post: Post,
    isTripHighlighted: Boolean,
    onTripTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = buildString {
        append(post.name)
        if (post.email.isNotEmpty()) append(" [${post.email}]")
    }
    val trip = post.trip
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = if (trip != null && isTripHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = if (trip != null) modifier.clickable { onTripTap(trip) } else modifier,
    )
}

/// Stands in for a post hidden by an NG rule. 5ch clients call this あぼーん.
@Composable
fun AbornPlaceholder(index: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PostNumberBadge(index + 1, muted = true)
        Text(
            text = stringResource(R.string.aborn),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("post", text))
}

/// Android has no system translation sheet the way iOS 17.4+ does; the
/// PROCESS_TEXT intent is what Google Translate and the other translators
/// register for, and is the closest equivalent.
private fun translate(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.translate))
    runCatching { context.startActivity(chooser) }
}
