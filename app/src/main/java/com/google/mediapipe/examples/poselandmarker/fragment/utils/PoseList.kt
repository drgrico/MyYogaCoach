package com.google.mediapipe.examples.poselandmarker.fragment.utils

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.security.AccessController.getContext
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses.getPoseList


class PoseList : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var poseAdapter: PoseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose_list)

        recyclerView = findViewById(R.id.recycle_view)
//        recyclerView.addItemDecoration(
//            DividerItemDecoration(
//                this,
//                DividerItemDecoration.VERTICAL
//            )
//        )

        val itemDecorator = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        itemDecorator.setDrawable(ContextCompat.getDrawable(this, R.drawable.item_divider)!!)
        recyclerView.addItemDecoration(itemDecorator)

        poseAdapter = PoseAdapter(getPoseList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = poseAdapter

        poseAdapter.setOnItemClickListener { view, position ->
//            val selectedItemData: String = poseAdapter.getItem(position).name
            val selectedItemData: String = position.toString()

            val resultIntent = Intent()
            resultIntent.putExtra("selectedItemData", selectedItemData)
            setResult(RESULT_OK, resultIntent)

            finish()
        }
    }
}
