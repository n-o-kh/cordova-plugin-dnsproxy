package com.roqos.cordova.plugin;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Base64;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.pcap4j.packet.*;

import de.measite.minidns.DNSMessage;
import de.measite.minidns.Question;
import de.measite.minidns.Record;
import de.measite.minidns.edns.EDNSOption;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.source.NetworkDataSource;
import de.measite.minidns.record.Data;

public class UdpProvider extends Provider {
    private static final String TAG = "UdpProvider";

    private final WospList dnsIn = new WospList();
    FileDescriptor mBlockFd = null;
    FileDescriptor mInterruptFd = null;
    final Queue<byte[]> deviceWrites = new LinkedList<byte[]>();

    public UdpProvider(ParcelFileDescriptor descriptor, RoqosVPNService service) {
        super(descriptor, service);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void stop() {
        try {
            if (mInterruptFd != null) {
                Os.close(mInterruptFd);
            }
            if (mBlockFd != null) {
                Os.close(mBlockFd);
            }
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
        } catch (Exception ignored) {
        }
    }

    private void queueDeviceWrite(IpPacket ipOutPacket) {
        dnsQueryTimes++;
        deviceWrites.add(ipOutPacket.getRawData());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void process() {
        try {
            Log.d(TAG, "Starting advanced DNS proxy.");
            FileDescriptor[] pipes = Os.pipe();
            mInterruptFd = pipes[0];
            mBlockFd = pipes[1];
            FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
            FileOutputStream outputStream = new FileOutputStream(descriptor.getFileDescriptor());

            byte[] packet = new byte[32767];
            while (running) {
                StructPollfd deviceFd = new StructPollfd();
                deviceFd.fd = inputStream.getFD();
                deviceFd.events = (short) OsConstants.POLLIN;
                StructPollfd blockFd = new StructPollfd();
                blockFd.fd = mBlockFd;
                blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);

                if (!deviceWrites.isEmpty())
                    deviceFd.events |= (short) OsConstants.POLLOUT;

                StructPollfd[] polls = new StructPollfd[2 + dnsIn.size()];
                polls[0] = deviceFd;
                polls[1] = blockFd;
                {
                    int i = -1;
                    Log.d(TAG, String.valueOf(dnsIn.size()));
                    for (WaitingOnSocketPacket wosp : dnsIn) {
                        i++;
                        StructPollfd pollFd = polls[2 + i] = new StructPollfd();
                        pollFd.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).getFileDescriptor();
                        pollFd.events = (short) OsConstants.POLLIN;
                    }
                }

                Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
                Os.poll(polls, -1);
                if (blockFd.revents != 0) {
                    Log.i(TAG, "Told to stop VPN");
                    running = false;
                    return;
                }

                // Need to do this before reading from the device, otherwise a new insertion there could
                // invalidate one of the sockets we want to read from either due to size or time out
                // constraints
                {
                    int i = -1;
                    Iterator<WaitingOnSocketPacket> iter = dnsIn.iterator();
                    Log.d(TAG, String.valueOf(iter.hasNext()));
                    while (iter.hasNext()) {
                        Log.d(TAG, "In While");
                        i++;
                        WaitingOnSocketPacket wosp = iter.next();
                        if ((polls[i + 2].revents & OsConstants.POLLIN) != 0) {
                            Log.d(TAG, "Read from UDP DNS socket" + wosp.socket);
                            iter.remove();
                            handleRawDnsResponse(wosp.packet, wosp.socket);
                            wosp.socket.close();
                        }
                    }
                }
                if ((deviceFd.revents & OsConstants.POLLOUT) != 0) {
                    Log.d(TAG, "Write to device");
                    writeToDevice(outputStream);
                }
                if ((deviceFd.revents & OsConstants.POLLIN) != 0) {
                    Log.d(TAG, "Read from device");
                    readPacketFromDevice(inputStream, packet);
                }
//                service.providerLoopCallback();
            }
        } catch (Exception e) {
//            Logger.logException(e);
        }
    }

    void writeToDevice(FileOutputStream outFd) throws RoqosVPNService.VpnNetworkException {
        try {
            outFd.write(deviceWrites.poll());
        } catch (IOException e) {
            throw new RoqosVPNService.VpnNetworkException("Outgoing VPN output stream closed");
        }
    }

    void readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws RoqosVPNService.VpnNetworkException {
        // Read the outgoing packet from the input stream.
        int length;

        try {
            length = inputStream.read(packet);
        } catch (IOException e) {
            throw new RoqosVPNService.VpnNetworkException("Cannot read from device", e);
        }


        if (length == 0) {
            Log.w(TAG, "Got empty packet!");
            return;
        }

        final byte[] readPacket = Arrays.copyOfRange(packet, 0, length);
        handleDnsRequest(readPacket);
    }

