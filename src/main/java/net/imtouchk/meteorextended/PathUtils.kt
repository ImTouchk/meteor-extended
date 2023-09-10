package net.imtouchk.meteorextended

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalGetToBlock
import net.imtouchk.meteorextended.modules.NewChunks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
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

        // Returns false if NewChunks is not activated
        fun isInNewChunk(pos: BlockPos): Boolean {
            val module = InteropUtils.getMeteorModule("new-chunks") as NewChunks
            if(!module.isActive) return false

            return module.newChunks.contains(ChunkPos(pos))
        }

        // Returns current chunk if NewChunks is not active
        fun getNearestOldChunk(pos: BlockPos): ChunkPos {
            val module = InteropUtils.getMeteorModule("new-chunks") as NewChunks
            if(!module.isActive) return ChunkPos(pos)

            val oldChunks = module.oldChunks
            if(oldChunks.isEmpty()) return ChunkPos(pos) // TODO: do something more intelligent

            val current = ChunkPos(pos)
            var nearest = oldChunks[0]
            for(chunk in oldChunks) {
                if(chunk.getChebyshevDistance(current) < nearest.getChebyshevDistance(current))
                    nearest = chunk
            }
            return nearest
        }

        // Returns true if NewChunks is not active
        fun isInOldChunk(pos: BlockPos): Boolean {
            val module = InteropUtils.getMeteorModule("new-chunks") as NewChunks
            if(!module.isActive) return true

            val current = ChunkPos(pos)
            val oldChunks = module.oldChunks
            for(chunk in oldChunks) {
                if(chunk.x == current.x && chunk.z == current.z)
                    return true
            }

            return false
        }
    }
}
