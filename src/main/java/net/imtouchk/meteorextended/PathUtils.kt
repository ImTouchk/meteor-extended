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
            val primaryBaritone = BaritoneAPI.getProvider().primaryBaritone
            primaryBaritone.followProcess.cancel()
            primaryBaritone.pathingBehavior.cancelEverything()
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

        fun getNearbyOldChunks(): List<ChunkPos> {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return listOf<ChunkPos>()
            return module.oldChunks.toList()
        }

        fun getNearbyNewChunks(): List<ChunkPos> {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return listOf<ChunkPos>()
            return module.newChunks.toList()
        }

        fun getNearestOldChunk(pos: BlockPos, ignoreSelf: Boolean): ChunkPos {
            val oldChunks = getNearbyOldChunks()
            if(oldChunks.isEmpty()) return ChunkPos(pos)

            val current = ChunkPos(pos)
            var nearest = oldChunks[0]
            for(chunk in oldChunks) {
                if(ignoreSelf && chunk == ChunkPos(pos))
                    continue

                if(chunk.getChebyshevDistance(current) <= nearest.getChebyshevDistance(current))
                    nearest = chunk
            }
            return nearest
        }

        fun getNearestNewChunk(pos: BlockPos, ignoreSelf: Boolean): ChunkPos {
            val newChunks = getNearbyNewChunks()
            if(newChunks.isEmpty()) return ChunkPos(pos)

            val current = ChunkPos(pos)
            var nearest = newChunks[0]
            for(chunk in newChunks) {
                if(ignoreSelf && chunk == ChunkPos(pos))
                    continue

                if(chunk.getChebyshevDistance(current) <= nearest.getChebyshevDistance(current))
                    nearest = chunk
            }
            return nearest
        }

        fun isNewChunk(chunkPos: ChunkPos): Boolean {
            return getNearbyNewChunks()
                .contains(chunkPos)
        }

        fun isOldChunk(chunkPos: ChunkPos): Boolean {
            return getNearbyOldChunks()
                .contains(chunkPos)
        }

        fun isInNewChunk(pos: BlockPos): Boolean {
            return getNearbyNewChunks()
                .contains(ChunkPos(pos))
        }

        fun isInOldChunk(pos: BlockPos): Boolean {
            return getNearbyOldChunks()
                     .contains(ChunkPos(pos))
        }

    }
}
