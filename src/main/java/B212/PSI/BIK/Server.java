package B212.PSI.BIK;

import java.io.*;
import java.net.*;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Scanner;

import static java.lang.Math.max;


/**
 * Holds robot states and operating methods in this states
 */
class AuthKeyPair {
    final private int ServerKey;
    final private int ClientKey;

    public AuthKeyPair(int serverKey, int clientKey) {
        ServerKey = serverKey;
        ClientKey = clientKey;
    }

    public int getServerKey() {
        return ServerKey;
    }

    public int getClientKey() {
        return ClientKey;
    }


}

/**
 * Contains defined server-client key pairs for validation
 */
interface AuthKey {
    Map<Integer, AuthKeyPair> AUTH_KEYS = Map.of(
            0, new AuthKeyPair(23019, 32037),
            1, new AuthKeyPair(32037, 29295),
            2, new AuthKeyPair(18789, 13603),
            3, new AuthKeyPair(16443, 29533),
            4, new AuthKeyPair(18189, 21952)
    );
}

/**
 * Contains predefined server messages constants
 */
interface ServerMessageText {

    String SERVER_CONFIRMATION = "";
    String SERVER_MOVE = "102 MOVE";
    String SERVER_TURN_LEFT = "103 TURN LEFT";
    String SERVER_TURN_RIGHT = "104 TURN RIGHT";
    String SERVER_PICK_UP = "105 GET MESSAGE";
    String SERVER_LOGOUT = "106 LOGOUT";
    String SERVER_KEY_REQUEST = "107 KEY REQUEST";
    String SERVER_OK = "200 OK";
    String SERVER_LOGIN_FAILED = "300 LOGIN FAILED";
    String SERVER_SYNTAX_ERROR = "301 SYNTAX ERROR";
    String SERVER_LOGIC_ERROR = "302 LOGIC ERROR";
    String SERVER_KEY_OUT_OF_RANGE_ERROR = "303 KEY OUT OF RANGE";
}

/**
 * Contains defined timeout constants
 */
interface Timeouts {
    int TIMEOUT = 1000;
    int TIMEOUT_RECHARGING = 5000;
}

/**
 * Base class for messages.
 */
class Message {
    protected final String text;

    public Message(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}

/**
 * Class representing server message.
 */
class ServerMessage extends Message {

    private final String code;

    /**
     * Constructor for creating general message
     *
     * @param text Message text - from constants
     */
    public ServerMessage(String text) {
        super(text);
        this.code = "";
    }

    /**
     * Constructor for creating message with return code for authentication
     *
     * @param text Message text - from constants
     * @param code Calculated auth code
     */
    public ServerMessage(String text, int code) {
        super(text);
        this.code = "" + code;
    }

    /**
     * Format message text for sending
     *
     * @return Formatted message string
     */
    public String getMessage() {
        return text + code + "\u0007\u0008";
    }
}

/**
 * Class representing client message.
 */
class ClientMessage extends Message {

    public ClientMessage(String text) {
        super(text);
    }

    public String getMessageText() {
        return super.text;
    }

}

/**
 * Wrapper for sending and receiving all messages
 */
class Messenger {
    private final int MAX_LENGTH = 12;
    private final String RECHARGING = "RECHARGING";
    private final String FULL_POWER = "FULL POWER";
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;

    public Messenger(BufferedReader reader, PrintWriter writer, Socket socket) {
        this.reader = reader;
        this.writer = writer;
        this.socket = socket;
    }

