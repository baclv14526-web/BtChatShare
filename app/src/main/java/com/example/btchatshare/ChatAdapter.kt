package com.example.btchatshare

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.btchatshare.db.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    var onOpenFileClick: ((ChatMessage) -> Unit)? = null,
    var onOpenLocationClick: ((ChatMessage) -> Unit)? = null,
    var onItemLongClick: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT     = 1
        private const val TYPE_RECEIVED = 2
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    class MsgHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutBubble:      View = itemView.findViewById(R.id.layoutBubble)
        val tvMessage:         TextView = itemView.findViewById(R.id.tvMessage)
        val tvTimestamp:       TextView = itemView.findViewById(R.id.tvTimestamp)
        val layoutFileActions: View? = itemView.findViewById(R.id.layoutFileActions)
        val btnOpenFile:       View? = itemView.findViewById(R.id.btnOpenFile)
        val btnOpenLocation:   View? = itemView.findViewById(R.id.btnOpenLocation)
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isMine) TYPE_SENT else TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_SENT)
            R.layout.item_message_sent else R.layout.item_message_received
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MsgHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        (holder as MsgHolder).apply {
            tvMessage.text = msg.text
            // Hiện giờ:phút; nếu tin nhắn từ hôm qua trở về trước thì hiện ngày đầy đủ
            tvTimestamp.text = formatTime(msg.timestamp)

            val hasFile = !msg.filePath.isNullOrBlank()
            if (hasFile) {
                layoutFileActions?.visibility = View.VISIBLE
                btnOpenFile?.setOnClickListener { onOpenFileClick?.invoke(msg) }
                btnOpenLocation?.setOnClickListener { onOpenLocationClick?.invoke(msg) }
            } else {
                layoutFileActions?.visibility = View.GONE
                btnOpenFile?.setOnClickListener(null)
                btnOpenLocation?.setOnClickListener(null)
            }

            layoutBubble.setOnLongClickListener {
                onItemLongClick?.invoke(msg)
                true
            }
        }
    }

    override fun getItemCount() = messages.size

    /** Thêm 1 tin nhắn mới (real-time khi đang chat). */
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * Thay toàn bộ danh sách (khi load lịch sử từ DB lúc mở màn hình).
     * Dùng DiffUtil để chỉ update các item thực sự thay đổi.
     */
    fun submitList(newList: List<ChatMessage>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = messages.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(o: Int, n: Int) =
                messages[o].timestamp == newList[n].timestamp &&
                messages[o].text == newList[n].text &&
                messages[o].filePath == newList[n].filePath
            override fun areContentsTheSame(o: Int, n: Int) =
                messages[o] == newList[n]
        })
        messages.clear()
        messages.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }

    private fun formatTime(ts: Long): String {
        val now = System.currentTimeMillis()
        val todayStart = now - (now % 86_400_000)
        return if (ts >= todayStart) timeFmt.format(Date(ts)) else dateFmt.format(Date(ts))
    }
}

