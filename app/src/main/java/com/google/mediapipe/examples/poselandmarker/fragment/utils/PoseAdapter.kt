package com.google.mediapipe.examples.poselandmarker.fragment.utils

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.fragment.CameraFragment

class PoseAdapter(private val poses: List<Pose>) :
    RecyclerView.Adapter<PoseAdapter.PoseViewHolder>() {

    private var onItemClickListener: ((View, Int) -> Unit)? = null

    class PoseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val poseImage: ImageView = itemView.findViewById(R.id.poseImage)
        val poseName: TextView = itemView.findViewById(R.id.poseName)
        val poseDescription: TextView = itemView.findViewById(R.id.poseDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pose, parent, false)
        return PoseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PoseViewHolder, position: Int) {
        val pose = poses[position]

        holder.poseImage.setImageResource(pose.imageResource)
        holder.poseName.text = pose.name
        holder.poseDescription.text = pose.description

        holder.itemView.setOnClickListener {
            Log.d("RecycleView", "Item clicked")
            onItemClickListener?.invoke(it, position)
        }
    }

    override fun getItemCount(): Int {
        return poses.size
    }

    fun setOnItemClickListener(listener: (View, Int) -> Unit) {
        onItemClickListener = listener
    }

    fun getItem(position: Int): Pose {
        return poses[position]
    }
}