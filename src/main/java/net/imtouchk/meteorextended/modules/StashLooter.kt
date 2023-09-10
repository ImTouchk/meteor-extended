package net.imtouchk.meteorextended.modules

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalGetToBlock
import meteordevelopment.meteorclient.events.packets.InventoryEvent
import meteordevelopment.meteorclient.events.world.TickEvent
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.EnumSetting
import meteordevelopment.meteorclient.settings.IntSetting
import meteordevelopment.meteorclient.systems.modules.Categories
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.Utils
import meteordevelopment.meteorclient.utils.entity.EntityUtils
import meteordevelopment.meteorclient.utils.world.BlockUtils
import meteordevelopment.orbit.EventHandler
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.imtouchk.meteorextended.MeteorExtendedAddon
import net.imtouchk.meteorextended.PathUtils
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d


class StashLooter : Module(MeteorExtendedAddon.CATEGORY, "Stash Looter", "For the lazy fucks") {
    private val sgGeneral = settings.defaultGroup
    private val cowerIfFound = sgGeneral.add(BoolSetting.Builder()
        .name("cower-if-found")
        .description("Avoid players at all costs (when disabled, they are completely ignored)")
        .defaultValue(true)
        .build()
    )
    private val maxSearchArea = sgGeneral.add(
        IntSetting.Builder()
        .name("max-search-area")
        .description("How far away from spawn should it search")
        .defaultValue(100_000)
        .build()
    )
    private val debugMode = sgGeneral.add(BoolSetting.Builder()
        .name("debug-mode")
        .description("Display additional messages")
        .defaultValue(true)
        .build()
    )
    private val otherDesirableItems = listOf<Item>(
        Items.ENCHANTED_GOLDEN_APPLE,
        Items.BEACON,
        Items.ENDER_CHEST
    )
    private enum class StopAction {
        Disconnect,
        Disable
    }

    private enum class BotState {
        Disabled,
        Roaming,
        Checking,
        Cowering,
        PickingDrop,
        GoingToOldChunks,
    }

    private val stopAction = StopAction.Disconnect

    private var currentState = BotState.Disabled
    private var chestsToCheck = mutableListOf<BlockPos>()
    private var checkedChests = mutableListOf<BlockPos>()
    private var initialInventoryState = mutableListOf<Item>()

    // TODO: Check if this shit actually works lol
    private fun runFromPlayers() {
        val players = mc.player?.clientWorld?.players ?: return
        val botPos = mc.player?.pos ?: return

        val averageDist = Vec3d(0.0, 0.0, 0.0)
        for(player in players) {
            val playerPos = player.pos
            val botPlayerDis = playerPos.subtract(botPos)
            averageDist.add(botPlayerDis)
        }
        averageDist.multiply((1 / players.size).toDouble())

        val bestOpposite = averageDist.negate()
            .normalize()
            .multiply(100.0)

        PathUtils.stopAny()
        val goalProcess = BaritoneAPI.getProvider().primaryBaritone.customGoalProcess
        val goal = GoalGetToBlock(BlockPos(
            (botPos.x + bestOpposite.x).toInt(),
            (botPos.y + bestOpposite.y).toInt(),
            (botPos.z + bestOpposite.z).toInt(),
        ))
        goalProcess.setGoalAndPath(goal)

        currentState = BotState.Cowering
        debugInfo("Current state: COWERING (Destination: ${goal.x}, ${goal.y}, ${goal.z})")
    }

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

    private fun isChestDesirable(entity: BlockEntity): Boolean {
        if(PathUtils.isInNewChunk(entity.pos)) return false
        return true
        // TODO: add more, for example avoid search mineshaft chests
    }

    private fun lookForNewChests() {
        for(entity in Utils.blockEntities()) {
            if(!(entity is ChestBlockEntity || entity is ShulkerBoxBlockEntity)) continue
            if(checkedChests.contains(entity.pos) || chestsToCheck.contains(entity.pos)) continue
            if(!isChestDesirable(entity)) continue
            chestsToCheck.add(entity.pos)
        }

        debugInfo("Found ${chestsToCheck.size} chests to check.")
    }

    private fun getNearestChest(): BlockPos {
        var nearestChest = chestsToCheck[0]
        for(chest in chestsToCheck) {
            if(distanceToBlock(chest) < distanceToBlock(nearestChest))
                nearestChest = chest
        }
        return nearestChest
    }

    private fun goToNearestChest() {
        val chest = getNearestChest()

        PathUtils.setGoal(chest)
        currentState = BotState.Checking
        debugInfo("Current state: CHECKING. (Destination: ${chest.x}, ${chest.y}, ${chest.z})")
    }

