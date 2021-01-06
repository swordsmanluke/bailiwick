package com.perfectlunacy.bailiwick.ipfs.lite


import android.util.Log
import org.minidns.DnsClient
import org.minidns.cache.LruCache
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsqueryresult.DnsQueryResult
import org.minidns.record.Data
import org.minidns.record.Record
import org.minidns.record.TXT
import java.net.Inet6Address
import java.net.InetAddress
import java.util.*


object DnsAddrResolver {
    private const val LIB2P = "_dnsaddr.bootstrap.libp2p.io"
    private const val DNS_ADDR = "dnsaddr=/dnsaddr/"
    private const val IPv4 = "/ip4/"
    private const val IPv6 = "/ip6/"
    private val TAG = DnsAddrResolver::class.java.simpleName

    // now get IP of multiAddress
    val multiAddresses: List<String>
        get() {
            val multiAddresses: MutableList<String> = ArrayList()
            val txtRecords = txtRecords
            for (txtRecord in txtRecords) {
                try {
                    if (txtRecord.startsWith(DNS_ADDR)) {
                        val multiAddress = txtRecord.replaceFirst(DNS_ADDR.toRegex(), "")
                        // now get IP of multiAddress
                        val host = multiAddress.substring(0, multiAddress.indexOf("/"))
                        if (!host.isEmpty()) {
                            val data = multiAddress.substring(host.length)
                            val address = InetAddress.getByName(host)
                            var ip = IPv4
                            if (address is Inet6Address) {
                                ip = IPv6
                            }
                            val hostAddress = address.hostAddress
                            if (!data.startsWith("/p2p/")) {
                                val newAddress = hostAddress + data
                                multiAddresses.add(ip + newAddress)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "" + e.localizedMessage, e)
                }
            }
            return multiAddresses
        }

    private val txtRecords: List<String>
        get() {
            val txtRecords: MutableList<String> = ArrayList()
            try {
                val client = DnsClient(LruCache(0))
                val result: DnsQueryResult = client.query(LIB2P, Record.TYPE.TXT)
                val response: DnsMessage = result.response
                val records: List<Record<out Data?>> = response.answerSection
                for (record in records) {
                    val text: TXT = record.getPayload() as TXT
                    txtRecords.add(text.getText())
                }
            } catch (e: Throwable) {
                Log.e(TAG, "" + e.localizedMessage, e)
            }
            return txtRecords
        }
}
