package net.imtouchk.meteorextended.modules

import meteordevelopment.meteorclient.events.packets.InventoryEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.settings.ItemListSetting
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.Utils
import meteordevelopment.meteorclient.utils.entity.EntityUtils
import meteordevelopment.meteorclient.utils.world.BlockUtils
import meteordevelopment.orbit.EventHandler
import net.imtouchk.meteorextended.InteropUtils
import net.imtouchk.meteorextended.MeteorExtendedAddon
import net.imtouchk.meteorextended.PathUtils
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.system.measureTimeMillis


class BaseRaider : Module(MeteorExtendedAddon.CATEGORY, "Base Raider", "Tries to find bases") {
    private val sgGeneral = settings.defaultGroup

    // TODO: Implement this check
    private val maxSearchArea = sgGeneral.add(IntSetting.Builder()
        .name("max-search-area")
        .description("How far away from spawn should it search (in thousands)")
        .defaultValue(100)
        .build()
    )

    private val chunksToAnalyzePerTick = sgGeneral.add(IntSetting.Builder()
        .name("chunks-to-analyze-per-tick")
        .description("How many chunks to analyze each tick (more = possibly worse performance/stuttering)")
        .defaultValue(2)
        .build()
    )

    private val searchThreshold = sgGeneral.add(IntSetting.Builder()
        .name("loot-search-threshold")
        .description("Start looking at a chunk when the probability of a base being there is equal or above this number")
        .sliderRange(1, 100)
        .defaultValue(70)
        .build()
    )

    private val resetOnDisable = sgGeneral.add(BoolSetting.Builder()
        .name("reset-on-disable")
        .description("Reset all data when module is disabled.")
        .defaultValue(false)
        .build()
    )

    private val requireNewChunks = sgGeneral.add(BoolSetting.Builder()
        .name("require-new-chunks")
        .description("Require NewChunks module to be enabled.")
        .defaultValue(true)
        .build()
    )

    private val chatAnnouncer = sgGeneral.add(BoolSetting.Builder()
        .name("chat-announcer")
        .description("Announce in the chat when you have found loot")
        .defaultValue(false)
        .build()
    )

    private val desirableItems = sgGeneral.add(ItemListSetting.Builder()
        .name("desirable-items")
        .description("Items to look out for when searching chests")
        .defaultValue(listOf(
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.BEACON,
            Items.ENDER_CHEST,

            Items.SHULKER_BOX,
            Items.BLACK_SHULKER_BOX,
            Items.GREEN_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX,
            Items.BLACK_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,
            Items.CYAN_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX,
            Items.LIME_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,
            Items.RED_SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX,
            Items.PINK_SHULKER_BOX,
            Items.ORANGE_SHULKER_BOX
        ))
        .build()
    )

    private val debugMode = sgGeneral.add(BoolSetting.Builder()
        .name("debug-mode")
        .description("Display additional messages")
        .defaultValue(true)
        .build()
    )

    private val chunkAnalyzer = ChunkAnalyzer(mc)

    // Design-wise, a Map would have been preferred here.
    // However, it would probably be slower (and more annoying) to loop through the elements so I chose a MutableList instead
    // TODO: Garbage collection on both of these
    private val chunksAnalyzed = mutableListOf<ChunkAnalyzer.QueryResults>()
    private val chunksWalkedThrough = mutableListOf<ChunkPos>()
    private val chunksSearched = mutableListOf<ChunkPos>()
    private var chunkToSearch: ChunkPos? = null


    private fun getPlayersNearby(): List<Entity> {
        val players = mutableListOf<Entity>()
        for(entity in mc.world?.entities!!) {
            if(entity !is PlayerEntity) continue
            if(entity == mc.player) continue
            if(entity == mc.cameraEntity && mc.options.perspective.isFirstPerson) continue
            if(EntityUtils.isInRenderDistance(entity))
                players.add(entity)
        }
        return players
    }

    private fun arePlayersNearby(): Boolean {
        return getPlayersNearby().isNotEmpty()
    }

