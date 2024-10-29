import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class SimpleProxyServer {
    private static final int PORT = 9090; // 服务器端口

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Proxy server is listening on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            // 读取请求行
            String requestLine = in.readLine();
            if (requestLine == null) return;

            // 解析请求行
            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) return;

            String method = requestParts[0];
            String url = requestParts[1];

            // 创建与目标服务器的连接
            URL targetUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
            connection.setRequestMethod(method);

            // 复制请求头
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                String[] headerParts = headerLine.split(": ");
                if (headerParts.length == 2) {
                    connection.setRequestProperty(headerParts[0], headerParts[1]);
                }
            }

            // 获取响应并转发给客户端
            int responseCode = connection.getResponseCode();
            out.println("HTTP/1.1 " + responseCode + " " + connection.getResponseMessage());
            for (String header : connection.getHeaderFields().keySet()) {
                if (header != null) {
                    out.println(header + ": " + connection.getHeaderField(header));
                }
            }
            out.println(); // 响应头结束

            // 读取响应内容并发送给客户端
            try (InputStream responseStream = connection.getInputStream();
                BufferedReader responseReader = new BufferedReader(new InputStreamReader(responseStream))) {
                String responseLine;
                while ((responseLine = responseReader.readLine()) != null) {
                    out.println(responseLine);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close(); // 关闭连接
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
