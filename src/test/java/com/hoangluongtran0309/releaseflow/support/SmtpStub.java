package com.hoangluongtran0309.releaseflow.support;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local SMTP server that speaks just enough of the protocol to accept a message and
 * remember it. It stands in for the deployment's own mail server, which is the only
 * one an email action ever uses.
 */
public final class SmtpStub implements AutoCloseable {

    private final ServerSocket socket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private volatile boolean refuse;
    private volatile boolean running = true;

    private SmtpStub() {
        try {
            socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        executor.submit(this::accept);
    }

    public static SmtpStub start() {
        return new SmtpStub();
    }

    public int port() {
        return socket.getLocalPort();
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    /** The next messages are refused, the way a server rejects a sender it does not know. */
    public void refuse(boolean refuse) {
        this.refuse = refuse;
    }

    public void reset() {
        messages.clear();
        refuse = false;
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing a listener nobody is using cannot fail in a way a test cares about.
        }
        executor.shutdownNow();
    }

    private void accept() {
        while (running) {
            try {
                Socket client = socket.accept();
                executor.submit(() -> converse(client));
            } catch (IOException exception) {
                return;
            }
        }
    }

    private void converse(Socket client) {
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = client.getOutputStream()) {
            reply(out, "220 releaseflow-test ESMTP");
            List<String> recipients = new ArrayList<>();
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                String command = line.toUpperCase(java.util.Locale.ROOT);
                if (command.startsWith("EHLO") || command.startsWith("HELO")) {
                    reply(out, "250 releaseflow-test");
                } else if (command.startsWith("MAIL FROM")) {
                    reply(out, refuse ? "550 sender refused" : "250 OK");
                } else if (command.startsWith("RCPT TO")) {
                    recipients.add(line.substring(line.indexOf('<') + 1, line.lastIndexOf('>')));
                    reply(out, "250 OK");
                } else if (command.startsWith("DATA")) {
                    reply(out, "354 End with a dot");
                    String body;
                    while ((body = in.readLine()) != null && !".".equals(body)) {
                        // SMTP doubles a leading dot; the message itself has one.
                        data.append(body.startsWith("..") ? body.substring(1) : body).append('\n');
                    }
                    messages.add(new Message(List.copyOf(recipients), data.toString()));
                    reply(out, "250 Queued");
                } else if (command.startsWith("QUIT")) {
                    reply(out, "221 Bye");
                    return;
                } else {
                    reply(out, "250 OK");
                }
            }
        } catch (IOException exception) {
            // A client that hung up mid-conversation is not something a test asserts on.
        }
    }

    private static void reply(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** One message the stub accepted. */
    public record Message(List<String> recipients, String body) {

        /** The decoded text of the first part of this type, or null when there is none. */
        public String part(String contentType) {
            try {
                MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
                return find(message, contentType);
            } catch (MessagingException | IOException exception) {
                throw new IllegalStateException("The stub accepted a message it cannot read.", exception);
            }
        }

        private static String find(Part part, String contentType) throws MessagingException, IOException {
            if (part.isMimeType(contentType)) {
                return (String) part.getContent();
            }
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int index = 0; index < multipart.getCount(); index++) {
                    BodyPart child = multipart.getBodyPart(index);
                    String found = find(child, contentType);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
    }
}