    private fun roam() {
        if(chunksAnalyzed.isEmpty() || chunkToSearch != null)
            return

        val currentGoal = PathUtils.getCurrentGoal()
        if(currentGoal != BlockPos.ZERO && ChunkPos(currentGoal) != mc.player?.chunkPos)
            return

        var bestChunk = chunksAnalyzed.first()
        var bestSurrounding = PathUtils.getSurroundingOldChunks(bestChunk.pos)
        for(chunk in chunksAnalyzed) {
            if(chunk.pos == mc.player?.chunkPos || chunksWalkedThrough.contains(chunk.pos))
                continue

            val surroundingOldChunks = PathUtils.getSurroundingOldChunks(chunk.pos)
            if(surroundingOldChunks >= bestSurrounding) {
                bestChunk = chunk
                bestSurrounding = surroundingOldChunks
            }
        }

        if(bestSurrounding == 0 && !PathUtils.isExploring()) {
            PathUtils.explore(mc.player?.pos!!)
            info("No old chunks found. Using Baritone roam feature...")
            return
        }

        val goal = bestChunk.pos.getCenterAtY(mc.player?.blockY!!)
        if(PathUtils.getCurrentGoal() == goal)
            return

        PathUtils.setGoal(goal)
        debugInfo("BaseRaider.roam: Determined chunk ${bestChunk.pos} to be the best, with $bestSurrounding old chunks neighboring it")
        info("Roaming...")
    }

    private fun analyzeNearbyChunks() {
        val chunks = Utils.chunks()
        var chunksAnalyzed = 0
        chunksLoop@ for(chunk in chunks) {
            for(analyzed in this.chunksAnalyzed)
                if(analyzed.pos == chunk.pos)
                    continue@chunksLoop

            lateinit var result: ChunkAnalyzer.QueryResults
            val elapsed = measureTimeMillis { result = chunkAnalyzer.queryChunk(chunk.pos) }
            this.chunksAnalyzed.add(result)
            debugInfo("BaseRaider.analyzeNearbyChunks: Analyzed ${chunk.pos} in ${elapsed}ms. Base probability: ${result.baseProbability}")

            chunksAnalyzed++
            if(chunksAnalyzed >= chunksToAnalyzePerTick.get())
                return
        }
    }

    private fun searchChunk() {
        if(chunkToSearch == null) {
            debugInfo("BaseRaider.searchChunk: Function called with null chunkToCheck value")
            return
        }

        val chunkData = getChunkQueryResult()
        if(chunkData == null) {
            debugInfo("BaseRaider.searchChunk: Could not find query results for chunk $chunkToSearch")
            return
        }

        val goal = PathUtils.getCurrentGoal()
        if(ChunkPos(goal) == chunkToSearch) {
            if(!goal.isWithinDistance(mc.player?.pos, 5.0)) {
                return
            }

            for(container in chunkData.potentialLoot) {
                if(container.pos == goal) {
                    if(container is ShulkerBoxBlockEntity) {
                        // TODO: Actually break it (and pick it up from the ground!)
                        info("Not implemented")
                    } else if(container is ChestBlockEntity) {
                        val block = BlockHitResult(
                            Vec3d(goal.x.toDouble(), goal.y.toDouble(), goal.z.toDouble()),
                            Direction.UP,
                            goal,
                            false
                        )

                        BlockUtils.interact(block, Hand.MAIN_HAND, true)
                    }

                    chunkData.potentialLoot.remove(container)
                    break
                }
            }

            // TODO: Implement item drop pickup
        }

        goToNextLoot()
    }

    private fun goToNextLoot() {
        if(chunkToSearch == null) {
            debugInfo("BaseRaider.goToNextLoot: Function called, but chunkToSearch was null")
            return
        }

        val chunkData = getChunkQueryResult()
        if(chunkData == null) {
            debugInfo("BaseRaider.goToNextLoot: Could not find query results for chunk $chunkToSearch")
            return
        }

        if(chunkData.potentialLoot.isEmpty() && chunkData.itemDrops.isEmpty()) {
            info("Finished searching chunk")
            chunksSearched.add(chunkToSearch!!)
            chunkToSearch = null
        } else if(chunkData.potentialLoot.isEmpty()) {
            val itemDrop = chunkData.itemDrops.first()
            PathUtils.setGoal(itemDrop.blockPos)
            debugInfo("BaseRaider.goToNextLoot: Going to item drop at ${itemDrop.blockPos}")
            // TODO: Handle pickup data
        } else {
            val potentialLoot = chunkData.potentialLoot.first()
            PathUtils.setGoal(potentialLoot.pos)
            debugInfo("BaseRaider.goToNextLoot: Going to potential valuable container at ${potentialLoot.pos}")
        }
    }

    private fun getChunkQueryResult(): ChunkAnalyzer.QueryResults? {
        for(chunk in chunksAnalyzed)
            if(chunk.pos == chunkToSearch)
                return chunk

        return null
    }

