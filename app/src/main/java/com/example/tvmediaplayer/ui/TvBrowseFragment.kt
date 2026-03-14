package com.example.tvmediaplayer.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.ui.presenter.SimpleTextPresenter
import kotlinx.coroutines.launch

class TvBrowseFragment : BrowseSupportFragment() {

    private val viewModel by viewModels<TvBrowserViewModel>()
    private val rowsAdapter by lazy { ArrayObjectAdapter(ListRowPresenter()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TV Music Player"
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = 0xFF22C55E.toInt()

        adapter = rowsAdapter
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is UiItem.ActionItem -> onActionClicked(item)
                is UiItem.FileItem -> viewModel.enterDirectory(item.entry)
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

    private fun render(state: TvBrowserState) {
        rowsAdapter.clear()

        val configRow = ArrayObjectAdapter(SimpleTextPresenter()).apply {
            add(UiItem.ActionItem(Action.EDIT_CONFIG, "Connection: ${configText(state.config)}"))
            add(UiItem.ActionItem(Action.REFRESH, "Refresh current folder"))
        }
        rowsAdapter.add(ListRow(HeaderItem(0, "Connection and Actions"), configRow))

        val pathLabel = if (state.currentPath.isBlank()) "/" else "/${state.currentPath}"
        val browserRow = ArrayObjectAdapter(SimpleTextPresenter())
        if (state.loading) {
            browserRow.add("Loading...")
        } else {
            if (state.currentPath.isNotBlank()) {
                browserRow.add(UiItem.FileItem(SmbEntry("..", state.currentPath, true), "[DIR] .. (Parent)"))
            }
            if (state.entries.isEmpty()) {
                browserRow.add("No files")
            } else {
                state.entries.filterNot { it.name == ".." }.forEach { entry ->
                    val icon = if (entry.isDirectory) "[DIR]" else "[AUDIO]"
                    browserRow.add(UiItem.FileItem(entry, "$icon ${entry.name}"))
                }
            }
        }
        rowsAdapter.add(ListRow(HeaderItem(1, "Browser: $pathLabel"), browserRow))

        state.error?.let {
            val errorRow = ArrayObjectAdapter(SimpleTextPresenter()).apply {
                add("Error: $it")
            }
            rowsAdapter.add(ListRow(HeaderItem(2, "Connection status"), errorRow))
        }
    }

    private fun onActionClicked(item: UiItem.ActionItem) {
        when (item.action) {
            Action.EDIT_CONFIG -> showConfigDialog()
            Action.REFRESH -> viewModel.loadCurrentPath()
        }
    }

    private fun showConfigDialog() {
        val current = viewModel.state.value.config
        val context = requireContext()

        val hostInput = EditText(context).apply {
            hint = "Server IP, e.g. 192.168.31.233"
            setText(current.host)
        }
        val shareInput = EditText(context).apply {
            hint = "Share name, e.g. Banana"
            setText(current.share)
        }
        val pathInput = EditText(context).apply {
            hint = "Sub path, e.g. h/DLsite"
            setText(current.path)
        }
        val userInput = EditText(context).apply {
            hint = "Username (empty for guest)"
            setText(current.username)
        }
        val passInput = EditText(context).apply {
            hint = "Password (empty for guest)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(current.password)
        }
        val guestCheck = CheckBox(context).apply {
            text = "Guest / Anonymous"
            isChecked = current.guest
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 20, 36, 20)
            addView(hostInput)
            addView(shareInput)
            addView(pathInput)
            addView(userInput)
            addView(passInput)
            addView(guestCheck)
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("SMB Connection")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save and Connect") { _, _ ->
                val config = SmbConfig(
                    host = hostInput.text.toString().trim(),
                    share = shareInput.text.toString().trim(),
                    path = pathInput.text.toString().trim(),
                    username = userInput.text.toString().trim(),
                    password = passInput.text.toString(),
                    guest = guestCheck.isChecked
                )
                viewModel.saveConfig(config)
            }
            .show()
    }

    private fun configText(config: SmbConfig): String {
        if (config.host.isBlank() || config.share.isBlank()) return "Not configured"
        val path = config.normalizedPath()
        return if (path.isBlank()) {
            "smb://${config.host}/${config.share}"
        } else {
            "smb://${config.host}/${config.share}/$path"
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
        EDIT_CONFIG,
        REFRESH
    }
}
