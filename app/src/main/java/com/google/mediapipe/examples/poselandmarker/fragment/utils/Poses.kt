package com.google.mediapipe.examples.poselandmarker.fragment.utils

import com.google.mediapipe.examples.poselandmarker.R

object Poses {
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

    fun getPoseList(): MutableList<Pose> {
        return list
    }
}

data class Pose(val imageResource: Int, val name: String, val description: String)
