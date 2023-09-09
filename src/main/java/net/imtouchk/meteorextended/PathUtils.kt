package net.imtouchk.meteorextended

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalGetToBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

class PathUtils {
    companion object {
        // TODO: NOT WORKING!!
        fun hasActiveJob(): Boolean {
            val pathingBehavior = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior
            return pathingBehavior.hasPath()
        }

        fun stopAny() {
            val pathingBehavior = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior
            pathingBehavior.cancelEverything()
        }

        fun setGoal(pos: BlockPos) {
            stopAny()
            val goalProcess = BaritoneAPI.getProvider().primaryBaritone.customGoalProcess
            val goal = GoalGetToBlock(pos)
            goalProcess.setGoalAndPath(goal)
        }

        fun explore(currentPos: Vec3d) {
            stopAny()
            val exploreProcess = BaritoneAPI.getProvider().primaryBaritone.exploreProcess
            exploreProcess.explore(
                currentPos.x.toInt(),
                currentPos.z.toInt()
            )
        }
    }

}
