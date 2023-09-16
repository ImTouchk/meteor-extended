package net.imtouchk.meteorextended.modules

import net.imtouchk.meteorextended.MeteorExtendedAddon
import meteordevelopment.meteorclient.systems.modules.Module
import net.imtouchk.meteorextended.PathUtils

class Debugger : Module(MeteorExtendedAddon.CATEGORY, "meteor-extended-debugger", "Debugger") {
    override fun onActivate() {
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
        toggle()
    }

    override fun onDeactivate() {

    }
}
