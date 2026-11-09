package com.smartbus.driver.lan;

import com.smartbus.driver.bluetooth.DriverBlePayload;
import com.smartbus.driver.bluetooth.SmartBusBleCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small, dependency-free LAN receiver. The business payload remains the same
 * two-field BLE payload: #routeId alightingStationId. The request id is only a
 * transport idempotency token and is never used for route validation.
 */
public final class LocalLanCheckInServer {

    public static final int DISCOVERY_PORT = 38766;
    public static final int TCP_PORT = 38767;
    public static final int STATUS_PORT = 38768;
    private static final String DISCOVERY_REQUEST = "SMARTBUS_DRIVER_DISCOVERY_V1";
    private static final String DISCOVERY_RESPONSE = "SMARTBUS_DRIVER_V1";
    private static final String CHECK_IN_HEADER = "SMARTBUS_CHECKIN_V1";
    private static final String STATUS_HEADER = "SMARTBUS_LAN_STATUS_V1";
    private static final int MAX_LINE_LENGTH = 256;

    public interface Listener {
        void onPayload(DriverBlePayload payload, String requestId, String passengerIdentifier,
                       Long boardingStopId, String sourceAddress);

        void onMessage(String message);
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ServerSocket tcpServer;
    private volatile DatagramSocket discoverySocket;
    private volatile ExecutorService ioExecutor;
    private volatile long routeId;
    private volatile Listener listener;
    private final ConcurrentHashMap<String, String> clientAddresses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> recentRequestIds = new ConcurrentHashMap<>();
    private static final long REQUEST_DEDUP_WINDOW_MS = 15_000L;

    public synchronized void start(long routeId, Listener listener) {
        if (running.get() && this.routeId == routeId) {
            return;
        }
        stop();
        this.routeId = routeId;
        this.listener = listener;
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(TCP_PORT));

            DatagramSocket discovery = new DatagramSocket(null);
            discovery.setReuseAddress(true);
            discovery.setBroadcast(true);
            discovery.bind(new InetSocketAddress(DISCOVERY_PORT));