    private fun roam() {
        // TODO: check boundaries

        PathUtils.explore(botPosition())
        currentState = BotState.Roaming
        debugInfo("Current state: ROAMING")
    }

    fun onShulkerPickedUp() {
        debugInfo("Shulker box picked up")
        roam()
    }

    @EventHandler
    private fun onInventoryOpen(event: InventoryEvent) {
        // Could have only one for loop here but shulkers are higher priority than anything else
        for(stack in event.packet.contents)
            if(isShulkerBox(stack.item))
                mc.player?.inventory?.insertStack(stack)

        for(stack in event.packet.contents)
            if(otherDesirableItems.contains(stack.item))
                mc.player?.inventory?.insertStack(stack)
    }

    @EventHandler
    private fun onTick(event: TickEvent.Pre) {
        assert(mc.world != null)

        if(currentState == BotState.Disabled)
            return

        when(currentState) {
            BotState.Roaming -> {
                if(PathUtils.isInNewChunk(BlockPos(
                        botPosition().x.toInt(),
                        botPosition().y.toInt(),
                        botPosition().z.toInt()
                ))) {
                    // TODO: go away from new chunks
                }

                if(chestsToCheck.isEmpty())
                    lookForNewChests()
                else
                    goToNearestChest()
            }
            BotState.PickingDrop -> {
                val entities = mc.world?.entities!!
                for(entity in entities) {
                    if(entity !is ItemEntity || entity.distanceTo(mc.player) > 5) continue

                    val item = entity as ItemEntity
                    if(!isShulkerBox(item.stack.item)) continue

                    PathUtils.setGoal(BlockPos(
                        entity.pos.x.toInt(),
                        entity.pos.y.toInt(),
                        entity.pos.z.toInt()
                    ))
                    currentState = BotState.PickingDrop
                }
            }
            BotState.Checking -> {
                val chest = getNearestChest()
                if (distanceToBlock(chest) <= 5) {
                    val entity = mc.player?.clientWorld?.getBlockEntity(chest)
                    checkedChests.add(chest)
                    chestsToCheck.remove(chest)

                    if(entity is ShulkerBoxBlockEntity) {
                        debugInfo("Shulker box!")

                        if(!BlockUtils.canBreak(entity.pos)) return
                        BlockUtils.breakBlock(entity.pos, true)
                        currentState = BotState.PickingDrop
                    } else {
                        val chestPos = entity?.pos!!
                        val block = BlockHitResult(
                            Vec3d(chestPos.x.toDouble(), chestPos.y.toDouble(), chestPos.z.toDouble()),
                            Direction.UP,
                            chestPos,
                            false
                        )
                        BlockUtils.interact(block, Hand.MAIN_HAND, true)

                    }
                    debugInfo("Chest checked")
                    roam()
                } else {
                    val goalProcess = BaritoneAPI.getProvider().primaryBaritone.customGoalProcess
                    if(!goalProcess.isActive) goToNearestChest()
                    val goal = goalProcess.goal as GoalGetToBlock
                    if(!chestsToCheck.contains(BlockPos(goal.x, goal.y, goal.z)))
                        goToNearestChest()
                }
            }
            else -> {}
        }

        // Remove useless items
//        val inventory = mc.player?.inventory!!
//        for(stack in inventory.main) {
//            if(initialInventoryState.contains(stack.item)) continue
//            if(isShulkerBox(stack.item)) continue
//            if(otherDesirableItems.contains(stack.item)) continue
//            mc.player?.dropItem(stack, false)
//        }

        // Player check
        if(arePlayersNearby())
            stop()
    }

    private fun stop() {
        PathUtils.stopAny()
        when(stopAction) {
            StopAction.Disable -> {
                currentState = BotState.Disabled
                toggle()
            }
            StopAction.Disconnect -> {
                currentState = BotState.Disabled
                mc.world?.disconnect()
                toggle()
            }
            else -> throw Exception()
        }
    }

    override fun onActivate() {
        val inventory = mc.player?.inventory!!
        for(stack in inventory.main) {
            initialInventoryState.add(stack.item)
        }

        roam()
    }

    override fun onDeactivate() {
        PathUtils.stopAny()
        initialInventoryState.clear()
        currentState = BotState.Disabled
    }

    private fun debugInfo(message: String) {
        if(debugMode.get())
            info(message)
    }

    private fun botPosition(): Vec3d {
        return mc.player?.pos!!
    }

    private fun distanceToBlock(position: BlockPos): Double {
        return botPosition().distanceTo(Vec3d(
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble()
        ))
    }

    companion object {
        @JvmStatic public fun isShulkerBox(item: Item): Boolean {
            val shulkerBlockItems = listOf<Item>(
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
            )

            return shulkerBlockItems.contains(item)
        }
    }
}
