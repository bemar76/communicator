package ch.bemar.supercache.comm.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.bemar.supercache.comm.ITransferContainer;
import ch.bemar.supercache.exception.SendAnswerException;

public class CommServer<K extends Serializable, V extends Serializable> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CommServer.class);

	private Integer port;

	private IncomingTransferListener<K, V> listener;

	private boolean listening = true;

	private ServerSocket serverSocket;

	private ExecutorService executorService;

	public CommServer(Integer port, IncomingTransferListener<K, V> listener) throws IOException {

		this.port = port;

		LOGGER.info("Initializing CacheServer on port {}", this.port);

		this.listener = listener;

		serverSocket = new ServerSocket(this.port);

		executorService = Executors.newCachedThreadPool(new CommThreadFactory());

		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				read();

			}
		});

		t.start();

	}

	public void read() {

		while (listening) {
			try {
				LOGGER.debug("Waiting for input");
				Socket listeningSocket = serverSocket.accept();

				executorService.submit(() -> handleIncoming(listeningSocket));

			} catch (Exception ex) {
				LOGGER.error(ex.getMessage(), ex);
			}
		}
	}

	private void handleIncoming(Socket listeningSocket) {

		try {

			LOGGER.debug("Session accepted");
			ObjectInputStream objectInputStream = new ObjectInputStream(listeningSocket.getInputStream());

			Object obj = objectInputStream.readObject();
			LOGGER.trace("got object {}", obj);

			listener.addIncomingTransfer((ITransferContainer<K, V>) obj);
			LOGGER.debug("Put object to receiving queue");

			sendConfirmation("OK", listeningSocket);

		} catch (Exception ex) {
			LOGGER.error(ex.getMessage(), ex);
		} finally {
			try {
				listeningSocket.close();
			} catch (IOException e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}

	private void sendConfirmation(String answer, Socket socket) throws SendAnswerException {
		PrintWriter out = null;
		try {

			out = new PrintWriter(socket.getOutputStream(), true);
			out.write(answer);
			LOGGER.debug("Sent confirmation");

			if (out.checkError()) {
				LOGGER.warn("detected error in output stream");
			}

		} catch (IOException ex) {

			LOGGER.error(ex.getMessage(), ex);
			throw new SendAnswerException(ex);

		} finally {
			if (out != null)
				out.close();
		}

	}

	public void stop() throws IOException {
		serverSocket.close();
		this.listening = false;
	}

}