    /**
     * Parses message from socket and check ending
     *
     * @param max max expected length of message
     * @return Instance of ClientMessage class if is successfully read
     * @throws IOException in case there is syntax error in message
     */
    private ClientMessage parseMessage(int max) throws IOException {
        max = max(max, MAX_LENGTH);
        char[] charMessage = new char[max];
        boolean flagA = false;
        int length = 0;
        for (int i = 0; i < max; ++i) {
            int in = reader.read();
            ++length;
            //check end of stream
            if (in < 0) break;
            //red into array
            charMessage[i] = (char) in;
            //check \a\b and break or set flags
            if (in == '\u0008' && flagA) break;
            if (in == '\u0007') flagA = true;
            if (in != '\u0007' && in != '\u0008') flagA = false;
        }


        //check proper end
        if (charMessage[length - 2] != '\u0007' || charMessage[length - 1] != '\u0008') {
            send(new ServerMessage(ServerMessageText.SERVER_SYNTAX_ERROR));
            System.out.println("Server: CLIENT MESSAGE BAD ENDING");
            throw new IOException("Server: BAD CLIENT MESSAGE");
        }

        String newMessage = new String(charMessage, 0, length - 2);

        System.out.println("Server received: " + newMessage);


        return new ClientMessage(newMessage);
    }

    /**
     * Receives message - by calling parseMessage method - and executes whole recharging logic.
     *
     * @param max Max expected length of message.
     * @return Instance of ClientMessage class.
     * @throws IOException If IOException occurred in nested calls, or recharging failed.
     */
    private ClientMessage receiveMessage(int max) throws IOException {
        ClientMessage message = parseMessage(max);

        if (message.text.equals(RECHARGING)) {
            socket.setSoTimeout(Timeouts.TIMEOUT_RECHARGING); //set recharging timeout
            message = parseMessage(max);
            if (!message.text.equals(FULL_POWER)) {
                send(new ServerMessage(ServerMessageText.SERVER_LOGIC_ERROR));
                System.out.println("Server: LOGIC ERROR - Client communicate during recharging.");
                throw new IOException("Server: LOGIC ERROR - Client communicate during recharging.");
            }

            socket.setSoTimeout(Timeouts.TIMEOUT); //set back standard timeout
            message = parseMessage(max);
        } else if (message.text.equals(FULL_POWER)) {
            send(new ServerMessage(ServerMessageText.SERVER_LOGIC_ERROR));
            System.out.println("Server: LOGIC ERROR - Client ends RECHARGING without started.");
            throw new IOException("Server: LOGIC ERROR - Client ends RECHARGING without started.");
        }

        return message;
    }

    /**
     * Sends message
     *
     * @param message Message to be sent
     * @throws IOException If message cannot be sent.
     */
    public void send(ServerMessage message) throws IOException {
        writer.print(message.getMessage());
        writer.flush();
        System.out.println("Server sending: " + message.getMessage());
    }


    // -------- AUTH METHODS ---------------------------

    /**
     * Reads expected message with name.
     *
     * @return String contains name obtained from message.
     * @throws IOException If wrong message or other IO error occurred.
     */
    public String readName() throws IOException {
        ClientMessage message = receiveMessage(20);
        //validate
        if (message.getMessageText().length() > 18) {
            throw new IOException("Server: BAD CLIENT_USERNAME");
        }
        return message.getMessageText();
    }

    /**
     * Reads expected message with client key.
     *
     * @return int contains client key from message.
     * @throws IOException If wrong message or other IO error occurred.
     */
    public int readKey() throws IOException {
        //check length
        ClientMessage message = receiveMessage(5);
        if (message.getMessageText().length() > 3) {
            System.out.println("Server: BAD CLIENT_KEY_ID format");
            throw new IOException("Server: BAD CLIENT_KEY_ID format");
        }

        //check integer
        int key;
        try {
            key = Integer.parseInt(message.getMessageText());
        } catch (NumberFormatException e) {
            send(new ServerMessage(ServerMessageText.SERVER_SYNTAX_ERROR));
            System.out.println("Server: BAD KEY_ID - NOT NUMERIC");
            throw new IOException("Server: BAD KEY_ID - NOT NUMERIC");
        }

        if (!AuthKey.AUTH_KEYS.containsKey(key)) {
            send(new ServerMessage(ServerMessageText.SERVER_KEY_OUT_OF_RANGE_ERROR));
            System.out.println("Server: BAD KEY_ID - NOT IN RANGE");
            throw new IOException("Server: BAD KEY_ID");
        }

        return key;
    }

