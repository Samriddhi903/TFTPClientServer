import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.Scanner;

class TFTPClient {
    private static final int TFTP_DEFAULT_PORT = 1069;
    private static final int BUFFER_SIZE = 516;
    private static final int DATA_SIZE = 512;
    private static final int MAX_RETRIES = 5;
    private static final int TIMEOUT_MS = 10000;
    private static final String DEFAULT_DIRECTORY = "client_files";
    
    private final InetAddress serverAddress; // Storing IP address of TFTP server
    private final Path clientDirectory;
    
    public TFTPClient(String serverHost) throws UnknownHostException {
        this.serverAddress = InetAddress.getByName(serverHost); // Gets the server IP address
        this.clientDirectory = Paths.get(DEFAULT_DIRECTORY);
        initializeDirectory();
    }
    
    private void initializeDirectory() { // Function to create 'client_files'
        try {
            Files.createDirectories(clientDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create client directory: " + e.getMessage());
            throw new RuntimeException("Failed to initialize client", e);
        }
    }
    
    public boolean authenticate(String username, String password) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            
            // Create authentication packet
            byte[] authData = createAuthPacket(username, password);
            DatagramPacket authPacket = new DatagramPacket(authData, authData.length, serverAddress, TFTP_DEFAULT_PORT);
            sendWithRetry(socket, authPacket);
            
            // Wait for authentication response
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            
            // Check response
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength()).trim();
            return "AUTH_SUCCESS".equals(response); // Fix: Added trim() to handle any extra spaces
        } catch (SocketTimeoutException e) {
            System.err.println("Authentication timeout.");
            return false;
        }
    }

    private byte[] createAuthPacket(String username, String password) {
        String authMessage = "AUTH:" + username + ":" + password;
        byte[] authData = new byte[2 + authMessage.length()];
        authData[0] = 0; // TFTP opcode
        authData[1] = 5; // AUTH opcode
        System.arraycopy(authMessage.getBytes(), 0, authData, 2, authMessage.length());
        
        // Debugging: Output the constructed authentication message
        System.out.println("Constructed Auth Packet: " + authMessage);
        
        return authData;
    }
    

    public void downloadFile(String filename) throws IOException { // RRQ
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            
            byte[] rrqData = createRequestPacket(OpCode.RRQ, filename, "octet");
            DatagramPacket rrqPacket = new DatagramPacket(rrqData, rrqData.length, serverAddress, TFTP_DEFAULT_PORT);
            sendWithRetry(socket, rrqPacket);
            
            Path outputFile = clientDirectory.resolve(filename);
            try (OutputStream fos = new FileOutputStream(outputFile.toFile())) {
                int blockNumber = 1;
                do {
                    DataPacketResult result = receiveDataPacket(socket, blockNumber);
                    if (!result.success) break;
                    
                    if (result.data.length > 0) {
                        fos.write(result.data);
                        System.out.println("Writing " + result.data.length + " bytes to file.");
                    } else {
                        System.out.println("Received empty data block.");
                    }
                    
                    sendAck(socket, blockNumber, result.address, result.port);
                    
                    if (result.lastPacket) {
                        System.out.println("Last packet received, closing file.");
                        break;
                    }
                    blockNumber++;
                } while (true);
            }
        }
    }

    public void uploadFile(String filename) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            
            byte[] wrqData = createRequestPacket(OpCode.WRQ, filename, "octet");
            DatagramPacket wrqPacket = new DatagramPacket(wrqData, wrqData.length, serverAddress, TFTP_DEFAULT_PORT);
            sendWithRetry(socket, wrqPacket);
            
            Path inputFile = clientDirectory.resolve(filename);
            byte[] fileData = Files.readAllBytes(inputFile);
            int blockNumber = 1;
            int offset = 0;

            while (offset < fileData.length) {
                int bytesToSend = Math.min(DATA_SIZE, fileData.length - offset);
                byte[] dataBlock = new byte[bytesToSend + 4];
                dataBlock[0] = 0;
                dataBlock[1] = OpCode.DATA.getValue();
                dataBlock[2] = (byte) (blockNumber >> 8);
                dataBlock[3] = (byte) (blockNumber & 0xFF);
                System.arraycopy(fileData, offset, dataBlock, 4, bytesToSend);

                DatagramPacket dataPacket = new DatagramPacket(dataBlock, dataBlock.length, serverAddress, TFTP_DEFAULT_PORT);
                sendWithRetry(socket, dataPacket);
                
                offset += bytesToSend;
                blockNumber++;
            }
            System.out.println("File uploaded successfully.");
        }
    }
    
    private byte[] createRequestPacket(OpCode opCode, String filename, String mode) {
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = mode.getBytes();
        byte[] packet = new byte[2 + filenameBytes.length + 1 + modeBytes.length + 1];
        
        packet[0] = 0;
        packet[1] = opCode.getValue();
        System.arraycopy(filenameBytes, 0, packet, 2, filenameBytes.length);
        packet[2 + filenameBytes.length] = 0;
        System.arraycopy(modeBytes, 0, packet, 3 + filenameBytes.length, modeBytes.length);
        packet[3 + filenameBytes.length + modeBytes.length] = 0;
        
        return packet;
    }
    
    private void sendWithRetry(DatagramSocket socket, DatagramPacket packet) throws IOException {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            try {
                socket.send(packet);
                System.out.println("Sent request packet, waiting for response...");
                return;
            } catch (IOException e) {
                System.err.println("Retrying... Attempt " + (attempts + 1));
                attempts++;
            }
        }
        throw new IOException("Failed to send packet after " + MAX_RETRIES + " attempts");
    }
    
    private DataPacketResult receiveDataPacket(DatagramSocket socket, int expectedBlock) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        int attempts = 0;
    
        while (attempts < MAX_RETRIES) {
            try {
                socket.receive(packet);
                byte[] receivedData = packet.getData();
                int blockNumber = getBlockNumber(receivedData);
    
                // Check if the block number matches the expected one
                if (blockNumber != expectedBlock) {
                    System.err.println("Expected block " + expectedBlock + ", but received block " + blockNumber);
                    continue;  // Skip this packet and try again
                }
    
                int dataLength = packet.getLength() - 4;  // Excluding the 4-byte header
                byte[] data = new byte[dataLength];
                System.arraycopy(receivedData, 4, data, 0, dataLength);
    
                System.out.println("Received block " + blockNumber + " with " + dataLength + " bytes.");
    
                // Return a DataPacketResult containing data and other information
                return new DataPacketResult(true, data, dataLength < DATA_SIZE, packet.getAddress(), packet.getPort());
            } catch (SocketTimeoutException e) {
                System.err.println("Timeout waiting for data packet. Retrying...");
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    System.err.println("Max retries reached. Aborting.");
                    return new DataPacketResult(false, new byte[0], false, null, 0);
                }
            }
        }
        return new DataPacketResult(false, new byte[0], false, null, 0);  // Timeout reached or max retries exceeded
    }
    
    private void sendAck(DatagramSocket socket, int blockNumber, InetAddress address, int port) throws IOException {
        byte[] ackData = new byte[4];
        ackData[0] = 0;
        ackData[1] = OpCode.ACK.getValue();
        ackData[2] = (byte) (blockNumber >> 8);
        ackData[3] = (byte) (blockNumber & 0xFF);
        
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, address, port);
        socket.send(ackPacket);
        
        System.out.println("Sent ACK for block " + blockNumber);
    }
    
    private static int getBlockNumber(byte[] data) {
        return ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
    }
    
    private static class DataPacketResult {
        final boolean success;
        final byte[] data;
        final boolean lastPacket;
        final InetAddress address;
        final int port;
        
        DataPacketResult(boolean success, byte[] data, boolean lastPacket, InetAddress address, int port) {
            this.success = success;
            this.data = data;
            this.lastPacket = lastPacket;
            this.address = address;
            this.port = port;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java TFTPClient <server> <get/put> <filename>");
            return;
        }
        
        try {
            TFTPClient client = new TFTPClient(args[0]);
            
            // Prompt for username and password
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter username: ");
            String username = scanner.nextLine();
            System.out.print("Enter password: ");
            String password = scanner.nextLine();
            
            // Authenticate
            if (!client.authenticate(username, password)) {
                System.out.println("Authentication failed. Exiting.");
                return;
            }
            
            String filename = args[2];
            if ("get".equalsIgnoreCase(args[1])) {
                client.downloadFile(filename);
                System.out.println("File downloaded successfully: " + filename);
            } else if ("put".equalsIgnoreCase(args[1])) {
                client.uploadFile(filename);
                System.out.println("File uploaded successfully: " + filename);
            } else {
                System.out.println("Invalid operation. Use 'get' or 'put'.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

enum OpCode {
    RRQ((byte) 1),
    WRQ((byte) 2),
    DATA((byte) 3),
    ACK((byte) 4);
    
    private final byte value;
    
    OpCode(byte value) {
        this.value = value;
    }
    
    public byte getValue() {
        return value;
    }
}