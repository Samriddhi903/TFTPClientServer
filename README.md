Setup Requirements
Prerequisites
Java Development Kit (JDK) 8 or higher
Git
VMware/VirtualBox (or any virtualization software)
Two Virtual Machines (VMs) running Ubuntu
Installation Steps
Clone the Repository on Both VMs
bash
Copy
Edit
git clone https://github.com/Samriddhi903/TFTPClientServer.git
cd TFTPClientServer
Server Setup
Create credentials.txt file: This file must contain valid username-password pairs, e.g.:
plaintext
Copy
Edit
user1:password1
user2:password2
Compile and Run the Server
bash
Copy
Edit
javac TFTPServer.java
java TFTPServer
Client Setup
Compile and Run the Client
bash
Copy
Edit
javac TFTPClient.java
java TFTPClient
Usage Instructions
Ensure Server is Running: Start the server on one VM.
Authenticate via Client: Enter valid credentials from credentials.txt.
Select Operation: Choose to upload or download files.
File Transfer: Monitor the transfer status in the terminal.
Project Structure
bash
Copy
Edit
TFTPClientServer/
├── client_files/          # Files for client access
├── server_files/          # Files stored on the server
├── credentials.txt        # Server-side credentials file
├── TFTPServer.java        # Server implementation
├── TFTPClient.java        # Client implementation
└── README.md              # Project documentation
Additional Notes
Ensure the VMs are networked properly (use NAT or Bridged mode).
The server VM must have port 69 (TFTP) open and accessible.
Verify that both VMs can ping each other before starting the server and client.
Contributing
Fork the repository
Create a new branch (git checkout -b feature-branch)
Commit your changes (git commit -m 'Add new feature')
Push to the branch (git push origin feature-branch)
Open a pull request
