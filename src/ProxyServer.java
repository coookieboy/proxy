import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ProxyServer {

    private static String username = "1111";
    private static String password = "eeee";
    private static int port = 9981;
    private static String nextProxy = "43.138.108.171";
    private static int nextProxyPort = 9981;
    private static boolean daemon = false;

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                ExecutorService executor = Executors.newCachedThreadPool();
                executor.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            System.out.println("Request: " + requestLine);
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colonPos = line.indexOf(':');
                if (colonPos != -1) {
                    headers.put(line.substring(0, colonPos).trim(), line.substring(colonPos + 1).trim());
                }
            }

            String authHeader = headers.get("Proxy-Authorization");
            if (authHeader == null) {
                sendNeedAuth(out);
                return;
            }

            if (!authenticate(authHeader)) {
                sendForbidden(out);
                return;
            }

            String host = headers.get("Host");
            if (host == null) {
                sendBadRequest(out);
                return;
            }

            String[] hostParts = host.split(":");
            String remoteHost = hostParts[0];
            int remotePort = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 80;

            Socket remoteSocket = new Socket(remoteHost, remotePort);
            BufferedWriter remoteOut = new BufferedWriter(new OutputStreamWriter(remoteSocket.getOutputStream()));
            BufferedReader remoteIn = new BufferedReader(new InputStreamReader(remoteSocket.getInputStream()));

            if (daemon) {
                sendTunnelOk(out);
            } else {
                out.write(requestLine + "\r\n");
                out.write("\r\n");
                out.flush();
            }

            ExecutorService executor = Executors.newFixedThreadPool(2);
            executor.submit(() -> {
                try {
                    forwardData(clientSocket.getInputStream(), remoteSocket.getOutputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            executor.submit(() -> {
                try {
                    forwardData(remoteSocket.getInputStream(), clientSocket.getOutputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void forwardData(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[16 * 1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (daemon) {
                    for (int i = 0; i < bytesRead; i++) {
                        buffer[i] ^= 42;
                    }
                }
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean authenticate(String authHeader) {
        String prefix = "Basic ";
        if (!authHeader.startsWith(prefix)) return false;
        String encoded = authHeader.substring(prefix.length());
        String decoded = new String(Base64.getDecoder().decode(encoded));
        String[] parts = decoded.split(":");
        return parts.length == 2 && parts[0].equals(username) && parts[1].equals(password);
    }

    private static void sendForbidden(BufferedWriter out) throws IOException {
        out.write("HTTP/1.1 403 Forbidden\r\n\r\n");
        out.flush();
    }

    private static void sendNeedAuth(BufferedWriter out) throws IOException {
        out.write("HTTP/1.1 407 Proxy Authentication Required\r\n");
        out.write("Proxy-Authenticate: Basic\r\n\r\n");
        out.flush();
    }

    private static void sendTunnelOk(BufferedWriter out) throws IOException {
        out.write("HTTP/1.1 200 Connection Established\r\n\r\n");
        out.flush();
    }

    private static void sendBadRequest(BufferedWriter out) throws IOException {
        out.write("HTTP/1.1 400 Bad Request\r\n\r\n");
        out.flush();
    }
}
