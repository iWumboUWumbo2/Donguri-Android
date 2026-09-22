package world.wumbo.donguri.bbs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import world.wumbo.donguri.R
import world.wumbo.donguri.bbs.model.Post

/// Floating banner naming the poster whose posts are currently highlighted.
@Composable
fun HighlightBanner(text: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_highlight))
            }
        }
    }
}

/// Floating card listing the replies to a post (or a single `>>N` target),
/// each of which can itself be tapped to open its own replies.
@Composable
fun ReplyPreviewCard(
    indices: List<Int>,
    posts: List<Post>,
    onClose: () -> Unit,
    onShowReplies: (List<Int>) -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (indices.size > 1) {
                    stringResource(R.string.reply_count, indices.size)
                } else {
                    stringResource(R.string.replies)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }

        LazyColumn(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(indices.filter { it in posts.indices }) { index ->
                Column {
                    Row(
                        modifier = Modifier
                            .clickable { onJump(index) }
                            .padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.SubdirectoryArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                        Text(
                            text = stringResource(R.string.jump_to_res, index + 1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PostCard(
                        index = index,
                        post = posts[index],
                        idCount = 0,
                        tripCount = 0,
                        isIdHighlighted = false,
                        isTripHighlighted = false,
                        isRowHighlighted = false,
                        onShowReplies = onShowReplies,
                        onIdTap = {},
                        onTripTap = {},
                        onLinkClick = {},
                        onNgId = {},
                        onNgName = {},
                    )
                }
            }
        }
    }
}
