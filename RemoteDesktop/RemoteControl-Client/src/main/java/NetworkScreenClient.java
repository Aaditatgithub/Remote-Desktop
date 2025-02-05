
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.xerial.snappy.Snappy;

public class NetworkScreenClient extends JFrame {
	// UI and connection constants
	private static final int FRAME_WIDTH = 500;
	private static final int FRAME_HEIGHT = 110;
	private static final int SERVER_PORT = 9999;
	private static final int SERVER_CURSOR_PORT = SERVER_PORT - 1;
	private static final int SERVER_KEYBOARD_PORT = SERVER_PORT - 2;

	// Main panels for connection and streaming
	private ControlPanel controlPanel;
	private ScreenPanel screenPanel;

	// ExecutorService for background tasks
	private ExecutorService executorService = Executors.newCachedThreadPool();

	public NetworkScreenClient() {
		super("Remote Support - Supportee");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(FRAME_WIDTH, FRAME_HEIGHT);
		setLocation(400, 400);
		setResizable(false);

		// Create and show the control panel
		controlPanel = new ControlPanel();
		setContentPane(controlPanel);

		// Allow window dragging (if undecorated)
		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				setLocation(e.getXOnScreen(), e.getYOnScreen());
			}
		});

		setVisible(true);
	}

	/**
	 * The panel for entering connection details and starting the stream.
	 */
	private class ControlPanel extends JPanel {
		private final String PLACEHOLDER = "123.123.123.123";
		private JTextField addressField;
		private JButton connectBtn;
		private JButton exitBtn;

		public ControlPanel() {
			setLayout(null);
			addressField = new JTextField(PLACEHOLDER);
			connectBtn = new JButton("Connect");
			exitBtn = new JButton("Exit");

			// Set bounds
			addressField.setBounds(0, 0, 200, FRAME_HEIGHT - 50);
			connectBtn.setBounds(200, 0, 150, FRAME_HEIGHT - 50);
			exitBtn.setBounds(350, 0, 150, FRAME_HEIGHT - 50);

			// Use a modern font and color
			Font font = new Font("Calibri", Font.PLAIN, 20);
			addressField.setFont(font);
			connectBtn.setFont(font);
			exitBtn.setFont(font);
			addressField.setForeground(Color.LIGHT_GRAY);
			addressField.setCaretPosition(0);
			addressField.setMargin(new Insets(1, 15, 1, 15));

			// Placeholder behavior
			addressField.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (addressField.getText().equals(PLACEHOLDER) &&
							addressField.getForeground().equals(Color.LIGHT_GRAY)) {
						addressField.setText("");
						addressField.setForeground(Color.BLACK);
					} else if (addressField.getText().equals("Connect Fail")) {
						addressField.setText("");
						addressField.setForeground(Color.BLACK);
					}
				}
			});
			addressField.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_ENTER) {
						connectBtn.doClick();
					}
					if (addressField.getText().equals(PLACEHOLDER) &&
							addressField.getForeground().equals(Color.LIGHT_GRAY)) {
						addressField.setText("");
						addressField.setForeground(Color.BLACK);
					}
				}

				@Override
				public void keyReleased(KeyEvent e) {
					if (addressField.getText().isEmpty()) {
						addressField.setForeground(Color.LIGHT_GRAY);
						addressField.setText(PLACEHOLDER);
						addressField.setCaretPosition(0);
					}
				}
			});

			add(addressField);
			add(connectBtn);
			add(exitBtn);
			exitBtn.setEnabled(false);

			// Connect button: attempt to open three sockets and then swap to the streaming panel.
			connectBtn.addActionListener(e -> {
				String ip = addressField.getText().trim();
				if (ip.equals(PLACEHOLDER) || ip.equals("Connect Fail")) {
					ip = "localhost";
				}
				try {
					Socket mainSocket = new Socket();
					Socket cursorSocket = new Socket();
					Socket keyboardSocket = new Socket();

					InetSocketAddress mainAddress = new InetSocketAddress(ip, SERVER_PORT);
					InetSocketAddress cursorAddress = new InetSocketAddress(ip, SERVER_CURSOR_PORT);
					InetSocketAddress keyboardAddress = new InetSocketAddress(ip, SERVER_KEYBOARD_PORT);

					// Connect with a timeout
					mainSocket.connect(mainAddress, 1000);
					cursorSocket.connect(cursorAddress, 1000);
					keyboardSocket.connect(keyboardAddress, 1000);

					// Indicate success and swap to screen panel
					addressField.setText("Connect Success!");
					screenPanel = new ScreenPanel(mainSocket, cursorSocket, keyboardSocket);
					setContentPane(screenPanel);
					revalidate();
					screenPanel.requestFocusInWindow();
				} catch (IOException ex) {
					ex.printStackTrace();
					addressField.setText("Connect Fail");
				}
			});

			exitBtn.addActionListener(e -> System.exit(0));
		}
	}

	/**
	 * The panel that displays the remote screen and sends mouse (and later keyboard) events.
	 */
	private class ScreenPanel extends JPanel {
		// Sockets and streams
		private Socket mainSocket;
		private Socket cursorSocket;
		private Socket keyboardSocket;
		private DataInputStream dataInputStream;
		private ObjectInputStream objectInputStream;
		private DataOutputStream mouseOutputStream;
		private DataOutputStream keyboardOutputStream;

		// Screen image parameters received from the server
		private int screenWidth, screenHeight;
		private int imageWidth, imageHeight;
		private boolean isCompress;

		// Latest image to display (volatile for safe publication)
		private volatile BufferedImage latestImage;
		// FPS counter and label
		private int fpsCount = 0;
		private JLabel fpsLabel;

		// Executor for background tasks on the panel
		private ExecutorService panelExecutor = Executors.newCachedThreadPool();
		private static final int FPS_UPDATE_INTERVAL = 1000;

		public ScreenPanel(Socket mainSocket, Socket cursorSocket, Socket keyboardSocket) {
			this.mainSocket = mainSocket;
			this.cursorSocket = cursorSocket;
			this.keyboardSocket = keyboardSocket;
			setLayout(null);

			fpsLabel = new JLabel("FPS: 0");
			fpsLabel.setFont(new Font("Serif", Font.BOLD, 20));
			fpsLabel.setBounds(10, 10, 100, 50);
			add(fpsLabel);

			try {
				mainSocket.setTcpNoDelay(true);
				dataInputStream = new DataInputStream(mainSocket.getInputStream());
				objectInputStream = new ObjectInputStream(mainSocket.getInputStream());
				mouseOutputStream = new DataOutputStream(cursorSocket.getOutputStream());
				keyboardOutputStream = new DataOutputStream(keyboardSocket.getOutputStream());
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			// Read initialization parameters from the server (blocking call)
			panelExecutor.submit(this::initializeParameters);
			// Start the screen data reading task
			panelExecutor.submit(this::readScreenData);
			// Start the FPS update task
			panelExecutor.submit(this::updateFPS);
			// Setup mouse event forwarding
			setupMouseListeners();

			// (Keyboard events can be added similarly.)
			requestFocusInWindow();
		}

		/**
		 * Reads initial parameters from the server.
		 */
		private void initializeParameters() {
			try {
				screenWidth = dataInputStream.readInt();
				screenHeight = dataInputStream.readInt();
				imageWidth = dataInputStream.readInt();
				imageHeight = dataInputStream.readInt();
				isCompress = dataInputStream.readBoolean();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		/**
		 * Reads incoming image data from the server and creates BufferedImages.
		 */
		private void readScreenData() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					int length = dataInputStream.readInt();
					byte[] imageBytes = new byte[length];
					dataInputStream.readFully(imageBytes, 0, length);

					byte[] processedBytes = isCompress ? Snappy.uncompress(imageBytes) : imageBytes;
					BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_3BYTE_BGR);
					// Wrap the raw byte array into the image's data buffer
					image.getRaster().setDataElements(0, 0, imageWidth, imageHeight, processedBytes);
					latestImage = image;
					fpsCount++;
					repaint();
				} catch (IOException ex) {
					ex.printStackTrace();
					break;
				} catch (Exception ex) {
					// For other exceptions (including Snappy exceptions), log and continue
					ex.printStackTrace();
				}
			}
		}

		/**
		 * Updates the FPS label once per second.
		 */
		private void updateFPS() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(FPS_UPDATE_INTERVAL);
				} catch (InterruptedException ex) {
					break;
				}
				SwingUtilities.invokeLater(() -> {
					fpsLabel.setText("FPS: " + fpsCount);
					fpsCount = 0;
				});
			}
		}

		/**
		 * Sets up mouse listeners to send mouse events to the server.
		 */
		private void setupMouseListeners() {
			// Send mouse movements (both move and drag)
			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					sendMouseMove(e);
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					sendMouseMove(e);
				}

				private void sendMouseMove(MouseEvent e) {
					// Map local panel coordinates to the remote screen's coordinate system
					int mappedX = e.getX() * screenWidth / getWidth();
					int mappedY = e.getY() * screenHeight / getHeight();
					try {
						mouseOutputStream.writeInt(1); // MOUSE_MOVE
						mouseOutputStream.writeInt(mappedX);
						mouseOutputStream.writeInt(mappedY);
						mouseOutputStream.flush();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			});

			// Mouse press and release events
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					try {
						mouseOutputStream.writeInt(2); // MOUSE_PRESSED
						mouseOutputStream.writeInt(e.getButton());
						mouseOutputStream.flush();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					try {
						mouseOutputStream.writeInt(3); // MOUSE_RELEASED
						mouseOutputStream.writeInt(e.getButton());
						mouseOutputStream.flush();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				}
			});

			// Mouse wheel events
			addMouseWheelListener(e -> {
				try {
					if (e.getWheelRotation() < 0) {
						mouseOutputStream.writeInt(4); // MOUSE_DOWN_WHEEL
					} else {
						mouseOutputStream.writeInt(5); // MOUSE_UP_WHEEL
					}
					mouseOutputStream.flush();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (latestImage != null) {
				// Draw the image scaled to the panel size
				g.drawImage(latestImage, 0, 0, getWidth(), getHeight(), this);
			}
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(NetworkScreenClient::new);
	}
}
