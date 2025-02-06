import com.sun.jna.Library;
import com.sun.jna.Native;
import org.xerial.snappy.Snappy;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class NetworkScreenServer extends JFrame {
    // Ports for the three channels (ensure these match the client)
    private static final int SERVER_PORT = 9090;
    private static final int SERVER_CURSOR_PORT = SERVER_PORT - 1;   // 8079
    private static final int SERVER_KEYBOARD_PORT = SERVER_PORT - 2; // 8078

    // Constants for mouse and keyboard events
    private static final int MOUSE_MOVE = 1;
    private static final int MOUSE_PRESSED = 2;
    private static final int MOUSE_RELEASED = 3;
    private static final int MOUSE_DOWN_WHEEL = 4;
    private static final int MOUSE_UP_WHEEL = 5;
    private static final int KEY_PRESSED = 6;
    private static final int KEY_RELEASED = 7;
    private static final int KEY_CHANGE_LANGUAGE = 8; // (optional)

    // UI Components (Control Panel)
    private JTextField widthTextField;
    private JTextField heightTextField;
    private JRadioButton compressTrueRBtn;
    private JRadioButton compressFalseRBtn;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;

    // Configuration parameters for image sending
    private int newWidth = 1920;
    private int newHeight = 1080;
    private boolean isCompress = true;

    // Executor for running background tasks
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Robot for capturing screen and simulating events
    private Robot robot;
    private Rectangle screenRect;

    // Server sockets and client sockets
    private ServerSocket imageServerSocket;
    private ServerSocket cursorServerSocket;
    private ServerSocket keyboardServerSocket;
    private Socket imageSocket;
    private Socket cursorSocket;
    private Socket keyboardSocket;

    // Running flag for tasks
    private volatile boolean isRunning = false;

    // Main control panel
    private final ControlPanel controlPanel = new ControlPanel();

    public NetworkScreenServer() {
        super("Network Screen Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 180);
        setResizable(false);
        setLocationRelativeTo(null);
        setContentPane(controlPanel);

        // Initialize the Robot and the screen capture rectangle
        try {
            robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            screenRect = new Rectangle(screenSize);
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Unable to initialize screen capture.", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        setVisible(true);
    }

    /**
     * ControlPanel is the UI that allows you to specify parameters and start/stop the server.
     */
    private class ControlPanel extends JPanel {
        public ControlPanel() {
            setLayout(null);
            startBtn = new JButton("Start");
            stopBtn = new JButton("Stop");
            stopBtn.setEnabled(false);
            widthTextField = new JTextField("1920", 5);
            heightTextField = new JTextField("1080", 5);
            compressTrueRBtn = new JRadioButton("Compress", true);
            compressFalseRBtn = new JRadioButton("No Compress", false);
            ButtonGroup compressGroup = new ButtonGroup();
            compressGroup.add(compressTrueRBtn);
            compressGroup.add(compressFalseRBtn);

            // Set bounds for components
            startBtn.setBounds(20, 20, 120, 40);
            stopBtn.setBounds(150, 20, 120, 40);
            JLabel widthLabel = new JLabel("Width:");
            widthLabel.setBounds(20, 80, 50, 30);
            widthTextField.setBounds(70, 80, 70, 30);
            JLabel heightLabel = new JLabel("Height:");
            heightLabel.setBounds(150, 80, 50, 30);
            heightTextField.setBounds(200, 80, 70, 30);
            compressTrueRBtn.setBounds(280, 20, 120, 40);
            compressFalseRBtn.setBounds(280, 70, 120, 40);
            statusLabel = new JLabel("Status: Idle");
            statusLabel.setBounds(20, 120, 300, 30);

            add(startBtn);
            add(stopBtn);
            add(widthLabel);
            add(widthTextField);
            add(heightLabel);
            add(heightTextField);
            add(compressTrueRBtn);
            add(compressFalseRBtn);
            add(statusLabel);

            startBtn.addActionListener(e -> startServer());
            stopBtn.addActionListener(e -> stopServer());
        }
    }

    /**
     * Starts the server tasks and waits for client connections.
     */
    private void startServer() {
        if (isRunning) return;
        try {
            newWidth = Integer.parseInt(widthTextField.getText().trim());
            newHeight = Integer.parseInt(heightTextField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid dimensions", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        isCompress = compressTrueRBtn.isSelected();

        // Disable controls while running
        widthTextField.setEditable(false);
        heightTextField.setEditable(false);
        compressTrueRBtn.setEnabled(false);
        compressFalseRBtn.setEnabled(false);
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        statusLabel.setText("Status: Waiting for client connection...");
        isRunning = true;

        // Submit tasks for each channel
        executorService.submit(new ImageTask());
        executorService.submit(new CursorTask());
        executorService.submit(new KeyboardTask());
    }

    /**
     * Stops the server tasks and closes sockets.
     */
    private void stopServer() {
        if (!isRunning) return;
        isRunning = false;
        try {
            if (imageServerSocket != null && !imageServerSocket.isClosed()) {
                imageServerSocket.close();
            }
            if (cursorServerSocket != null && !cursorServerSocket.isClosed()) {
                cursorServerSocket.close();
            }
            if (keyboardServerSocket != null && !keyboardServerSocket.isClosed()) {
                keyboardServerSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        widthTextField.setEditable(true);
        heightTextField.setEditable(true);
        compressTrueRBtn.setEnabled(true);
        compressFalseRBtn.setEnabled(true);
        statusLabel.setText("Status: Stopped");
    }

    /**
     * ImageTask accepts the image client connection, sends initialization parameters,
     * and continuously captures, scales, compresses (if enabled), and sends screen images.
     */
    private class ImageTask implements Runnable {
        @Override
        public void run() {
            try {
                imageServerSocket = new ServerSocket(SERVER_PORT);
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Status: Waiting for image client..."));
                imageSocket = imageServerSocket.accept();
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Status: Image client connected"));

                DataOutputStream dataOutputStream = new DataOutputStream(imageSocket.getOutputStream());
                // Send initialization parameters to the client
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                int screenWidth = screenSize.width;
                int screenHeight = screenSize.height;
                dataOutputStream.writeInt(screenWidth);
                dataOutputStream.writeInt(screenHeight);
                dataOutputStream.writeInt(newWidth);
                dataOutputStream.writeInt(newHeight);
                dataOutputStream.writeBoolean(isCompress);
                dataOutputStream.flush();
                System.out.println("Sent init params: " + screenWidth + "x" + screenHeight +
                        ", scaled to: " + newWidth + "x" + newHeight +
                        ", compress=" + isCompress);

                // Continuously capture and send screen images
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    BufferedImage capture = robot.createScreenCapture(screenRect);
                    BufferedImage scaled = getScaledImage(capture, newWidth, newHeight, BufferedImage.TYPE_3BYTE_BGR);
                    byte[] imageBytes = ((DataBufferByte) scaled.getRaster().getDataBuffer()).getData();
                    byte[] outputBytes = isCompress ? Snappy.compress(imageBytes) : imageBytes;
                    System.out.println("Sending frame with byte length: " + outputBytes.length);
                    dataOutputStream.writeInt(outputBytes.length);
                    dataOutputStream.write(outputBytes);
                    dataOutputStream.flush();
                    Thread.sleep(30); // Adjust sleep to control frame rate
                }
            } catch (IOException | InterruptedException ex) {
                if (isRunning) {
                    ex.printStackTrace();
                }
            } finally {
                try {
                    if (imageSocket != null && !imageSocket.isClosed()) {
                        imageSocket.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * CursorTask accepts the cursor client connection, listens for incoming mouse events,
     * and uses Robot to simulate those events on the server.
     */
    private class CursorTask implements Runnable {
        @Override
        public void run() {
            try {
                cursorServerSocket = new ServerSocket(SERVER_CURSOR_PORT);
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Status: Waiting for cursor client..."));
                cursorSocket = cursorServerSocket.accept();
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Status: Cursor client connected"));

                DataInputStream dis = new DataInputStream(cursorSocket.getInputStream());
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    int mouseEvent = dis.readInt();
                    switch (mouseEvent) {
                        case MOUSE_MOVE:
                            int mouseX = dis.readInt();
                            int mouseY = dis.readInt();
                            System.out.println("Cursor move to: " + mouseX + ", " + mouseY);
                            robot.mouseMove(mouseX, mouseY);
                            break;
                        case MOUSE_PRESSED:
                            int button = dis.readInt();
                            System.out.println("Cursor pressed button: " + button);
                            robot.mousePress(getButtonMask(button));
                            break;
                        case MOUSE_RELEASED:
                            button = dis.readInt();
                            System.out.println("Cursor released button: " + button);
                            robot.mouseRelease(getButtonMask(button));
                            break;
                        case MOUSE_DOWN_WHEEL:
                            robot.mouseWheel(-3);
                            break;
                        case MOUSE_UP_WHEEL:
                            robot.mouseWheel(3);
                            break;
                        default:
                            System.out.println("Unknown mouse event: " + mouseEvent);
                            break;
                    }
                }
            } catch (IOException ex) {
                if (isRunning) {
                    ex.printStackTrace();
                }
            } finally {
                try {
                    if (cursorSocket != null && !cursorSocket.isClosed()) {
                        cursorSocket.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        // Converts a mouse button number to the appropriate mask for Robot.
        private int getButtonMask(int button) {
            switch (button) {
                case 1:
                    return MouseEvent.BUTTON1_DOWN_MASK;
                case 2:
                    return MouseEvent.BUTTON2_DOWN_MASK;
                case 3:
                    return MouseEvent.BUTTON3_DOWN_MASK;
                default:
                    return 0;
            }
        }
    }

    /**
     * KeyboardTask accepts the keyboard client connection, listens for incoming keyboard events,
     * and simulates key presses/releases using Robot.
     */
    private class KeyboardTask implements Runnable {
        @Override
        public void run() {
            try {
                keyboardServerSocket = new ServerSocket(SERVER_KEYBOARD_PORT);
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Status: Waiting for keyboard client..."));
                keyboardSocket = keyboardServerSocket.accept();
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Status: Keyboard client connected"));

                DataInputStream dis = new DataInputStream(keyboardSocket.getInputStream());
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    int keyEvent = dis.readInt();
                    int keyCode = dis.readInt();
                    System.out.println("Keyboard event: " + keyEvent + ", keyCode: " + keyCode);
                    switch (keyEvent) {
                        case KEY_PRESSED:
                            robot.keyPress(keyCode);
                            break;
                        case KEY_RELEASED:
                            robot.keyRelease(keyCode);
                            break;
                        default:
                            System.out.println("Unknown keyboard event: " + keyEvent);
                            break;
                    }
                }
            } catch (IOException ex) {
                if (isRunning) {
                    ex.printStackTrace();
                }
            } finally {
                try {
                    if (keyboardSocket != null && !keyboardSocket.isClosed()) {
                        keyboardSocket.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * Utility method for scaling a BufferedImage.
     *
     * @param src       the source image
     * @param width     target width
     * @param height    target height
     * @param imageType the image type (e.g. BufferedImage.TYPE_3BYTE_BGR)
     * @return the scaled image
     */
    private BufferedImage getScaledImage(BufferedImage src, int width, int height, int imageType) {
        BufferedImage scaled = new BufferedImage(width, height, imageType);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, width, height, null);
        g2d.dispose();
        return scaled;
    }

    /**
     * Optional: A JNA interface for simulating keyboard events on Windows.
     * Currently, the Robot is used for simulating key events. Uncomment and modify if needed.
     */
    public interface User32jna extends Library {
        User32jna INSTANCE = (User32jna) Native.load("user32.dll", User32jna.class);
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(NetworkScreenServer::new);
    }
}
