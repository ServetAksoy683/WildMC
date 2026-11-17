package com.movtery.zalithlauncher.ui.screens.content.wild

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.multiplayer.pingServer
import com.movtery.zalithlauncher.game.version.multiplayer.resolve
import com.movtery.zalithlauncher.path.GLOBAL_JSON
import com.movtery.zalithlauncher.utils.network.ServerAddress
import com.movtery.zalithlauncher.utils.network.fetchStringFromUrl
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** WildMC server settings. The update feed is a plain JSON file in the project repository. */
object WildConfig {
    const val SERVER_ADDRESS = "141.95.34.176:25626"
    const val UPDATES_URL =
        "https://raw.githubusercontent.com/ServetAksoy683/WildMC/main/server/updates.json"
}

@Serializable
data class WildUpdate(
    val title: String = "",
    val date: String = "",
    val tag: String = "",
    val body: String = "",
    val link: String = ""
)

@Serializable
private data class WildUpdateFeed(
    val updates: List<WildUpdate> = emptyList()
)

private sealed interface PingState {
    object Loading : PingState
    data class Online(val players: Int, val max: Int, val pingMs: Long, val version: String) : PingState
    object Offline : PingState
}

private sealed interface UpdatesState {
    object Loading : UpdatesState
    data class Loaded(val items: List<WildUpdate>) : UpdatesState
    object Failed : UpdatesState
}

@Composable
private fun rememberPing(refreshKey: Int): State<PingState> =
    produceState<PingState>(PingState.Loading, refreshKey) {
        value = PingState.Loading
        value = try {
            val result = pingServer(ServerAddress.parse(WildConfig.SERVER_ADDRESS).resolve())
            PingState.Online(
                players = result.status.players.online,
                max = result.status.players.max,
                pingMs = result.pingMs,
                version = result.status.version.name
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PingState.Offline
        }
    }

@Composable
private fun rememberUpdates(refreshKey: Int): State<UpdatesState> =
    produceState<UpdatesState>(UpdatesState.Loading, refreshKey) {
        value = UpdatesState.Loading
        value = try {
            val text = fetchStringFromUrl(WildConfig.UPDATES_URL)
            val feed = GLOBAL_JSON.decodeFromString(WildUpdateFeed.serializer(), text)
            UpdatesState.Loaded(feed.updates)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdatesState.Failed
        }
    }

@Composable
private fun Modifier.wildCard(
    shape: Shape = RoundedCornerShape(18.dp),
    borderAlpha: Float = 0.35f
): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(colors.surfaceContainer)
        .border(1.dp, colors.primary.copy(alpha = borderAlpha), shape)
}

/**
 * WildMC home content: brand, server status and join button next to the server updates feed.
 */
@Composable
fun WildHome(
    onQuickPlayServer: (Version?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    val ping by rememberPing(refreshKey)
    val updates by rememberUpdates(refreshKey)
    val uriHandler = LocalUriHandler.current

    val onJoin: () -> Unit = { onQuickPlayServer(null, WildConfig.SERVER_ADDRESS) }
    val onRefresh: () -> Unit = { refreshKey++ }
    val onOpen: (String) -> Unit = { link -> runCatching { uriHandler.openUri(link) } }

    BoxWithConstraints(modifier = modifier) {
        if (maxWidth >= 640.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroPanel(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    compact = false,
                    ping = ping,
                    onJoin = onJoin
                )
                UpdatesPanel(
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                    state = updates,
                    onRefresh = onRefresh,
                    onOpen = onOpen
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroPanel(
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                    ping = ping,
                    onJoin = onJoin
                )
                UpdatesPanel(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = updates,
                    onRefresh = onRefresh,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun HeroPanel(
    modifier: Modifier,
    compact: Boolean,
    ping: PingState,
    onJoin: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        val logoShape = RoundedCornerShape(28.dp)
        Image(
            painter = painterResource(R.drawable.wildmc_logo),
            contentDescription = null,
            modifier = Modifier
                .size(if (compact) 96.dp else 150.dp)
                .clip(logoShape)
                .border(1.5.dp, colors.primary, logoShape)
        )
        Text(
            text = "WildMC",
            color = colors.primary,
            fontSize = if (compact) 24.sp else 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Text(
            text = stringResource(R.string.wild_season),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 3.sp
        )

        ServerStatusCard(
            modifier = Modifier.fillMaxWidth(),
            ping = ping
        )

        Button(
            onClick = onJoin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = stringResource(R.string.wild_join),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Text(
            text = stringResource(R.string.wild_join_hint, WildConfig.SERVER_ADDRESS),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ServerStatusCard(
    modifier: Modifier,
    ping: PingState,
) {
    val colors = MaterialTheme.colorScheme
    val dotColor = when (ping) {
        is PingState.Online -> colors.primary
        PingState.Offline -> colors.error
        PingState.Loading -> colors.onSurfaceVariant
    }
    val statusText = when (ping) {
        is PingState.Online -> stringResource(R.string.wild_server_online)
        PingState.Offline -> stringResource(R.string.wild_server_offline)
        PingState.Loading -> stringResource(R.string.wild_server_checking)
    }

    Column(
        modifier = modifier.wildCard().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = statusText,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = WildConfig.SERVER_ADDRESS,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (ping is PingState.Online) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.wild_players, ping.players, ping.max),
                    color = colors.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.wild_ping, ping.pingMs.toInt()),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (ping.version.isNotBlank()) {
                Text(
                    text = ping.version,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UpdatesPanel(
    modifier: Modifier,
    state: UpdatesState,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.wild_updates_title).uppercase(),
                modifier = Modifier.weight(1f),
                color = colors.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.wild_refresh))
            }
        }

        when (state) {
            UpdatesState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            UpdatesState.Failed -> {
                MessageBox(
                    text = stringResource(R.string.wild_updates_failed),
                    actionText = stringResource(R.string.wild_retry),
                    onAction = onRefresh
                )
            }
            is UpdatesState.Loaded -> {
                if (state.items.isEmpty()) {
                    MessageBox(text = stringResource(R.string.wild_updates_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(state.items) { update ->
                            UpdateCard(update = update, onOpen = onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBox(
    text: String,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().wildCard().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (actionText != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
private fun UpdateCard(
    update: WildUpdate,
    onOpen: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hasLink = update.link.isNotBlank()
    var cardModifier = Modifier.fillMaxWidth().wildCard()
    if (hasLink) cardModifier = cardModifier.clickable { onOpen(update.link) }

    Column(
        modifier = cardModifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = update.title,
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (update.tag.isNotBlank()) {
                Text(
                    text = update.tag,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = colors.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (update.date.isNotBlank()) {
            Text(
                text = update.date,
                color = colors.primary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (update.body.isNotBlank()) {
            Text(
                text = update.body,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
