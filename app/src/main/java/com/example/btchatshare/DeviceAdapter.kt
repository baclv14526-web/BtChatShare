package com.example.btchatshare

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: MutableList<BluetoothDevice>,
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceHolder>() {

    class DeviceHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        val tvAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceHolder(view)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceHolder, position: Int) {
        val device = devices[position]
        val name = try { device.name } catch (e: SecurityException) { null }
        holder.tvName.text = name ?: "(Không rõ tên)"
        holder.tvAddress.text = device.address
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount(): Int = devices.size

    fun addIfAbsent(device: BluetoothDevice) {
        if (devices.none { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun submit(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }
}