    /**
     * Reads expected message with confirmation key.
     *
     * @return int contains confirmation key from message.
     * @throws IOException If wrong message or other IO error occurred.
     */
    public int readConfirmation() throws IOException {

        ClientMessage message = receiveMessage(7);
        if (message.getMessageText().length() > 5) {
            send(new ServerMessage(ServerMessageText.SERVER_SYNTAX_ERROR));
            System.out.println("Server: BAD CLIENT_CONFIRMATION format");
            throw new IOException("Server: BAD CLIENT_CONFIRMATION format");
        }

        int key;
        try {
            key = Integer.parseInt(message.getMessageText());
        } catch (NumberFormatException e) {
            send(new ServerMessage(ServerMessageText.SERVER_SYNTAX_ERROR));
            System.out.println("Server: BAD CLIENT_CONFIRMATION KEY - NOT NUMERIC");
            throw new IOException("Server: BAD CLIENT_CONFIRMATION KEY - NOT NUMERIC");
        }
        return key;
    }

    // -------- MOVE METHODS ---------------------------

    /**
     * Reads expected message with move information.
     *
     * @return Instance of RobotPosition class.
     * @throws IOException If wrong message or other IO error occurred.
     */
    public RobotPosition readMove() throws IOException {
        ClientMessage message = receiveMessage(12);

        RobotPosition position;
        try {
            Scanner s = new Scanner(message.getMessageText());
            String ok = s.next();
            if (!ok.equals("OK")) throw new IOException("Server: BAD CLIENT_POSITION format");
            int x = s.nextInt();
            int y = s.nextInt();
            position = new RobotPosition(x, y);
            s.useDelimiter("");
            if (s.hasNext()) throw new IOException("Server: BAD CLIENT_POSITION format");
        } catch (InputMismatchException | NumberFormatException | IOException e) {
            send(new ServerMessage(ServerMessageText.SERVER_SYNTAX_ERROR));
            System.out.println("Server: BAD CLIENT_POSITION - COORDINATES NOT CORRECT");
            throw new IOException("Server: BAD CLIENT_POSITION - COORDINATES NOT CORRECT");
        }

        return position;
    }

    // -------- PICK UP ---------------------------

    /**
     * Reads final message on target position
     *
     * @return String contains final message text.
     * @throws IOException If wrong message or other IO error occurred.
     */
    public String readSecretMessage() throws IOException {
        ClientMessage message = receiveMessage(100);
        //validate
        if (message.getMessageText().length() > 98) {
            throw new IOException("Server: BAD PICKED UP MESSAGE");
        }
        return message.getMessageText();
    }

}

/**
 * Container for robot basic robot position attributes and orientation methods
 */
interface RobotOrientation {

    RobotOrientation turnRight();

    RobotOrientation turnLeft();

    /**
     * X+ orientation
     */
    RobotOrientation XP = new RobotOrientation() {
        @Override
        public RobotOrientation turnRight() {
            return YN;
        }

        @Override
        public RobotOrientation turnLeft() {
            return YP;
        }

        @Override
        public String toString() {
            return "X+";
        }
    };

    /**
     * X- orientation
     */
    RobotOrientation XN = new RobotOrientation() {
        @Override
        public RobotOrientation turnRight() {
            return YP;
        }

        @Override
        public RobotOrientation turnLeft() {
            return YN;
        }

        @Override
        public String toString() {
            return "X-";
        }
    };

    /**
     * Y+ orientation
     */
    RobotOrientation YP = new RobotOrientation() {
        @Override
        public RobotOrientation turnRight() {
            return XP;
        }

        @Override
        public RobotOrientation turnLeft() {
            return XN;
        }

        @Override
        public String toString() {
            return "Y+";
        }
    };

    /**
     * Y- orientation
     */
    RobotOrientation YN = new RobotOrientation() {
        @Override
        public RobotOrientation turnRight() {
            return XN;
        }

        @Override
        public RobotOrientation turnLeft() {
            return XP;
        }

        @Override
        public String toString() {
            return "Y-";
        }
    };