            tcpServer = server;
            discoverySocket = discovery;
            ioExecutor = executor;
            running.set(true);
            executor.execute(this::acceptTcpConnections);
            executor.execute(this::answerDiscoveryRequests);
            notifyMessage("Đã mở nhận check-in LAN cho tuyến #" + routeId + ".");
        } catch (IOException exception) {
            closeQuietly(tcpServer);
            closeQuietly(discoverySocket);
            executor.shutdownNow();
            tcpServer = null;
            discoverySocket = null;
            ioExecutor = null;
            notifyMessage("Không mở được nhận check-in LAN: " + safeMessage(exception));
        }
    }

    public synchronized void stop() {
        running.set(false);
        clientAddresses.clear();
        recentRequestIds.clear();
        closeQuietly(tcpServer);
        closeQuietly(discoverySocket);
        tcpServer = null;
        discoverySocket = null;
        ExecutorService executor = ioExecutor;
        ioExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void acceptTcpConnections() {
        while (running.get()) {
            try {
                ServerSocket server = tcpServer;
                if (server == null) return;
                Socket socket = server.accept();
                ExecutorService executor = ioExecutor;
                if (executor != null) {
                    executor.execute(() -> handleTcpConnection(socket));
                } else {
                    closeQuietly(socket);
                }
            } catch (IOException exception) {
                if (running.get()) {
                    notifyMessage("Mất kết nối lắng nghe LAN: " + safeMessage(exception));
                }
            }
        }
    }

    private void answerDiscoveryRequests() {
        byte[] buffer = new byte[128];
        while (running.get()) {
            try {
                DatagramSocket socket = discoverySocket;
                if (socket == null) return;
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String request = new String(
                        packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                if (!DISCOVERY_REQUEST.equals(request)) continue;
                String response = DISCOVERY_RESPONSE + ";" + TCP_PORT + ";" + routeId;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        bytes, bytes.length, packet.getAddress(), packet.getPort());
                socket.send(reply);
            } catch (IOException exception) {
                if (running.get()) {
                    notifyMessage("Không phản hồi được discovery LAN: " + safeMessage(exception));
                }
            }
        }
    }

    private void handleTcpConnection(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    client.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    client.getOutputStream(), StandardCharsets.UTF_8));
            String header = readLine(reader);
            String requestId = readLine(reader);
            String passengerIdentifier = readLine(reader);
            Long boardingStopId = parseOptionalId(readLine(reader));
            String payloadText = readLine(reader);
            if (!CHECK_IN_HEADER.equals(header) || !isSafeRequestId(requestId)
                    || !isSafeRequestId(passengerIdentifier)
                    || payloadText == null || payloadText.length() > MAX_LINE_LENGTH) {
                writeResponse(writer, "ERROR");
                return;
            }
            DriverBlePayload payload = SmartBusBleCodec.decode(
                    payloadText.getBytes(StandardCharsets.UTF_8));
            if (payload == null) {
                writeResponse(writer, "ERROR_PAYLOAD");
                return;
            }
            String deduplicationKey = payload.getRouteId() + ":"
                    + (boardingStopId == null ? "-" : boardingStopId) + ":"
                    + payload.getDestinationStopId() + ":" + requestId;
            long now = System.currentTimeMillis();
            Long previous = recentRequestIds.putIfAbsent(deduplicationKey, now);
            if (previous != null && now - previous < REQUEST_DEDUP_WINDOW_MS) {
                writeResponse(writer, "DUPLICATE");
                return;
            }
            recentRequestIds.put(deduplicationKey, now);
            recentRequestIds.entrySet().removeIf(entry -> now - entry.getValue() >= REQUEST_DEDUP_WINDOW_MS);
            Listener currentListener = listener;
            if (currentListener != null) {
                String sourceAddress = client.getInetAddress() == null
                        ? "LAN" : client.getInetAddress().getHostAddress();
                clientAddresses.put(requestId, sourceAddress);
                currentListener.onPayload(payload, requestId, passengerIdentifier,
                        boardingStopId, sourceAddress);
            }
            writeResponse(writer, "OK");
        } catch (IOException exception) {
            if (running.get()) {
                notifyMessage("Nhận check-in LAN thất bại: " + safeMessage(exception));
            }
        }
    }

    /** Sends the Driver's local acceptance back to the Passenger that sent a LAN request. */
    public void sendAcceptance(
            String requestId,
            long tripId,
            long routeId,
            Long boardingStopId,
            Long destinationStopId,
            String status
    ) {
        if (requestId == null || requestId.isBlank()) return;
        String address = clientAddresses.get(requestId);
        ExecutorService executor = ioExecutor;
        if (address == null || executor == null || !running.get()) return;
        executor.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, STATUS_PORT), 1500);
                socket.setSoTimeout(2000);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
                writeLine(writer, STATUS_HEADER);
                writeLine(writer, requestId);
                writeLine(writer, status == null ? "BOARDED" : status);
                writeLine(writer, String.valueOf(tripId));
                writeLine(writer, String.valueOf(routeId));
                writeLine(writer, boardingStopId == null ? "-" : String.valueOf(boardingStopId));
                writeLine(writer, destinationStopId == null ? "-" : String.valueOf(destinationStopId));
                writer.flush();
                clientAddresses.remove(requestId);
                notifyMessage("Đã gửi trạng thái xác nhận LAN cho hành khách.");
            } catch (IOException exception) {
                notifyMessage("Không gửi được trạng thái xác nhận LAN cho hành khách: "
                        + safeMessage(exception));
            }
        });
    }

    private static String readLine(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line != null && line.length() > MAX_LINE_LENGTH) return null;
        return line;
    }

    private static boolean isSafeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) return false;
        for (int i = 0; i < requestId.length(); i++) {
            char value = requestId.charAt(i);
            if (!(Character.isLetterOrDigit(value) || value == '-' || value == '_' || value == '.'
                    || value == ':')) {
                return false;
            }
        }
        return true;
    }

    private static Long parseOptionalId(String value) {
        if (value == null || "-".equals(value) || value.length() > 32) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void writeResponse(BufferedWriter writer, String response) throws IOException {
        writeLine(writer, response);
        writer.flush();
    }

    private static void writeLine(BufferedWriter writer, String value) throws IOException {
        writer.write(value == null ? "-" : value);
        writer.newLine();
    }

    private void notifyMessage(String message) {
        Listener currentListener = listener;
        if (currentListener != null) currentListener.onMessage(message);
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }

    private static void closeQuietly(DatagramSocket socket) {
        if (socket != null) socket.close();
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
}
