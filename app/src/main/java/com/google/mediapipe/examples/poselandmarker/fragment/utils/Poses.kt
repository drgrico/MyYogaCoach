package com.google.mediapipe.examples.poselandmarker.fragment.utils

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.R
import java.io.BufferedReader
import java.io.InputStreamReader

object Poses {
    private const val TAG = "PoseData"

    private var list: MutableList<Pose> = mutableListOf(
        Pose(R.drawable.yoga_pose01_mountain, "Mountain Pose", "Easy - Strengthen legs, improve posture"),
        Pose(R.drawable.yoga_pose03_childs_pose, "Child's Pose", "Easy - Relax and stretch the back"),
        Pose(R.drawable.yoga_pose10_corpse, "Corpse Pose", "Easy - Relax and rejuvenate the body"),
        Pose(R.drawable.yoga_pose07_chair, "Chair Pose", "Intermediate - Build strength in the thighs and core"),
        Pose(R.drawable.yoga_pose04_baby_cobra, "Baby Cobra Pose", "Intermediate - Strengthen back muscles"),
        Pose(R.drawable.yoga_pose05_bridge, "Bridge Pose", "Intermediate - Open the chest and improve spine flexibility"),
        Pose(R.drawable.yoga_pose02_downward_facing_dog, "Downward Facing Dog", "Intermediate - Stretch and strengthen the whole body"),
        Pose(R.drawable.yoga_pose09_fish, "Fish Pose", "Intermediate - Open the chest and throat"),
        Pose(R.drawable.yoga_pose06_pigeon, "Pigeon Pose", "Intermediate - Open the hips and stretch the thighs"),
        Pose(R.drawable.yoga_pose08_dolphin_plank, "Dolphin Plank Pose", "Intermediate - Strengthen the core and arms")
    )
    private var correctPoses: MutableList<String> = mutableListOf()

    fun getPoseList(): MutableList<Pose> {
        return list
    }

    fun getPoseName(position: Int): String {
        return list[position].name
    }

    fun loadCorrectLandmarks(ctx: Context) {
        for (pose in list) {
            var landmarks = StringBuilder()

            try {
                val inputStream = ctx.assets.open("${pose.name}.txt")
                val reader = BufferedReader(InputStreamReader(inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    landmarks.append(line).append("\n")
                }

                reader.close()
                inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (landmarks.isNotEmpty()) {
                correctPoses.add(landmarks.toString())
                Log.d(TAG, "Read file ${pose.name}.txt")
                Log.d(TAG, "Read landmarks: $landmarks")
            }
            else
                Log.e(TAG, "Error reading landmarks from file")
        }
        Log.d(TAG, "PoseData list: $correctPoses")
    }

    fun getCorrectLandmarks(position: Int): String {
        Log.d(TAG, "Getting Landmarks:\n${correctPoses[position]}")
        return correctPoses[position]
    }

    fun filterResult(result: PoseLandmarkerHelper.ResultBundle): String {
        val relevantIndices = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)

        val allLandmarks = result.results.first().landmarks()
        val flattenedLandmarks = allLandmarks.flatten()

        val filteredLandmarks = flattenedLandmarks.filterIndexed { index, _ ->
            index in relevantIndices
        }

        Log.d(TAG, "Filtered length: ${filteredLandmarks.size}")
        return filteredLandmarks.toString()
    }
}

data class Pose(val imageResource: Int, val name: String, val description: String)
