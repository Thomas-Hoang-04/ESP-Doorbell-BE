package com.thomas.espdoorbell.doorbell.streaming.udp

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.pipeline.DeviceStreamManager
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.slf4j.LoggerFactory
import org.snf4j.core.DatagramServerHandler
import org.snf4j.core.SelectorLoop
import org.snf4j.core.handler.AbstractDatagramHandler
import org.snf4j.core.handler.DataEvent
import org.snf4j.core.handler.SessionEvent
import org.snf4j.core.session.DefaultSessionConfig
import org.snf4j.core.session.ISessionConfig
import org.springframework.stereotype.Component
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.DatagramChannel
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

@Component
class UdpDtlsServer(
    private val deviceService: DeviceService,
    private val deviceStreamManager: DeviceStreamManager,
    private val config: StreamingProperties
) {
    private val log = LoggerFactory.getLogger(UdpDtlsServer::class.java)

    private val port: Int get() = config.udp.port
    private val certPath: String get() = config.dtls.certPath
    private val keyPath: String get() = config.dtls.keyPath

    private lateinit var loop: SelectorLoop
    private lateinit var channel: DatagramChannel
    private lateinit var inboundHandler: UdpInboundHandler
    private lateinit var sslContext: SSLContext

    private val activeSessions = ConcurrentHashMap<SocketAddress, DtlsSessionHandler>()

    @PostConstruct
    fun start() {
        log.info("Starting UDP/DTLS server on port {}", port)

        try {
            inboundHandler = UdpInboundHandler(deviceService, deviceStreamManager)
            sslContext = createSslContext()

            loop = SelectorLoop("udp-dtls-loop")
            loop.start()

            channel = DatagramChannel.open()
            channel.configureBlocking(false)
            channel.socket().reuseAddress = true
            channel.bind(InetSocketAddress(port))

            val serverHandler = DtlsDatagramServerHandler()
            loop.register(channel, serverHandler).sync()

            log.info("UDP/DTLS server started successfully on port {}", port)
        } catch (e: Exception) {
            log.error("Failed to start UDP/DTLS server on port {}", port, e)
            throw e
        }
    }

    @PreDestroy
    fun stop() {
        log.info("Stopping UDP/DTLS server")
        try {
            activeSessions.values.forEach { handler ->
                try {
                    handler.session?.close()
                } catch (e: Exception) {
                    log.debug("Error closing session", e)
                }
            }
            activeSessions.clear()

            if (::channel.isInitialized && channel.isOpen) {
                channel.close()
            }
            if (::loop.isInitialized) {
                loop.stop()
                loop.join(5000)
            }
            log.info("UDP/DTLS server stopped")
        } catch (e: Exception) {
            log.error("Error stopping UDP/DTLS server", e)
        }
    }

    private fun createSslContext(): SSLContext {
        return if (certPath.isNotBlank() && keyPath.isNotBlank()) {
            createSslContextFromPem(certPath, keyPath)
        } else {
            log.warn("No DTLS certificates configured - using default context (NOT RECOMMENDED)")
            SSLContext.getInstance("DTLS").apply { init(null, null, null) }
        }
    }

    private fun createSslContextFromPem(certPath: String, keyPath: String): SSLContext {
        log.info("Loading DTLS certificate from {} and key from {}", certPath, keyPath)

        val certs = mutableListOf<X509Certificate>()
        FileReader(certPath).use { reader ->
            val parser = PEMParser(reader)
            var obj: Any?
            while (parser.readObject().also { obj = it } != null) {
                when (val parsed = obj) {
                    is X509CertificateHolder -> {
                        certs.add(JcaX509CertificateConverter().getCertificate(parsed))
                    }
                }
            }
        }

        if (certs.isEmpty()) {
            throw IllegalStateException("No certificates found in $certPath")
        }

        var privateKey: PrivateKey? = null
        FileReader(keyPath).use { reader ->
            val parser = PEMParser(reader)
            var obj: Any?
            while (parser.readObject().also { obj = it } != null) {
                when (val parsed = obj) {
                    is PEMKeyPair -> {
                        privateKey = JcaPEMKeyConverter().getPrivateKey(parsed.privateKeyInfo)
                    }
                    is PrivateKeyInfo -> {
                        privateKey = JcaPEMKeyConverter().getPrivateKey(parsed)
                    }
                }
                if (privateKey != null) break
            }
        }

        if (privateKey == null) {
            throw IllegalStateException("No private key found in $keyPath")
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("server", privateKey, charArrayOf(), certs.toTypedArray())

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, charArrayOf())

        val ctx = SSLContext.getInstance("DTLS")
        ctx.init(kmf.keyManagers, null, null)

        log.info("DTLS SSLContext initialized successfully with {} certificate(s)", certs.size)
        return ctx
    }

    private fun createDtlsEngine(): SSLEngine {
        return sslContext.createSSLEngine().apply {
            useClientMode = false
            needClientAuth = false
            wantClientAuth = false
            val dtlsProtocols = supportedProtocols.filter { it.startsWith("DTLS") }
            if (dtlsProtocols.isNotEmpty()) {
                enabledProtocols = dtlsProtocols.toTypedArray()
            }
            log.debug("Created DTLS engine with protocols: {}", enabledProtocols.toList())
        }
    }

    private val dtlsSessionConfig = object : DefaultSessionConfig() {
        override fun createSSLEngine(clientMode: Boolean): SSLEngine = createDtlsEngine()
    }

    private inner class DtlsDatagramServerHandler : DatagramServerHandler(
        { remoteAddress -> DtlsSessionHandler(remoteAddress) },
        dtlsSessionConfig
    )

    private inner class DtlsSessionHandler(
        private val remoteAddress: SocketAddress
    ) : AbstractDatagramHandler() {

        override fun getConfig(): ISessionConfig = dtlsSessionConfig

        override fun read(data: ByteArray) {
            handleDecryptedPacket(data)
        }

        override fun read(msg: Any?) {
            when (msg) {
                is ByteArray -> handleDecryptedPacket(msg)
                else -> log.warn("Unexpected message type from {}: {}", remoteAddress, msg?.javaClass?.name)
            }
        }

        override fun read(remoteAddress: SocketAddress?, msg: Any?) {
            val addr = remoteAddress ?: this.remoteAddress
            when (msg) {
                is ByteArray -> handleDecryptedPacket(msg)
                else -> log.warn("Unexpected message type from {}: {}", addr, msg?.javaClass?.name)
            }
        }

        override fun event(remoteAddress: SocketAddress?, event: DataEvent?, length: Long) {}

        private fun handleDecryptedPacket(data: ByteArray) {
            try {
                inboundHandler.handlePacket(remoteAddress, data) { responseData ->
                    session?.write(responseData)
                }
            } catch (e: Exception) {
                log.error("Error handling decrypted packet from {}", remoteAddress, e)
            }
        }

        override fun event(event: SessionEvent) {
            when (event) {
                SessionEvent.OPENED -> {
                    log.info("DTLS session opened from {}", remoteAddress)
                    activeSessions[remoteAddress] = this
                }
                SessionEvent.READY -> log.info("DTLS handshake complete for {}", remoteAddress)
                SessionEvent.CLOSED -> {
                    log.info("DTLS session closed from {}", remoteAddress)
                    cleanup()
                }
                SessionEvent.ENDING -> log.debug("DTLS session ending from {}", remoteAddress)
                else -> log.debug("Session event {} from {}", event, remoteAddress)
            }
        }

        override fun exception(t: Throwable) {
            log.error("DTLS session error from {}: {}", remoteAddress, t.message)
            if (log.isDebugEnabled) {
                log.debug("DTLS exception details", t)
            }
            cleanup()
        }

        private fun cleanup() {
            activeSessions.remove(remoteAddress)
            inboundHandler.removeSession(remoteAddress)
        }
    }
}

