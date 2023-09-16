package net.imtouchk.meteorextended.modules

import meteordevelopment.meteorclient.utils.Utils
import net.imtouchk.meteorextended.PathUtils
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos

class ChunkAnalyzer(private val mc: MinecraftClient) {
    val BlockProbabilityMultipliers = mapOf(
        Blocks.OBSIDIAN.defaultState to 0.15f,
        Blocks.REDSTONE_LAMP.defaultState to 0.75f,
        Blocks.REPEATER.defaultState to 0.75f,
        Blocks.COMPARATOR.defaultState to 0.75f,
        Blocks.WHITE_CONCRETE.defaultState to 0.25f,
        Blocks.DRAGON_EGG.defaultState to 20f,
        Blocks.CRAFTING_TABLE.defaultState to 0.1f,
        Blocks.FURNACE.defaultState to 0.1f,
    )

    object ContainerProbabilityMultipliers {
        const val ENDER_CHEST = 0.1f
        const val SHULKER_BOX = 100f
        const val CHEST = 1f
        const val NEW_CHUNKS = 80f
    }

    object ChestProbabilityMultipliers {
        const val NEAR_MONSTER_SPAWNER = 1f / 8f
        const val BELOW_OLD_BEDROCK_HEIGHT = 1f / 4f
        const val DOUBLE_CHEST = 2f
        const val NEAR_HOPPER = 3f
    }

    object Settings {
        const val NEW_CHUNKS_THRESHOLD = 4 // How many new chunks are too many?
    }

    class QueryResults(
        val pos: ChunkPos,
        val baseProbability: Float,
        val potentialLoot: MutableList<BlockEntity>,
        val itemDrops: MutableList<ItemEntity>
    )

    fun isBlockWithinRadius(pos: BlockPos, type: Block, radius: Int): Boolean {
        for(z in -radius..radius) {
            for(y in -radius..radius) {
                for(x in -radius..radius) {
                    val block = BlockPos(pos.x + x, pos.y + y, pos.z + z)
                    if(block == pos) continue
                    if(mc.world?.getBlockState(block)?.block?.defaultState == type.defaultState)
                        return true
                }
            }
        }
        return false
    }

    fun playerChestProbability(entity: ChestBlockEntity): Float {
        var multiplier = 1f
        if(isBlockWithinRadius(entity.pos, Blocks.SPAWNER, 10)) multiplier *= ChestProbabilityMultipliers.NEAR_MONSTER_SPAWNER
        if(entity.pos.y <= 4) multiplier *= ChestProbabilityMultipliers.BELOW_OLD_BEDROCK_HEIGHT
        if(isBlockWithinRadius(entity.pos, Blocks.CHEST, 1)) multiplier *= ChestProbabilityMultipliers.DOUBLE_CHEST
        if(isBlockWithinRadius(entity.pos, Blocks.HOPPER, 5)) multiplier *= ChestProbabilityMultipliers.NEAR_HOPPER
        return ContainerProbabilityMultipliers.CHEST * multiplier
    }

    fun queryChunk(chunkPos: ChunkPos): QueryResults {
        var probability = 0f

        val newChunks = PathUtils.getNearbyNewChunks()
        if(newChunks.size >= Settings.NEW_CHUNKS_THRESHOLD) probability -= ContainerProbabilityMultipliers.NEW_CHUNKS
        if(PathUtils.isNewChunk(chunkPos)) probability -= 100000f

        val chunk = mc.player?.clientWorld?.getChunk(chunkPos.x, chunkPos.z)

        // Check chests and shulkers
        // chunk.blockEntities should've been used instead of Utils.blockEntities() but it seems to always be empty so idk
        val potentialLoot = mutableListOf<BlockEntity>()
        for(entity in Utils.blockEntities()) {
            if(ChunkPos(entity.pos) != chunkPos)
                continue

            if(entity is EnderChestBlockEntity){
                probability += ContainerProbabilityMultipliers.ENDER_CHEST
            }
            else if(entity is ShulkerBoxBlockEntity) {
                probability += ContainerProbabilityMultipliers.SHULKER_BOX
                potentialLoot.add(entity)
            }
            else if(entity is ChestBlockEntity) {
                val chestProbability = playerChestProbability(entity)
                probability += chestProbability
                if(chestProbability >= ContainerProbabilityMultipliers.CHEST)
                    potentialLoot.add(entity)
            }
            else continue
        }

        // TODO: Check item entities

        val beforeBlockAnalysis = probability
        var potentialBlocksFound = 0
        // Check all blocks (probably EXTREMELY slow)
        for(x in 0..15) {
            for(y in 0..383) {
                for(z in 0..15) {
                    val blockPos = chunkPos.getBlockPos(x, y, z)
                    val blockState = chunk?.getBlockState(blockPos)

                    val multiplier = BlockProbabilityMultipliers
                                        .getOrDefault(blockState?.block?.defaultState, 0f)

                    probability += multiplier
                    if(multiplier != 0f)
                        potentialBlocksFound++
                }
            }
        }

        // Cap values between [0, 100] to be more user-friendly
        if(probability > 100f) probability = 100f
        if(probability < 0f) probability = 0f
        return QueryResults(chunkPos, probability, potentialLoot, mutableListOf())
    }
}