    default String toString(String s) {
        return null;
    }
}

/**
 * Class representing robot position
 */
class RobotPosition {
    private final int x;
    private final int y;

    public RobotPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isTarget() {
        return x == 0 && y == 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RobotPosition position) {
            return x == position.getX() && y == position.getY();
        }
        return false;
    }
}

/**
 * Class representing one client-robot in its whole lifecycle
 */
class Robot {
    //authentication parameters
    private String name;

    //moving parameters
    private RobotPosition position;
    private RobotOrientation orientation;


    public String getName() {
        return name;
    }

    public void setName(String userName) {
        name = userName;
    }

    public RobotPosition getPosition() {
        return position;
    }

    public void setPosition(RobotPosition position) {
        this.position = position;
    }

    public RobotOrientation getOrientation() {
        return orientation;
    }

    public void setOrientation(RobotOrientation orientation) {
        this.orientation = orientation;
    }

    public boolean reachedTarget() {
        return position.isTarget();
    }

    public void turnRight() {
        orientation = orientation.turnRight();
    }

    public void turnLeft() {
        orientation = orientation.turnLeft();
    }

}

/**
 * Class wrapping messaging and robot together - auth and moving with robot based on messages from messenger.
 */
class Puppeteer implements ServerMessageText {
    private final Robot robot;
    private final Messenger messenger;
    private int ClientKey;
    private int ServerKey;
    private int hash;
    private String secret;


    public Puppeteer(Messenger messenger) {
        this.robot = new Robot();
        this.messenger = messenger;
    }

    private int calculateHash() {
        hash = 0;
        for (var i : robot.getName().toCharArray())
            hash += i;
        return (hash *= 1000) % 65536;
    }

    private int calculateConfHash(int key) {
        int ClientHash = (key - ClientKey + 65536) % 65536;
        System.out.println("Server: HASH FROM CLIENT CODE {" + ClientHash + "}");

        return ClientHash;
    }

    /**
     * Provide Authenticate process
     *
     * @return True if success, false otherwise.
     * @throws IOException In case of messaging error.
     */
    boolean authenticate() throws IOException {
        //get name
        robot.setName(messenger.readName());

        // key request + obtain
        messenger.send(new ServerMessage(SERVER_KEY_REQUEST));
        int keyID = messenger.readKey();
        ClientKey = AuthKey.AUTH_KEYS.get(keyID).getClientKey();
        ServerKey = AuthKey.AUTH_KEYS.get(keyID).getServerKey();

        // send hash for check
        hash = calculateHash();
        System.out.println("SERVER: KEY_ID {" + keyID + "} | CLIENT_KEY {" + ClientKey + "} | SERVER_KEY {" + ServerKey + "} | NAME {" + robot.getName() + "} | HASH {" + hash + "}");
        messenger.send(new ServerMessage(SERVER_CONFIRMATION, (hash + ServerKey) % 65536));

        //final check of robot hash
        if (hash == calculateConfHash(messenger.readConfirmation())) {
            messenger.send(new ServerMessage(SERVER_OK));
            return true;
        } else {
            messenger.send(new ServerMessage(SERVER_LOGIN_FAILED));
            return false;
        }
    }

