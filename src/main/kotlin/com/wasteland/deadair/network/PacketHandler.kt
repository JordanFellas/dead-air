package com.wasteland.deadair.network

import com.wasteland.deadair.DeadAir
import com.wasteland.deadair.network.packets.PacketRadioReceive
import com.wasteland.deadair.network.packets.PacketRadioSquelch
import com.wasteland.deadair.network.packets.PacketRadioStatic
import com.wasteland.deadair.network.packets.PacketRadioStatus
import com.wasteland.deadair.network.packets.PacketRadioTransmit
import com.wasteland.deadair.network.packets.PacketSetRadioSettings
import com.wasteland.deadair.network.packets.PacketSongUpload
import com.wasteland.deadair.network.packets.PacketStationDisc
import com.wasteland.deadair.network.packets.PacketStationNotice
import com.wasteland.deadair.network.packets.PacketStationPlaylist
import com.wasteland.deadair.network.packets.PacketStationInfo
import com.wasteland.deadair.network.packets.PacketStationSettings
import com.wasteland.deadair.network.packets.PacketTunerSettings
import com.wasteland.deadair.network.packets.PacketSwapRadioBattery
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

@EventBusSubscriber(modid = DeadAir.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object PacketHandler {

    @SubscribeEvent
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("5")

        registrar.playToServer(
            PacketRadioTransmit.TYPE,
            PacketRadioTransmit.STREAM_CODEC,
            PacketRadioTransmit::handle
        )

        registrar.playToClient(
            PacketRadioReceive.TYPE,
            PacketRadioReceive.STREAM_CODEC,
            PacketRadioReceive::handle
        )

        registrar.playToServer(
            PacketSetRadioSettings.TYPE,
            PacketSetRadioSettings.STREAM_CODEC,
            PacketSetRadioSettings::handle
        )

        registrar.playToServer(
            PacketSwapRadioBattery.TYPE,
            PacketSwapRadioBattery.STREAM_CODEC,
            PacketSwapRadioBattery::handle
        )

        registrar.playToClient(
            PacketRadioStatic.TYPE,
            PacketRadioStatic.STREAM_CODEC,
            PacketRadioStatic::handle
        )

        registrar.playToClient(
            PacketRadioStatus.TYPE,
            PacketRadioStatus.STREAM_CODEC,
            PacketRadioStatus::handle
        )

        registrar.playToClient(
            PacketRadioSquelch.TYPE,
            PacketRadioSquelch.STREAM_CODEC,
            PacketRadioSquelch::handle
        )

        // Radio stations
        registrar.playToClient(PacketStationDisc.TYPE, PacketStationDisc.STREAM_CODEC, PacketStationDisc::handle)
        registrar.playToClient(PacketStationInfo.TYPE, PacketStationInfo.STREAM_CODEC, PacketStationInfo::handle)
        registrar.playToServer(PacketStationSettings.TYPE, PacketStationSettings.STREAM_CODEC, PacketStationSettings::handle)
        registrar.playToServer(PacketTunerSettings.TYPE, PacketTunerSettings.STREAM_CODEC, PacketTunerSettings::handle)
        registrar.playToServer(PacketSongUpload.TYPE, PacketSongUpload.STREAM_CODEC, PacketSongUpload::handle)
        registrar.playToClient(PacketStationNotice.TYPE, PacketStationNotice.STREAM_CODEC, PacketStationNotice::handle)
        registrar.playToClient(PacketStationPlaylist.TYPE, PacketStationPlaylist.STREAM_CODEC, PacketStationPlaylist::handle)
    }
}
