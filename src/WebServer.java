import http.JspService;
import http.Session;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;

class ServerThread extends Thread {
    private Socket sock;

    public ServerThread(Socket sock) {
        this.sock = sock;
    }

    public void run() {
        try {
            HttpRequest req = new HttpRequest();
            String msg = req.receive(sock.getInputStream());

            String url = req.getUrl(msg);
            System.out.println("file name: " + url);

            if(url != null) {
                String host = req.getHost(msg);
                String file = req.getFile(url);
                String[] params = req.getParams(url);
                if(params != null) {
                    System.out.println("host: " + host + ", file: " + file + ", params[0]: " + params[0]);
                }

                HttpResponse res = new HttpResponse();
                res.send(sock.getOutputStream(), host, file, params);
            }
            sock.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

class HttpRequest {
    public String receive(InputStream is) throws IOException {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int cnt;
        while ((cnt = is.read(buf)) != -1) {
            bao.write(buf, 0, cnt);
            if (cnt < buf.length)
                break;
        }
        return bao.toString();
    }

    public String getUrl(String msg) {
        String[] lines = msg.trim().split("\n");
        if (lines.length == 0)
            return null;

        System.out.println("first: " + lines[0]);
        String[] toks = lines[0].trim().split(" ");
        if (toks.length < 2)
            return null;
        if (!toks[0].equals("GET") && !toks[0].equals("POST"))
            return null;

        String filePath = toks[1];
        return filePath;
    }

    public String getHost(String msg) {
        String[] lines = msg.trim().split("\n");
        if(lines.length <= 1) return null;

        System.out.println("second: " + lines[1]);
        String[] toks = lines[1].split(":");
        if(toks.length == 0) return null;
        if(!toks[0].equals("Host") && !toks[0].equals("host")) return null;

        return toks[1].trim();
    }
    public String getFile(String url) {
        int idx = url.indexOf("?");
        //if(idx < 0) return url;
        //return url.substring(0, idx);
        return (idx < 0) ? url : url.substring(0, idx);
    }
    public String[] getParams(String url) {
        int idx = url.indexOf("?");
        return (idx < 0) ? null : url.substring(idx+1).split("&");
    }
}

class HttpResponse {
    public void send(OutputStream os, String host, String file, String[] params) throws IOException {
        if(isJsp(file)){
            JspHandler hnd = new JspHandler();
            hnd.send(os, host, file, params);
        } else {
            file = "web" + file;
            System.out.println("send file: " + file);
            File requestedFile = new File(file);
            if (!requestedFile.exists()) {
                System.out.println("Requested file not found: " + file);
                sendNotFoundResponse(os);
            } else {
                if (isImage(file)) {
                    byte[] bytes = getMsgImage(file);
                    sendImageResponse(os, bytes);
                } else {
                    byte[] bytes = getMsgText(file);
                    sendTextResponse(os, bytes);
                }
            }
        }
    }
    private boolean isJsp(String file) {
        int idx = file.lastIndexOf(".");
        String ext = (idx > 0) ? file.substring(idx + 1) : "";
        return ext.equals("jsp");
    }

    private boolean isImage(String file) {
        int idx = file.lastIndexOf(".");
        String ext = (idx > 0) ? file.substring(idx + 1) : "";
        return (ext.equals("jpg") || ext.equals("jpeg"));
    }

    private byte[] getMsgText(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        String msg = "HTTP/1.1 200 OK\r\n";
        msg += "Content-Type: text/html;charset=utf-8\r\n";
        msg += "Content-Length: " + bytes.length + "\r\n";
        msg += "Date: " + new Date().toString() + "\r\n";
        msg += "\r\n";
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        bao.write(msg.getBytes());
        bao.write(bytes);
        return bao.toByteArray();
    }

    private byte[] getMsgImage(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        String msg = "HTTP/1.1 200 OK\r\n";
        msg += "Content-Type: image/jpeg\r\n";
        msg += "Content-Length: " + bytes.length + "\r\n";
        msg += "Date: " + new Date().toString() + "\r\n";
        msg += "\r\n";
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        bao.write(msg.getBytes());
        bao.write(bytes);
        return bao.toByteArray();
    }

    private void sendTextResponse(OutputStream os, byte[] bytes) throws IOException {
        os.write(bytes);
        os.flush();
        os.close();
    }

    private void sendImageResponse(OutputStream os, byte[] bytes) throws IOException {
        os.write(bytes);
        os.flush();
        os.close();
    }

    private void sendNotFoundResponse(OutputStream os) throws IOException {
        String msg = "HTTP/1.1 404 Not Found\r\n";
        msg += "Content-Type: text/html;charset=utf-8\r\n";
        msg += "Content-Language: ko\r\n";
        msg += "Date: " + new Date().toString() + "\r\n";
        msg += "\r\n";
        msg += "<html><meta charset='utf-8'>요청하신 파일이 존재하지 않습니다.</html>\r\n";
        os.write(msg.getBytes());
        os.flush();
        os.close();
    }
}
class JspHandler {
    public void send(OutputStream os, String host, String file, String[] params) throws IOException {
        file = "jsp" + getClassName(file);
        try {
            Class c = Class.forName(file);
            JspService svc = (JspService) c.newInstance();
            sendText(os, getMsg(200, svc.getHtml(Session.getInstance()  ,host, params)));
        } catch (Exception ex) {
            ex.printStackTrace();
            sendText(os, getMsg(500, getHtml("서버 오류가 발생하였습니다.")));
        }
    }

    private String getMsg(int code, String html){
        String msg = "HTTP/1.1 " + code + "\n";
        msg += "Content-Type: text/html;charset=utf-8\n";
        msg += "Content-Language: ko\n";
        msg += "Date: " + new java.util.Date().toString() + "\n";
        msg += "\n";
        msg += html;
        return msg;
    }

    private String getHtml(String desc){
        return "<html><meta charset='utf-8'>" + desc + "</html>";
    }
    private String getClassName(String file){
        int idx = file.lastIndexOf(".jsp");
        return (idx < 0) ? "" : file.substring(0, idx).replaceAll("/",".");
    }

    private void sendText(OutputStream os, String msg) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, "utf-8"));
        pw.println(msg);
        pw.flush();
    }
}

public class WebServer {
    public static void main(String[] args) {
        try {
            ServerSocket srvsock = new ServerSocket(80);
            System.out.println("Server start ... \n");
            while (true) {
                Socket sock = srvsock.accept();
                ServerThread thread = new ServerThread(sock);
                thread.start();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