    /**
     * Provide moving process
     *
     * @return True if success, false otherwise.
     * @throws IOException In case of messaging error.
     */
    public boolean move() throws IOException {

        //Initialization of moving - get position and basic orientation.
        if (moveInit()) {
            return true;
        }

        //moving loop
        while (!robot.reachedTarget()) {
            messenger.send(new ServerMessage(ServerMessageText.SERVER_MOVE));
            RobotPosition newPosition = messenger.readMove();

            //turn on obstacle
            if (newPosition.equals(robot.getPosition())) {
                System.out.println("Server: DETECTED OBSTACLE");

                //detected if obstacle was on coordinate 0 for one direction
                boolean zeroObstacle = robot.getPosition().getX() == 0 || robot.getPosition().getY() == 0;

                turn(); // TURN ROBOT
                messenger.send(new ServerMessage(ServerMessageText.SERVER_MOVE)); //MAKE MOVE
                if (zeroObstacle) turnInit(); // TURN BACK if move was from 0 position
                newPosition = messenger.readMove();
            }

            //set new position
            robot.setPosition(newPosition);
            System.out.println("Server: Robot position " + robot.getPosition().getX() + " | " + robot.getPosition().getY());

            //check if turn
            if ((robot.getPosition().getX() == 0 && (robot.getOrientation() == RobotOrientation.XN || robot.getOrientation() == RobotOrientation.XP))
                    || (robot.getPosition().getY() == 0 && (robot.getOrientation() == RobotOrientation.YN || robot.getOrientation() == RobotOrientation.YP))) {
                if (!turn()) {
                    return false;
                }
            }

        }
        return true;
    }

    /**
     * Provide initial robot moving and orientation.
     *
     * @return True if success, false otherwise.
     * @throws IOException In case of messaging error.
     */
    private boolean moveInit() throws IOException {
        RobotPosition firstPosition;
        RobotPosition secPosition;

        //double move to get two position messages
        messenger.send(new ServerMessage(ServerMessageText.SERVER_MOVE));
        firstPosition = messenger.readMove();
        if (firstPosition.isTarget()) {
            return true;
        }
        messenger.send(new ServerMessage(ServerMessageText.SERVER_MOVE));
        secPosition = messenger.readMove();
        if (secPosition.isTarget()) return true;

        //check if there was obstacle, if so, turn left and move again (can't determine which turn is better, so LEFT everytime - could be random as well)
        if (firstPosition.equals(secPosition)) {
            System.out.println("Server: TWO FIRST POSITIONS ARE SAME - Obstacle in orientation phase.");
            messenger.send(new ServerMessage(ServerMessageText.SERVER_TURN_LEFT));
            firstPosition = messenger.readMove();
            if (firstPosition.isTarget()) return true;
            messenger.send(new ServerMessage(ServerMessageText.SERVER_MOVE));
            secPosition = messenger.readMove();
            if (secPosition.isTarget()) return true;
        }

        //set new position and orientation
        robot.setPosition(secPosition);
        robot.setOrientation(orientInit(firstPosition, secPosition));


        //Starting orienting by one or two turns (two needed in case of one position 0 and direction from center)
        turnInit();
        turnInit();

        return false;
    }

    /**
     * Provide robot turn on obstacle - turn against second coordinate.
     *
     * @return True if success, false otherwise.
     * @throws IOException In case of messaging error.
     */
    private boolean turn() throws IOException {
        if (robot.getPosition().getY() >= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.XN ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.XP ||
                robot.getPosition().getY() >= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.YN ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.YP
        ) {
            //TURN LEFT
            messenger.send(new ServerMessage(ServerMessageText.SERVER_TURN_LEFT));
            messenger.readMove();
            robot.turnLeft();
            System.out.println("Server: Robot reoriented to " + robot.getOrientation().toString());
            return true;
        }

        if (robot.getPosition().getY() >= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.YN ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.YP ||
                robot.getPosition().getY() >= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.XP ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.XN
        ) {
            //TURN RIGHT
            messenger.send(new ServerMessage(ServerMessageText.SERVER_TURN_RIGHT));
            messenger.readMove();
            robot.turnRight();
            System.out.println("Server: Robot reoriented to " + robot.getOrientation().toString());
        }

        return true;
    }

