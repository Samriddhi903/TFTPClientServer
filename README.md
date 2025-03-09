# TFTP Client-Server Implementation in Java

## Setup Requirements

### Prerequisites
- **Java Development Kit (JDK)** 8 or higher
- **Git**
- **VMware/VirtualBox** (or any virtualization software)
- **Two Virtual Machines (VMs)** running **Ubuntu**

---

## Installation Steps

### Clone the Repository on Both VMs
```bash
git clone https://github.com/Samriddhi903/TFTPClientServer.git
cd TFTPClientServer
```

### Server Setup
1. **Create `credentials.txt` file:** This file must contain valid username-password pairs, e.g.:
```plaintext
user1:password1
user2:password2
```

2. **Compile and Run the Server**
```bash
javac TFTPServer.java
java TFTPServer
```

### Client Setup
1. **Compile and Run the Client**
```bash
javac TFTPClient.java
java TFTPClient
```

---

## Usage Instructions
1. **Ensure Server is Running:** Start the server on one VM.
2. **Authenticate via Client:** Enter valid credentials from `credentials.txt`.
3. **Select Operation:** Choose to **upload** or **download** files.
4. **File Transfer:** Monitor the transfer status in the terminal.

---

## Project Structure
```bash
TFTPClientServer/
├── client_files/          # Files for client access
├── server_files/          # Files stored on the server
├── credentials.txt        # Server-side credentials file
├── TFTPServer.java        # Server implementation
├── TFTPClient.java        # Client implementation
└── README.md              # Project documentation
```

---

## Additional Notes
- Ensure the **VMs are networked properly** (use **NAT** or **Bridged** mode).
- The **server VM** must have **port 69 (TFTP)** open and accessible.
- Verify that both **VMs** can **ping** each other before starting the server and client.

---

## Contributing
1. **Fork the repository**
2. **Create a new branch**
```bash
git checkout -b feature-branch
```
3. **Commit your changes**
```bash
git commit -m 'Add new feature'
```
4. **Push to the branch**
```bash
git push origin feature-branch
```
5. **Open a pull request**

---

## License
This project is licensed under the **MIT License**.

