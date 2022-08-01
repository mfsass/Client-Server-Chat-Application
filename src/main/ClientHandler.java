import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler implements Runnable {

    public static ArrayList<ClientHandler> clientHandlers = new ArrayList<>();
    private Socket socket;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;
    private String clientUsername;

    public ClientHandler(Socket socket) {
        try {
            this.socket = socket;
           
            this.objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            this.objectInputStream = new ObjectInputStream(socket.getInputStream());

            this.clientUsername = ((Message) objectInputStream.readObject()).text();//waits for message to be sent
        
        } catch (IOException e){
            closeEverything();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void checkUsername() { 
        outer: while (true) {
            for (ClientHandler handler : clientHandlers) {
                if (handler.clientUsername.equals(this.clientUsername)) {
                    try {
                        this.objectOutputStream.writeObject(new Message("Username already exists!\nEnter your username: ", "SERVER"));
                        this.objectOutputStream.flush();

                        this.clientUsername = ((Message) objectInputStream.readObject()).text();

                    } catch (IOException e) {
                        closeEverything();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    continue outer;
                }
            }
            break;
        }
        clientHandlers.add(this);
        sendMessage(new Message(clientUsername + " has entered the chat!","SERVER"));
    }

    @Override
    public void run() {
        // run on every thread
        //thread waiting and sending for each message
        Message messageFromClient;

        while (socket.isConnected()) {
            try{
                messageFromClient = (Message) objectInputStream.readObject();
                if (messageFromClient != null) {
                    if (messageFromClient.text().equals("\\list")) {
                        String text = "List of current users -\n";
                        for (ClientHandler handler : clientHandlers) {
                            text += "        " + handler.clientUsername + "\n";
                        }
                        text = text.substring(0, text.length()-1);
                        Message msg = new Message(text, "SERVER", clientUsername);
                        sendMessage(msg);
                    } else {
                        sendMessage(messageFromClient);
                    }
                }
            } catch (IOException e){
                closeEverything();
                break; //exit while
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                break;
            }
        }

    }

    public void sendMessage(Message messageToSend) {
        if (messageToSend.to() == null) {   // broadcast
            for (ClientHandler clientHandler : clientHandlers){
                try{ 
                    clientHandler.objectOutputStream.writeObject(messageToSend);
                    clientHandler.objectOutputStream.flush();//manual clear before it fills   
                } catch (IOException e){
                    closeEverything();
                }
            }
        } else {    // whisper
            ClientHandler user = null;
            for (ClientHandler clientHandler : clientHandlers){
                if (clientHandler.clientUsername.equals(messageToSend.to())) {
                    user = clientHandler;
                }
            }
            if (user != null) {
                try{   
                    user.objectOutputStream.writeObject(messageToSend);
                    user.objectOutputStream.flush();//manual clear before it fills

                    if (!user.equals(this)) {
                        this.objectOutputStream.writeObject(messageToSend);
                        this.objectOutputStream.flush();
                    }
                } catch (IOException e){
                    closeEverything();
                }
            } else {
                try {
                    this.objectOutputStream.writeObject(new Message("User, \"" + messageToSend.to() + "\", does not exist.", "SERVER"));
                    this.objectOutputStream.flush();
                } catch (IOException e) {
                    closeEverything();
                }
            }
        }
    }

    public void closeEverything() {
        removeClientHandler();
        try {
            if (objectInputStream != null) {
                objectInputStream.close();
            }
        } catch (IOException e) {}

        try {
            if (objectOutputStream != null) {
                objectOutputStream.close();
            }
        } catch (IOException e) {}

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {}
    }

    public void removeClientHandler() {
        clientHandlers.remove(this);
        sendMessage(new Message(clientUsername + " has left the chat!","SERVER"));
        System.out.println("Client Disconnected!");
    }

}
