package com.thomas.espdoorbell.doorbell.streaming.udp

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.pipeline.DeviceStreamManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.SocketAddress
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class UdpInboundHandler(
    private val deviceService: DeviceService,
    private val deviceStreamManager: DeviceStreamManager
) {
    private val log = LoggerFactory.getLogger(UdpInboundHandler::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val sessions = ConcurrentHashMap<SocketAddress, SessionState>()
    private val jitterBuffers = ConcurrentHashMap<UUID, JitterBuffer>()
    private val fragmentAssemblers = ConcurrentHashMap<UUID, FragmentAssembler>()

    data class SessionState(
        val remoteAddress: SocketAddress,
        var deviceId: UUID? = null,
        var deviceIdentifier: String? = null,
        var authenticated: Boolean = false
    )

    fun handlePacket(remoteAddress: SocketAddress, data: ByteArray, sendResponse: (ByteArray) -> Unit) {
        val packet = UdpStreamPacket.parse(data)
        if (packet == null) {
            log.warn("Malformed packet from {}: invalid format", remoteAddress)
            return
        }

        val session = sessions.computeIfAbsent(remoteAddress) { SessionState(remoteAddress) }

        when (packet.type) {
            UdpStreamPacket.TYPE_AUTH -> handleAuth(session, packet, sendResponse)
            UdpStreamPacket.TYPE_VIDEO -> handleMediaFrame(session, packet)
            UdpStreamPacket.TYPE_AUDIO -> handleMediaFrame(session, packet)
            UdpStreamPacket.TYPE_CONTROL -> handleControl(session, packet)
            else -> log.warn("Unknown packet type 0x{} from {}", packet.type.toString(16), remoteAddress)
        }
    }

    private fun handleAuth(session: SessionState, packet: UdpStreamPacket, sendResponse: (ByteArray) -> Unit) {
        if (packet.payload.size < 96) {
            log.warn("Auth packet too small from {}", session.remoteAddress)
            sendResponse(UdpStreamPacket.createControlPacket(UdpStreamPacket.CTRL_AUTH_FAIL))
            return
        }

        val deviceIdBytes = packet.payload.sliceArray(0 until 32)
        val deviceKeyBytes = packet.payload.sliceArray(32 until 96)

        val deviceIdentifier = String(deviceIdBytes, StandardCharsets.UTF_8).trim('\u0000')
        val deviceKey = String(deviceKeyBytes, StandardCharsets.UTF_8).trim('\u0000')

        try {
            val isValid = kotlinx.coroutines.runBlocking {
                deviceService.verifyDeviceKey(deviceIdentifier, deviceKey)
            }
            if (isValid) {
                try {
                    val device = kotlinx.coroutines.runBlocking {
                        deviceService.getDeviceEntityByIdentifier(deviceIdentifier)
                    }
                    session.deviceId = device.id
                    session.deviceIdentifier = deviceIdentifier
                    session.authenticated = true

                    kotlinx.coroutines.runBlocking {
                        deviceStreamManager.registerInbound(device.id!!, session.remoteAddress.toString())
                    }
                    jitterBuffers[device.id!!] = JitterBuffer()
                    fragmentAssemblers[device.id] = FragmentAssembler()

                    log.info("Device {} authenticated successfully from {}", deviceIdentifier, session.remoteAddress)
                    sendResponse(UdpStreamPacket.createControlPacket(UdpStreamPacket.CTRL_AUTH_OK))
                } catch (_: Exception) {
                    log.warn("Unknown device ID: {}", deviceIdentifier)
                    session.authenticated = false
                    sendResponse(UdpStreamPacket.createControlPacket(UdpStreamPacket.CTRL_AUTH_FAIL))
                }
            } else {
                log.warn("Auth failed for device {}: invalid key", deviceIdentifier)
                session.authenticated = false
                sendResponse(UdpStreamPacket.createControlPacket(UdpStreamPacket.CTRL_AUTH_FAIL))
            }
        } catch (e: Exception) {
            log.error("Auth error for device {} from {}", deviceIdentifier, session.remoteAddress, e)
            session.authenticated = false
            sendResponse(UdpStreamPacket.createControlPacket(UdpStreamPacket.CTRL_AUTH_FAIL))
        }
    }

    private fun handleMediaFrame(session: SessionState, packet: UdpStreamPacket) {
        if (!session.authenticated) {
            log.warn("Received media frame from unauthenticated session {}", session.remoteAddress)
            return
        }

        val deviceId = session.deviceId ?: return
        val assembler = fragmentAssemblers[deviceId] ?: return
        val jitterBuffer = jitterBuffers[deviceId] ?: return

        val fullPayload = assembler.assemble(packet) ?: return

        val readyPackets = when (packet.type) {
            UdpStreamPacket.TYPE_VIDEO -> jitterBuffer.insertVideo(
                UdpStreamPacket(
                    packet.magic, packet.type, packet.flags, 0,
                    packet.pts, packet.seq, fullPayload
                )
            )
            UdpStreamPacket.TYPE_AUDIO -> jitterBuffer.insertAudio(
                UdpStreamPacket(
                    packet.magic, packet.type, packet.flags, 0,
                    packet.pts, packet.seq, fullPayload
                )
            )
            else -> emptyList()
        }

        for (readyPacket in readyPackets) {
            when (readyPacket.type) {
                UdpStreamPacket.TYPE_VIDEO -> {
                    deviceStreamManager.feedVideoFrame(deviceId, readyPacket.payload, readyPacket.pts)
                }
                UdpStreamPacket.TYPE_AUDIO -> {
                    deviceStreamManager.feedAudioFrame(deviceId, readyPacket.payload, readyPacket.pts)
                }
            }
        }
    }

    private fun handleControl(session: SessionState, packet: UdpStreamPacket) {
        val controlType = if (packet.payload.isNotEmpty()) packet.payload[0] else packet.flags

        when (controlType) {
            UdpStreamPacket.CTRL_KEEPALIVE -> {}
            UdpStreamPacket.CTRL_STREAM_END -> {
                session.deviceId?.let { deviceId ->
                    scope.launch {
                        try {
                            deviceStreamManager.unregisterInbound(deviceId, session.remoteAddress.toString())
                            jitterBuffers.remove(deviceId)?.reset()
                            fragmentAssemblers.remove(deviceId)
                            log.info("Stream ended for device {} from {}", session.deviceIdentifier, session.remoteAddress)
                        } catch (e: Exception) {
                            log.error("Error unregistering device {}", deviceId, e)
                        }
                    }
                }
                removeSession(session.remoteAddress)
            }
            else -> log.warn("Unknown control type 0x{} from {}", controlType.toString(16), session.remoteAddress)
        }
    }

    fun removeSession(remoteAddress: SocketAddress) {
        sessions.remove(remoteAddress)?.let { session ->
            session.deviceId?.let { deviceId ->
                jitterBuffers.remove(deviceId)?.reset()
                fragmentAssemblers.remove(deviceId)
                scope.launch {
                    try {
                        deviceStreamManager.unregisterInbound(deviceId, remoteAddress.toString())
                    } catch (e: Exception) {
                        log.error("Error cleaning up session for device {}", deviceId, e)
                    }
                }
            }
        }
    }
}

