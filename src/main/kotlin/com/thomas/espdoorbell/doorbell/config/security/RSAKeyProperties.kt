package com.thomas.espdoorbell.doorbell.config.security

import org.springframework.stereotype.Component
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Component
class RSAKeyProperties {
    private final val _publicKey: RSAPublicKey
    val publicKey: RSAPublicKey
        get() = _publicKey
    private final val _privateKey: RSAPrivateKey
    val privateKey: RSAPrivateKey
        get() = _privateKey

    init {
        val keyPair: KeyPair = KeyGenerator.generateRSAKey()
        this._publicKey = keyPair.public as RSAPublicKey
        this._privateKey = keyPair.private as RSAPrivateKey
    }
}