    private fun checkForBases() {
        if(chunkToSearch != null) {
            searchChunk()
            return
        }

        for(chunk in chunksAnalyzed) {
            if(chunk.baseProbability >= searchThreshold.get() && !chunksSearched.contains(chunk.pos)) {
                if(chunk.potentialLoot.isEmpty() && chunk.itemDrops.isEmpty()) {
                    chunksSearched.add(chunk.pos)
                    debugInfo("BaseRaider.checkForBases: Chunk meets search threshold (probability of being a base: ${chunk.baseProbability}), but there are no potential valuables. Skipping...")
                } else {
                    chunkToSearch = chunk.pos
                    debugInfo("BaseRaider.checkForBases: Chunk ${chunk.pos} meets search threshold (probability of being a base: ${chunk.baseProbability})")
                    info("Found chunk worth checking.")
                }
            }
        }
    }

    @EventHandler
    private fun onInventoryOpen(event: InventoryEvent) {
        // TODO: Either handle items in priority (i.e. shulkers over anything else) or do inventory cleanups
        val contents = event.packet.contents
        var foundValuables = false
        for(stack in contents) {
            if(desirableItems.get().contains(stack.item)) {
                mc.player?.inventory?.insertStack(stack)
                foundValuables = true
            }
        }

        info("Chest searched. Found valuables: ${if (foundValuables) "yes" else "no"})")
        if(chatAnnouncer.get() && foundValuables) {
            // TODO: send chat message (how the fuck do I do it?)
        }
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        assert(mc.world != null)

        if(requireNewChunks.get()) {
            val module = InteropUtils.getMeteorModule(NewChunks::class.java)
            if(!module.isActive) {
                module.toggle()
                info("Enabled NewChunks module.")
            }
        }

        if(!chunksWalkedThrough.contains(mc.player?.chunkPos))
            chunksWalkedThrough.add(mc.player?.chunkPos!!)

        analyzeNearbyChunks()
        roam()
        checkForBases()

        // TODO: Inventory cleanup
        // Remove useless items
//        val inventory = mc.player?.inventory!!
//        for(stack in inventory.main) {
//            if(initialInventoryState.contains(stack.item)) continue
//            if(isShulkerBox(stack.item)) continue
//            if(otherDesirableItems.contains(stack.item)) continue
//            mc.player?.dropItem(stack, false)
//        }

        // Player check
        if(arePlayersNearby()) {
            // TODO: Disconnect
        }
    }

    override fun onActivate() {
//        val inventory = mc.player?.inventory!!
//        for(stack in inventory.main) {
//            initialInventoryState.add(stack.item)
//        }

        if(debugMode.get()) {
            info("Debugger data")

            info("PathUtils.hasActiveJob: ${PathUtils.hasActiveJob()}")

            info("PathUtils.getNearbyOldChunks: ${PathUtils.getNearbyOldChunks()}")
            info("PathUtils.getNearestOldChunk(): ${PathUtils.getNearestOldChunk(mc.player?.blockPos!!, false)}")
            info("PathUtils.getNearestOldChunk(ignoreSelf): ${PathUtils.getNearestOldChunk(mc.player?.blockPos!!, true)}")

            info("PathUtils.getNearbyNewChunks: ${PathUtils.getNearbyNewChunks()}")
            info("PathUtils.getNearestNewChunk(): ${PathUtils.getNearestNewChunk(mc.player?.blockPos!!, false)}")
            info("PathUtils.getNearestNewChunk(ignoreSelf): ${PathUtils.getNearestNewChunk(mc.player?.blockPos!!, true)}")

            info("PathUtils.isInOldChunk(@player): ${PathUtils.isInOldChunk(mc.player?.blockPos!!)}")
            info("PathUtils.isInNewChunk(@player): ${PathUtils.isInNewChunk(mc.player?.blockPos!!)}")

            info("-----------------")
        }

        info("BaseRaider module is now active")
    }

    override fun onDeactivate() {
        chunkToSearch = null

        PathUtils.stopAny()
        if(resetOnDisable.get()) {
            chunksSearched.clear()
            chunksWalkedThrough.clear()
            chunksAnalyzed.clear()
        }

        info("BaseRaider module has been deactivated")
    }

    private fun debugInfo(message: String) {
        println(message)
        if(debugMode.get())
            info(message)
    }
}