    /**
     * Provide robot initial turn - turn against bigger coordinate.
     *
     * @throws IOException In case of messaging error.
     */
    private void turnInit() throws IOException {
        if (robot.getPosition().getY() >= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.YP ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.YN ||
                robot.getPosition().getY() >= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.XN ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.XP
        ) {
            //TURN LEFT
            messenger.send(new ServerMessage(ServerMessageText.SERVER_TURN_LEFT));
            messenger.readMove();
            robot.turnLeft();
            System.out.println("Server: Robot Oriented to " + robot.getOrientation().toString());
        }

        if (robot.getPosition().getY() >= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.XP ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.XN ||
                robot.getPosition().getY() >= 0 && robot.getPosition().getX() <= 0 && robot.getOrientation() == RobotOrientation.YP ||
                robot.getPosition().getY() <= 0 && robot.getPosition().getX() >= 0 && robot.getOrientation() == RobotOrientation.YN
        ) {
            //TURN RIGHT
            messenger.send(new ServerMessage(ServerMessageText.SERVER_TURN_RIGHT));
            messenger.readMove();
            robot.turnRight();
            System.out.println("Server: Robot Oriented to " + robot.getOrientation().toString());
        }
    }

    /**
     * Get orientation from initial moves - two positions.
     *
     * @param first  First position
     * @param second Second position
     * @return Instance of RobotOrientation class
     */
    public RobotOrientation orientInit(RobotPosition first, RobotPosition second) {
        RobotOrientation orientation = null;

        if (first.getX() == second.getX()) {
            if (first.getY() > second.getY())
                orientation = RobotOrientation.YN;
            else
                orientation = RobotOrientation.YP;
        }
        if (first.getY() == second.getY()) {
            if (first.getX() > second.getX())
                orientation = RobotOrientation.XN;
            else
                orientation = RobotOrientation.XP;
        }

        return orientation;

    }

    /**
     * Pick up text message hidden in target position
     *
     * @return True if success, false otherwise.
     * @throws IOException In case of messaging error.
     */
    public boolean pick() throws IOException {
        messenger.send(new ServerMessage(ServerMessageText.SERVER_PICK_UP));
        secret = messenger.readSecretMessage();
        return true;
    }

    public String getSecret() {
        return secret;
    }
}

//**********************************************************************************************************************

/**
 * Main class with access point of main method
 */
public class Server {

    public static void main(String[] args) throws InterruptedException {

        Listener listener = new Listener(1111);
        Thread t_listener = new Thread(listener);
        t_listener.start();
        t_listener.join();
    }

    /**
     * Class providing listener for creating connection with robots-clients
     */
    static class Listener implements Runnable {
        private ServerSocket ss;

        public Listener(int port) {
            try {
                ss = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Method in endless loop listening on port.
         * If there is a connection request, creates new service in new thread for further communication.
         */
        @Override
        public void run() {
            try {
                System.out.println("-----------------------------------------------------");
                System.out.println("Server - service started");
                System.out.println("-----------------------------------------------------");

                while (true) { //endless loop
                    Socket socket = ss.accept();
                    Service service = new Service(socket);
                    (new Thread(service)).start();
                }
            } catch (Exception ex) {
                System.out.println("-----------------------------------------------------");
                System.out.println("Server - service exception reached");
                System.out.println("-----------------------------------------------------");
            }
        }
    }

    /**
     * Class providing all lifecycle process for one separated robot-client
     */
    static class Service implements Runnable {
        private final Socket socket;
        Puppeteer puppeteer;


        public Service(Socket socket) {
            this.socket = socket;
        }

        /**
         * Method provides all actions with robot in separated thread.
         */
        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                //set default timeout before cutting connection
                socket.setSoTimeout(Timeouts.TIMEOUT);

                //creates instance of messenger and puppeteer
                Messenger messenger = new Messenger(reader, writer, socket);
                puppeteer = new Puppeteer(messenger);

                //auth process
                if (!puppeteer.authenticate())
                    return;

                //moving process
                if (!puppeteer.move())
                    return;

                System.out.println("Server: Target reached!");

                // picking target message
                if (!puppeteer.pick())
                    return;

                System.out.println(puppeteer.getSecret());

                //robot-client logout
                messenger.send(new ServerMessage(ServerMessageText.SERVER_LOGOUT));

            } catch (Exception ex) {
                System.out.println("Server: Service exception reached!");
            }
        }
    }
}