    void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws RoqosVPNService.VpnNetworkException {
        DatagramSocket dnsSocket;
        Log.d(TAG, "forwardPacket");
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            Log.d(TAG, "Packets to be sent to the real DNS server will need to be protected from the VPN");
            dnsSocket = new DatagramSocket();

            service.protect(dnsSocket);

            dnsSocket.send(outPacket);

            if (parsedPacket != null) {
                Log.d(TAG, "dnsIn.add");
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
            } else {
                dnsSocket.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "DNSProvider: Could not send packet to upstream, forwarding packet directly");
            handleDnsResponse(parsedPacket, outPacket.getData());
        }
    }

    private void handleRawDnsResponse(IpPacket parsedPacket, DatagramSocket dnsSocket) {
        try {
            byte[] datagramData = new byte[1024];
            DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
            dnsSocket.receive(replyPacket);
            handleDnsResponse(parsedPacket, datagramData);
        } catch (Exception e) {
//            Logger.logException(e);
        }
    }


    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
        UdpPacket udpOutPacket = (UdpPacket) requestPacket.getPayload();
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder(udpOutPacket)
                .srcPort(udpOutPacket.getHeader().getDstPort())
                .dstPort(udpOutPacket.getHeader().getSrcPort())
                .srcAddr(requestPacket.getHeader().getDstAddr())
                .dstAddr(requestPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder()
                                .rawData(responsePayload)
                );


        IpPacket ipOutPacket;
        if (requestPacket instanceof IpV4Packet) {
            ipOutPacket = new IpV4Packet.Builder((IpV4Packet) requestPacket)
                    .srcAddr((Inet4Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet4Address) requestPacket.getHeader().getSrcAddr())
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();

        } else {
            ipOutPacket = new IpV6Packet.Builder((IpV6Packet) requestPacket)
                    .srcAddr((Inet6Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet6Address) requestPacket.getHeader().getSrcAddr())
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();
        }

        queueDeviceWrite(ipOutPacket);
    }

    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public byte[] macAddressToByteArray(String s) {
        String macAddress =  s;
        String[] macAddressParts = macAddress.split(":");

        // convert hex string to byte values
        byte[] macAddressBytes = new byte[6];
        for(int i=0; i<6; i++) {
            Integer hex = Integer.parseInt(macAddressParts[i], 16);
            macAddressBytes[i] = hex.byteValue();
        }

        return Base64.encode(macAddressBytes, 0);
    }

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws RoqosVPNService.VpnNetworkException If some network error occurred
     */
    private void handleDnsRequest(byte[] packetData) throws RoqosVPNService.VpnNetworkException {

        IpPacket parsedPacket;
        try {
            parsedPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Log.i(TAG, "handleDnsRequest: Discarding invalid IP packet", e);
            return;
        }

        if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
            Log.i(TAG, "handleDnsRequest: Discarding unknown packet type " + parsedPacket.getPayload());
            return;
        }

        InetAddress destAddr = parsedPacket.getHeader().getDstAddr();
        if (destAddr == null)
            return;
        try {
            destAddr = InetAddress.getByName(service.dnsServers.get(destAddr.getHostAddress()));
        } catch (Exception e) {
//            Logger.logException(e);
//            Logger.error("handleDnsRequest: DNS server alias query failed for " + destAddr.getHostAddress());
            return;
        }

        UdpPacket parsedUdp = (UdpPacket) parsedPacket.getPayload();

        if (parsedUdp.getPayload() == null) {
            Log.i(TAG, "handleDnsRequest: Sending UDP packet without payload: " + parsedUdp);

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, 0, destAddr,
                    DNSServerHelper.getPortOrDefault(destAddr, parsedUdp.getHeader().getDstPort().valueAsInt()));
            forwardPacket(outPacket, null);
            return;
        }

        byte[] dnsRawData = (parsedUdp).getPayload().getRawData();
        DNSMessage dnsMsg;
        try {
            dnsMsg = new DNSMessage(dnsRawData);
            Log.i(TAG, "adb logcat | grep MacAddress: " + Roqos.optionsCode.get(0));
            DNSMessage.Builder message = dnsMsg.asBuilder();
            // Roqos.optionsCode.size
            for(int i = 0; i < Roqos.optionsCode.size(); i++){
                message.getEdnsBuilder().addEdnsOption(EDNSOption.parse(Integer.parseInt(Roqos.optionsCode.get(i)), new String(Roqos.ednsMessage.get(i)).getBytes()));
            }
            // message.getEdnsBuilder().addEdnsOption(EDNSOption.parse(65073, macAddressToByteArray("1f:68:3f:75:41:1e")));
            // message.getEdnsBuilder().addEdnsOption(EDNSOption.parse(65074, new String("5abc68de8f7b634e50a36f05").getBytes()));
            message.getEdnsBuilder().setUdpPayloadSize(512);
            dnsMsg = message.build();
            dnsRawData = dnsMsg.toArray();
//            Log.d(TAG, dnsRawData.toString());
        } catch (IOException e) {
            Log.i(TAG, "handleDnsRequest: Discarding non-DNS or invalid packet", e);
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Log.i(TAG, "handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }
        String dnsQueryName = dnsMsg.getQuestion().name.toString();

        try {
            String response = RuleResolver.resolve(dnsQueryName, dnsMsg.getQuestion().type);
            if (response != null && dnsMsg.getQuestion().type == Record.TYPE.A) {
//                Logger.info("Provider: Resolved " + dnsQueryName + "  Local resolver response: " + response);
                DNSMessage.Builder builder = dnsMsg.asBuilder();
//                builder.getEdnsBuilder().addEdnsOption(EDNSOption.parse(65073, hexStringToByteArray("425041684a31535a")));
//                builder.getEdnsBuilder().addEdnsOption(EDNSOption.parse(65074, hexStringToByteArray("356162313831633334376635313732663335636563666661")));
//                builder.getEdnsBuilder().addEdnsOption(EDNSOption.parse(8, hexStringToByteArray("0001200062bfd43c")));
//                builder.getEdnsBuilder().setUdpPayloadSize(512);
                builder.setQrFlag(true)
                        .addAnswer(new Record<Data>(dnsQueryName, Record.TYPE.A, 1, 64,
                                new A(Inet4Address.getByName(response).getAddress())));
                handleDnsResponse(parsedPacket, builder.build().toArray());
            } else if (response != null && dnsMsg.getQuestion().type == Record.TYPE.AAAA) {
//                Logger.info("Provider: Resolved " + dnsQueryName + "  Local resolver response: " + response);
                DNSMessage.Builder builder = dnsMsg.asBuilder();
//                builder.getEdnsBuilder().addEdnsOption(EDNSOption.parse(65073, hexStringToByteArray("425041684a31535a")));
//                builder.getEdnsBuilder().addEdnsOption(EDNSOption.parse(65074, hexStringToByteArray("356162313831633334376635313732663335636563666661")));
//                builder.getEdnsBuilder().addEdnsOption(EDNSOption.parse(8, hexStringToByteArray("0001200062bfd43c")));
//                builder.getEdnsBuilder().setUdpPayloadSize(512);
                builder.setQrFlag(true)
                        .addAnswer(new Record<Data>(dnsQueryName, Record.TYPE.AAAA, 1, 64,
                                new AAAA(Inet6Address.getByName(response).getAddress())));
                handleDnsResponse(parsedPacket, builder.build().toArray());
            } else {
                Log.d(TAG, "Provider: Resolving " + dnsQueryName + " Type: " + dnsMsg.getQuestion().type.name() + " Sending to " + destAddr);
//                Log.d(TAG, String.valueOf(DNSServerHelper.getPortOrDefault(destAddr, parsedUdp.getHeader().getDstPort().valueAsInt())));
                DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, destAddr,
                        Roqos.getPort());
//                Log.d(TAG, String.valueOf(outPacket));
                forwardPacket(outPacket, parsedPacket);
            }
        } catch (Exception e) {
            Log.d(TAG, "Provider: Resolving " + e.toString());
        }
    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    private static class WaitingOnSocketPacket {
        final DatagramSocket socket;
        final IpPacket packet;
        private final long time;

        WaitingOnSocketPacket(DatagramSocket socket, IpPacket packet) {
            this.socket = socket;
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - time) / 1000;
        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    private static class WospList implements Iterable<WaitingOnSocketPacket> {
        private final LinkedList<WaitingOnSocketPacket> list = new LinkedList<WaitingOnSocketPacket>();

        void add(WaitingOnSocketPacket wosp) {
            if (list.size() > 1024) {
                Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            while (!list.isEmpty() && list.element().ageSeconds() > 10) {
                Log.d(TAG, "Timeout on socket " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            list.add(wosp);
        }

        @NonNull
        public Iterator<WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }

    }
}
