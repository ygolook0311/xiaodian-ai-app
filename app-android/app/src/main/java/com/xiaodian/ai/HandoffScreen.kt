package com.xiaodian.ai

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Boss-mode handoff screen (step 1 of the App plan, 2026-11-03).
// NO timed polling (2026-11-03 user report: auto-refresh kept
// interrupting typing/replying). Refresh is manual + on entry +
// after a reply/resolve, so a draft in a card is never reset by
// a background refresh. Server is still the source of truth, the
// boss just pulls it when they want it.
@Composable
fun HandoffScreen(baseUrl: String) {
    var items by remember { mutableStateOf<List<HandoffItem>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            refreshing = true
            try {
                val fresh = withContext(Dispatchers.IO) {
                    HandoffRepository(baseUrl).fetchHandoffs()
                }
                // Only assign when changed: assigning an identical list
                // still recomposes the cards and can steal input focus.
                if (fresh != items) items = fresh
                error = ""
            } catch (e: Exception) {
                error = "拉取待处理失败：${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    // Once on entry only. No while(true) polling loop.
    LaunchedEffect(baseUrl) {
        refresh()
    }

    Column(Modifier.padding(16.dp)) {
        Text("待人工处理（语音 / 图片 / 视频 / 文件）")
        Text("文字点单不受影响，AI 照常回；这里只处理媒体那条。")
        Text("不自动刷新：进页面拉一次，之后点“立即刷新”查看新消息，正在输入的回复不会被刷新打断。")
        if (error.isNotBlank()) Text(error)
        Button(onClick = { refresh() }, enabled = !refreshing) {
            Text(if (refreshing) "刷新中…" else "立即刷新")
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text("暂无待处理")
        } else {
            LazyColumn {
                items(items, key = { it.msgid }) { item ->
                    HandoffCard(item, baseUrl, onDone = { refresh() })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HandoffCard(item: HandoffItem, baseUrl: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replyText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    // Close media preview / stop voice: previously there was no way
    // to close an opened image or stop a playing voice (2026-11-03
    // user report: "App连关闭都没有").
    fun closeMedia() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        imageBitmap = null
        status = ""
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${item.typeLabel()} · 顾客 ${item.externalUserid.take(12)}…")
            Text(item.createdAt)
            Row {
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            when (item.msgtype) {
                                "image" -> {
                                    val bytes = withContext(Dispatchers.IO) {
                                        HandoffRepository(baseUrl).downloadMedia(item.mediaId)
                                    }
                                    imageBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                                "voice" -> {
                                    val bytes = withContext(Dispatchers.IO) {
                                        HandoffRepository(baseUrl).downloadMedia(item.mediaId)
                                    }
                                    val tmp = File(context.cacheDir, "handoff_${item.msgid}.amr")
                                    tmp.writeBytes(bytes)
                                    val p = MediaPlayer()
                                    p.setDataSource(tmp.absolutePath)
                                    p.prepare()
                                    p.start()
                                    player = p
                                    status = "正在播放语音"
                                }
                                else -> {
                                    // Video / file: hand to the system player/viewer.
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse(HandoffRepository(baseUrl).mediaUrl(item.mediaId))
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        } catch (e: Exception) {
                            status = "媒体打开失败：${e.message}"
                        }
                    }
                }) { Text("查看 / 播放") }
            }
            if (imageBitmap != null) {
                Image(bitmap = imageBitmap!!.asImageBitmap(), contentDescription = "顾客图片")
            }
            if (imageBitmap != null || player != null) {
                OutlinedButton(onClick = { closeMedia() }) { Text("关闭媒体") }
            }
            TextField(
                value = replyText,
                onValueChange = { replyText = it },
                label = { Text("回顾客的话") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Button(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                HandoffRepository(baseUrl).reply(item.externalUserid, replyText, item.msgid)
                            }
                            status = "已发送"
                            replyText = ""
                            onDone()
                        } catch (e: Exception) {
                            status = "发送失败：${e.message}"
                        }
                    }
                }, enabled = replyText.isNotBlank()) { Text("发送给顾客") }
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                HandoffRepository(baseUrl).resolve(item.externalUserid, item.msgid)
                            }
                            onDone()
                        } catch (e: Exception) {
                            status = "标记失败：${e.message}"
                        }
                    }
                }) { Text("已处理") }
            }
            if (status.isNotBlank()) Text(status)
        }
    }
}
