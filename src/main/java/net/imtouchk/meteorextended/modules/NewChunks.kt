package net.imtouchk.meteorextended.modules

import meteordevelopment.meteorclient.events.packets.PacketEvent
import meteordevelopment.meteorclient.events.render.Render3DEvent
import meteordevelopment.meteorclient.renderer.ShapeMode
import meteordevelopment.meteorclient.settings.BoolSetting
import meteordevelopment.meteorclient.settings.ColorSetting
import meteordevelopment.meteorclient.settings.IntSetting
import net.imtouchk.meteorextended.MeteorExtendedAddon
import meteordevelopment.meteorclient.systems.modules.Module
import meteordevelopment.meteorclient.utils.render.color.Color
import meteordevelopment.meteorclient.utils.render.color.SettingColor
import meteordevelopment.orbit.EventHandler
import net.minecraft.block.BlockState
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket
import net.minecraft.util.math.*
import net.minecraft.world.chunk.WorldChunk
import java.util.Collections

/*
    DISCLAIMER!! This code is a modified version of meteor-rejects' NewChunks module
    Source: https://github.com/AntiCope/meteor-rejects/blob/master/src/main/java/anticope/rejects/modules/NewChunks.java
 */

class NewChunks : Module(MeteorExtendedAddon.CATEGORY, "new-chunks", "Detects completely new chunks using certain traits of them") {
    private val sgRender = settings.createGroup("Render")

    private val renderEnable = sgRender.add(BoolSetting.Builder()
        .name("enable-rendering")
        .description("Enable visualization of NewChunks module")
        .defaultValue(false)
        .build()
    )

    private val newChunksColor = sgRender.add(ColorSetting.Builder()
        .name("new-chunks-color")
        .description("Color of new chunks")
        .defaultValue(SettingColor(255, 0, 0, 75))
        .build()
    )

    private val oldChunksColor = sgRender.add(ColorSetting.Builder()
        .name("new-chunks-color")
        .description("Color of new chunks")
        .defaultValue(SettingColor(0, 255, 0, 75))
        .build()
    )

    private val renderHeight = sgRender.add(IntSetting.Builder()
        .name("render-height")
        .description("Height at which to render the visualization")
        .defaultValue(0)
        .build()
    )

    private val shapeMode = ShapeMode.Both

    // MAKE SURE TO DO SYNCHRONIZED CHECKS!!

    val newChunks = Collections.synchronizedSet(HashSet<ChunkPos>())
    val oldChunks = Collections.synchronizedSet(HashSet<ChunkPos>())

    private fun render(box: Box, col: Color, shapeMode: ShapeMode, event: Render3DEvent) {
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, col, col, shapeMode, 0)
    }

    @EventHandler
    private fun onRender(event: Render3DEvent) {
        if(!renderEnable.get())
            return

        synchronized(newChunks) {
            for(c in newChunks)
                if(mc.getCameraEntity()?.blockPos?.isWithinDistance(Vec3i(c.startPos.x, c.startPos.y, c.startPos.z), 1024.0)!!)
                    render(Box(c.startPos, c.startPos.add(16, renderHeight.get(), 16)), newChunksColor.get(), shapeMode, event)
        }

        synchronized(oldChunks) {
            for(c in oldChunks)
                if(mc.getCameraEntity()?.blockPos?.isWithinDistance(Vec3i(c.startPos.x, c.startPos.y, c.startPos.z), 1024.0)!!)
                    render(Box(c.startPos, c.startPos.add(16, renderHeight.get(), 16)), oldChunksColor.get(), shapeMode, event)
        }
    }

    @EventHandler
    private fun onReadPacket(event: PacketEvent.Receive) {
        if(event.packet is ChunkDeltaUpdateS2CPacket) {
            val packet = event.packet as ChunkDeltaUpdateS2CPacket
            packet.visitUpdates { pos: BlockPos, state: BlockState ->
                if(!state.fluidState.isEmpty && !state.fluidState.isStill) {
                    val chunkPos = ChunkPos(pos)
                    for(dir in searchDirs) {
                        if(mc.world?.getBlockState(pos.offset(dir))?.fluidState?.isStill!! && !oldChunks.contains(chunkPos)) {
                            newChunks.add(chunkPos)
                            return@visitUpdates
                        }
                    }
                }
            }
        } else if(event.packet is BlockUpdateS2CPacket) {
            val packet = event.packet as BlockUpdateS2CPacket
            if(!packet.state.fluidState.isEmpty && !packet.state.fluidState.isStill) {
                val chunkPos = ChunkPos(packet.pos)
                for (dir in searchDirs) {
                    if (mc.world?.getBlockState(packet.pos.offset(dir))?.fluidState?.isStill!! && !oldChunks.contains(chunkPos)) {
                        newChunks.add(chunkPos)
                        return
                    }
                }
            }
        } else if(event.packet is ChunkDataS2CPacket && mc.world != null) {
            val packet = event.packet as ChunkDataS2CPacket
            val pos = ChunkPos(packet.x, packet.z)
            if(!newChunks.contains(pos) && mc?.world?.chunkManager?.getChunk(packet.x, packet.z) == null) {
                val chunk = WorldChunk(mc.world, pos)
                try { chunk.loadFromPacket(packet.chunkData.sectionsDataBuf, NbtCompound(), packet.chunkData.getBlockEntities(packet.x, packet.z))}
                catch(e: ArrayIndexOutOfBoundsException) { return }

                val topY = mc.world?.topY!!
                val bottomY = mc.world?.bottomY!!
                for(x in 0..15) {
                    for(y in bottomY..topY-1) {
                        for(z in 0..15) {
                            val fluid = chunk.getFluidState(x, y, z)
                            if(!fluid.isEmpty && !fluid.isStill) {
                                oldChunks.add(pos)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val searchDirs = listOf(Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP)
    }
}
