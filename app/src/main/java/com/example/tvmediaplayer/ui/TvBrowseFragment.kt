package com.example.tvmediaplayer.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.playback.PlaybackQueueBuilder
import com.example.tvmediaplayer.playback.PlaybackService
import com.example.tvmediaplayer.playback.SmbMediaItemFactory
import com.example.tvmediaplayer.ui.presenter.SimpleTextPresenter
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvBrowseFragment : VerticalGridSupportFragment() {

    private val viewModel by viewModels<TvBrowserViewModel> {
        TvBrowserViewModel.factory(requireContext().applicationContext)
    }
    private val listAdapter by lazy { ArrayObjectAdapter(SimpleTextPresenter()) }
    private val mediaItemFactory by lazy { SmbMediaItemFactory() }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "电视音乐播放器"

        gridPresenter = VerticalGridPresenter().apply {
            numberOfColumns = 1
        }
        adapter = listAdapter

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is UiItem.ActionItem -> onActionClicked(item)
                is UiItem.FileItem -> onFileClicked(item.entry)
            }
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                render(state)
                state.toast?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.consumeToast()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (viewModel.state.value.currentPath.isNotBlank()) {
                        viewModel.enterDirectory(SmbEntry("..", viewModel.state.value.currentPath, true))
                        true
                    } else {
                        false
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    showConnectionManagerDialog()
                    true
                }
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun render(state: TvBrowserState) {
        listAdapter.clear()

        listAdapter.add("【连接管理】")
        listAdapter.add("当前连接：${configText(state.config)}")
        listAdapter.add("已保存连接：${state.savedConnections.size} 个")
        listAdapter.add(UiItem.ActionItem(Action.OPEN_CONNECTION_MANAGER, "管理连接（编辑 / 切换 / 新建）"))

        listAdapter.add("【文件浏览】")
        val pathLabel = if (state.currentPath.isBlank()) "/" else "/${state.currentPath}"
        listAdapter.add("当前路径：$pathLabel")
        listAdapter.add(UiItem.ActionItem(Action.REFRESH, "刷新当前目录"))
        if (state.error != null) {
            listAdapter.add(UiItem.ActionItem(Action.RETRY, "重试连接"))
        }

        if (state.loading) {
            listAdapter.add("加载中...")
        } else {
            if (state.currentPath.isNotBlank()) {
                listAdapter.add(UiItem.FileItem(SmbEntry("..", state.currentPath, true), "[目录] ..（上一级）"))
            }
            if (state.entries.isEmpty()) {
                listAdapter.add("当前目录为空")
            } else {
                state.entries.forEach { entry ->
                    val icon = if (entry.isDirectory) "[目录]" else "[音频]"
                    listAdapter.add(UiItem.FileItem(entry, "$icon ${entry.name}"))
                }
            }
        }

        listAdapter.add("【播放控制】")
        listAdapter.add(UiItem.ActionItem(Action.PLAY_ALL, "播放当前目录（顺序）"))
        listAdapter.add(UiItem.ActionItem(Action.PLAY_SHUFFLE, "播放当前目录（随机）"))

        state.error?.let {
            listAdapter.add("【连接状态】")
            listAdapter.add(it)
        }
    }

    private fun onActionClicked(item: UiItem.ActionItem) {
        when (item.action) {
            Action.OPEN_CONNECTION_MANAGER -> showConnectionManagerDialog()
            Action.REFRESH -> viewModel.loadCurrentPath()
            Action.RETRY -> viewModel.loadCurrentPath()
            Action.PLAY_ALL -> playDirectory(shuffle = false)
            Action.PLAY_SHUFFLE -> playDirectory(shuffle = true)
        }
    }

    private fun onFileClicked(entry: SmbEntry) {
        if (entry.isDirectory) {
            viewModel.enterDirectory(entry)
            return
        }
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        val startIndex = PlaybackQueueBuilder.startIndex(queue, entry)
        playQueue(queue, startIndex, shuffle = false)
    }

    private fun playDirectory(shuffle: Boolean) {
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        playQueue(queue, startIndex = 0, shuffle = shuffle)
    }

    private fun playQueue(queue: List<SmbEntry>, startIndex: Int, shuffle: Boolean) {
        if (queue.isEmpty()) {
            Toast.makeText(requireContext(), "当前目录没有可播放音频", Toast.LENGTH_SHORT).show()
            return
        }
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(requireContext(), "播放器初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
        lifecycleScope.launch {
            val mediaItems = withContext(Dispatchers.IO) {
                mediaItemFactory.create(config, queue)
            }
            controller.setShuffleModeEnabled(shuffle)
            controller.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
            controller.prepare()
            controller.play()
        }
    }

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return

        val token = SessionToken(
            requireContext(),
            ComponentName(requireContext(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller -> mediaController = controller }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(requireContext(), "播放器连接失败", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun releaseController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    private fun showConnectionManagerDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("连接管理")
            .setMessage("请选择操作")
            .setPositiveButton("编辑当前连接") { _, _ -> showConfigDialog(false) }
            .setNeutralButton("新建连接") { _, _ -> showConfigDialog(true) }
            .setNegativeButton("切换连接") { _, _ -> showSwitchDialog() }
            .show()
    }

    private fun showSwitchDialog() {
        val saved = viewModel.state.value.savedConnections
        if (saved.isEmpty()) {
            Toast.makeText(requireContext(), "还没有已保存连接，请先保存一个连接", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = saved.map { "${it.name}（${it.config.host}）" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("切换 SMB 连接")
            .setItems(labels) { _, which ->
                viewModel.switchConnection(saved[which].id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showConfigDialog(saveAsNewDefault: Boolean) {
        val current = viewModel.state.value.config
        val context = requireContext()

        val nameInput = EditText(context).apply {
            hint = "连接名称，例如 客厅 NAS"
            typeface = AppFonts.regular(context)
            val active = viewModel.state.value.savedConnections
                .firstOrNull { it.id == viewModel.state.value.activeConnectionId }
            setText(active?.name.orEmpty())
        }
        val hostInput = EditText(context).apply {
            hint = "SMB 服务器地址，例如 192.168.0.10"
            typeface = AppFonts.regular(context)
            setText(current.host)
        }
        val shareInput = EditText(context).apply {
            hint = "共享名（可留空，留空显示所有共享）"
            typeface = AppFonts.regular(context)
            setText(current.share)
        }
        val pathInput = EditText(context).apply {
            hint = "子路径（可留空）"
            typeface = AppFonts.regular(context)
            setText(current.path)
        }
        val userInput = EditText(context).apply {
            hint = "用户名（访客可留空）"
            typeface = AppFonts.regular(context)
            setText(current.username)
        }
        val passInput = EditText(context).apply {
            hint = "密码（访客可留空）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = AppFonts.regular(context)
            setText(current.password)
        }
        val guestCheck = CheckBox(context).apply {
            text = "访客 / 匿名"
            typeface = AppFonts.regular(context)
            isChecked = current.guest
        }
        val smb1Check = CheckBox(context).apply {
            text = "启用 SMB1 兼容（默认关闭）"
            typeface = AppFonts.regular(context)
            isChecked = current.smb1Enabled
        }
        val saveAsNewCheck = CheckBox(context).apply {
            text = "另存为新连接"
            typeface = AppFonts.regular(context)
            isChecked = saveAsNewDefault
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 20)
            addView(nameInput)
            addView(hostInput)
            addView(shareInput)
            addView(pathInput)
            addView(userInput)
            addView(passInput)
            addView(guestCheck)
            addView(smb1Check)
            addView(saveAsNewCheck)
        }

        AlertDialog.Builder(context)
            .setTitle("SMB 连接配置")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存并连接") { _, _ ->
                val config = SmbConfig(
                    host = hostInput.text.toString().trim(),
                    share = shareInput.text.toString().trim(),
                    path = pathInput.text.toString().trim(),
                    username = userInput.text.toString().trim(),
                    password = passInput.text.toString(),
                    guest = guestCheck.isChecked,
                    smb1Enabled = smb1Check.isChecked
                )
                viewModel.saveConfig(
                    config = config,
                    name = nameInput.text.toString().trim(),
                    saveAsNew = saveAsNewCheck.isChecked
                )
            }
            .show()
    }

    private fun configText(config: SmbConfig): String {
        if (config.host.isBlank()) return "未配置"
        return if (config.share.isBlank()) {
            "smb://${config.host}（全部共享）"
        } else {
            val path = config.normalizedPath()
            if (path.isBlank()) "smb://${config.host}/${config.share}"
            else "smb://${config.host}/${config.share}/$path"
        }
    }

    private sealed interface UiItem {
        data class ActionItem(val action: Action, private val text: String) : UiItem {
            override fun toString(): String = text
        }

        data class FileItem(val entry: SmbEntry, private val text: String) : UiItem {
            override fun toString(): String = text
        }
    }

    private enum class Action {
        OPEN_CONNECTION_MANAGER,
        REFRESH,
        RETRY,
        PLAY_ALL,
        PLAY_SHUFFLE
    }
}
