import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

class TFTPServer {
    private static final int TFTP_DEFAULT_PORT = 1069;
    private static final int BUFFER_SIZE = 516;
    private static final int DATA_SIZE = 512;
    private static final String CREDENTIALS_FILE = "credentials.txt";
    
    private static Map<String, String> credentials = new HashMap<>();

    public static void main(String[] args) throws IOException {
        loadCredentials();
        
        DatagramSocket socket = new DatagramSocket(TFTP_DEFAULT_PORT);
        System.out.println("TFTP Server is listening on port " + TFTP_DEFAULT_PORT);

        while (true) {
            DatagramPacket requestPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
            socket.receive(requestPacket);

            byte[] data = requestPacket.getData();
            int opcode = data[1];

            InetAddress clientAddress = requestPacket.getAddress();
            int clientPort = requestPacket.getPort();

            switch (opcode) {
                case 1: // RRQ (Read Request)
                    handleReadRequest(socket, data, clientAddress, clientPort);
                    break;
                case 2: // WRQ (Write Request)
                    handleWriteRequest(socket, data, clientAddress, clientPort);
                    break;
                case 5: // AUTH (Authentication Request)
                    handleAuthRequest(socket, data, clientAddress, clientPort);
                    break;
                default:
                    System.out.println("Received an unknown opcode: " + opcode);
                    break;
            }
        }
    }

    private static void loadCredentials() {
        try (BufferedReader br = new BufferedReader(new FileReader(CREDENTIALS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    credentials.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load credentials: " + e.getMessage());
        }
    }

    private static void handleAuthRequest(DatagramSocket socket, byte[] data, InetAddress clientAddress, int clientPort) throws IOException {
        String[] authData = extractAuthData(data);
        String username = authData[1];
        String password = authData[2];

        String response = credentials.containsKey(username) && credentials.get(username).equals(password) ? "AUTH_SUCCESS" : "AUTH_FAILED";
        System.out.println("Received authentication request for user: " + username + " with password: " + password);
        byte[] responseData = response.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
        socket.send(responsePacket);
        System.out.println("Sent authentication response: " + response);
    }

    private static String[] extractAuthData(byte[] data) {
        String authMessage = new String(data, 2, data.length - 2).trim();
        return authMessage.split(":");
    }

    private static void handleReadRequest(DatagramSocket socket, byte[] data, InetAddress clientAddress, int clientPort) throws IOException {
        String filename = extractFilename(data);
        System.out.println("Received read request for file: " + filename);

        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("File not found: " + filename);
            return;
        }

        try (BufferedInputStream fileStream = new BufferedInputStream(new FileInputStream(file))) {
            int blockNumber = 1;
            byte[] fileData = new byte[DATA_SIZE];
            int bytesRead;

            while ((bytesRead = fileStream.read(fileData)) != -1) {
                DatagramPacket dataPacket = createDataPacket(fileData, bytesRead, blockNumber, clientAddress, clientPort);
                socket.send(dataPacket);

                DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4);
                socket.receive(ackPacket);
                blockNumber++;
            }

            System.out.println("File transfer complete.");
        }
    }

    private static void handleWriteRequest(DatagramSocket socket, byte[] data, InetAddress clientAddress, int clientPort) throws IOException {
        String filename = extractFilename(data);
        System.out.println("Received write request for file: " + filename);

        File file = new File(filename);
        try (BufferedOutputStream fileStream = new BufferedOutputStream(new FileOutputStream(file))) {
            int blockNumber = 0;
            while (true) {
                sendAck(socket, blockNumber, clientAddress, clientPort);

                DatagramPacket dataPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
                socket.receive(dataPacket);

                byte[] packetData = dataPacket.getData();
                int receivedBlockNumber = (packetData[2] << 8) | (packetData[3] & 0xFF);

                if (receivedBlockNumber != blockNumber + 1) {
                    System.out.println("Unexpected block number: expected " + (blockNumber + 1) + ", but got " + receivedBlockNumber);
                    continue;
                }

                byte[] fileData = new byte[dataPacket.getLength() - 4];
                System.arraycopy(packetData, 4, fileData, 0, fileData.length);
                fileStream.write(fileData);
                fileStream.flush();

                System.out.println("Written block " + receivedBlockNumber);
                blockNumber = receivedBlockNumber;

                if (fileData.length < DATA_SIZE) {
                    System.out.println("Last packet received, file transfer complete.");
                    break;
                }
            }
        }
    }

    private static String extractFilename(byte[] data) {
        int i = 2;
        StringBuilder filename = new StringBuilder();
        while (data[i] != 0) {
            filename.append((char) data[i]);
            i++;
        }
        return filename.toString();
    }

    private static DatagramPacket createDataPacket(byte[] fileData, int bytesRead, int blockNumber, InetAddress address, int port) {
        byte[] packetData = new byte[4 + bytesRead];
        packetData[0] = 0;
        packetData[1] = 3;
        packetData[2] = (byte) (blockNumber >> 8);
        packetData[3] = (byte) (blockNumber & 0xFF);

        System.arraycopy(fileData, 0, packetData, 4, bytesRead);

        return new DatagramPacket(packetData, packetData.length, address, port);
    }

    private static void sendAck(DatagramSocket socket, int blockNumber, InetAddress address, int port) throws IOException {
        byte[] ackData = new byte[4];
        ackData[0] = 0;
        ackData[1] = 4;
        ackData[2] = (byte) (blockNumber >> 8);
        ackData[3] = (byte) (blockNumber & 0xFF);

        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
        socket.send(ackPacket);
        System.out.println("Sent ACK for block " + blockNumber);
    }
}