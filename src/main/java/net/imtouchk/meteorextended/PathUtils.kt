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
            if(module == null || !module.isActive) return listOf()
            synchronized(module.oldChunks) {
                val set = HashSet(module.oldChunks)
                return set.toList()
            }
        }

        fun getNearbyNewChunks(): List<ChunkPos> {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return listOf()
            synchronized(module.newChunks) {
                val set = HashSet(module.newChunks)
                return set.toList()
            }
        }

        fun getNearestOldChunk(pos: BlockPos, ignoreSelf: Boolean): ChunkPos {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return ChunkPos(BlockPos(0, 0, 0))
            synchronized(module.oldChunks) {
                if(module.oldChunks.isEmpty())
                    return ChunkPos(BlockPos(0, 0, 0))

                val current = ChunkPos(pos)
                var nearest = module.oldChunks.first()
                for(chunk in module.oldChunks) {
                    if(ignoreSelf && chunk == ChunkPos(pos))
                        continue

                    if(chunk.getChebyshevDistance(current) <= nearest.getChebyshevDistance(current))
                        nearest = chunk
                }

                return nearest
            }
        }

        fun getNearestNewChunk(pos: BlockPos, ignoreSelf: Boolean): ChunkPos {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return ChunkPos(BlockPos(0, 0, 0))
            synchronized(module.newChunks) {
                if (module.newChunks.isEmpty())
                    return ChunkPos(BlockPos(0, 0, 0))

                val current = ChunkPos(pos)
                var nearest = module.newChunks.first()
                for (chunk in module.newChunks) {
                    if (ignoreSelf && chunk == ChunkPos(pos))
                        continue

                    if (chunk.getChebyshevDistance(current) <= nearest.getChebyshevDistance(current))
                        nearest = chunk
                }

                return nearest
            }
        }

        // Return 'true' if New Chunks is disabled
        fun isNewChunk(chunkPos: ChunkPos): Boolean {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return true
            synchronized(module.newChunks) {
                return module.newChunks.contains(chunkPos)
            }
        }

        fun isOldChunk(chunkPos: ChunkPos): Boolean {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(module == null || !module.isActive) return true
            synchronized(module.oldChunks) {
                return module.oldChunks.contains(chunkPos)
            }
        }

        fun isInNewChunk(pos: BlockPos): Boolean {
            return isNewChunk(ChunkPos(pos))
        }

        fun isInOldChunk(pos: BlockPos): Boolean {
            return isOldChunk(ChunkPos(pos))
        }

    }
}
