package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Protocol
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Advertises the control server on the LAN via mDNS so Android NSD can find it. */
class DeskBuddyAdvertiser(private val port: Int = Protocol.PORT) {

    private val instances = mutableListOf<JmDNS>()

    /** One JmDNS per LAN address so the phone hears us whichever adapter it shares. */
    fun start() {
        val name = LanInterfaces.hostName()
        val addresses = LanInterfaces.current().ifEmpty { listOf(InetAddress.getLocalHost()) }
        for (address in addresses) {
            runCatching {
                JmDNS.create(address, name).also {
                    it.registerService(
                        ServiceInfo.create(Protocol.SERVICE_TYPE + "local.", name, port, "DeskBuddy control server"),
                    )
                    instances += it
                }
            }
        }
    }

    fun stop() {
        instances.forEach { jmdns ->
            runCatching {
                jmdns.unregisterAllServices()
                jmdns.close()
            }
        }
        instances.clear()
    }
}
