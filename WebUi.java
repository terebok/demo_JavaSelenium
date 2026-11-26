import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.nio.file.Files;

public class WebUi {

    private static volatile boolean running = false;
    private static final StringBuilder logBuffer = new StringBuilder();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", WebUi::handleIndex);
        server.createContext("/run", WebUi::handleRun);
        server.createContext("/status", WebUi::handleStatus);
        server.createContext("/logs", WebUi::handleLogs);
        server.createContext("/clear", WebUi::handleClear);
        server.createContext("/allure", WebUi::handleAllure);
        server.start();
        System.out.println("WebUI: http://localhost:8080");
        Thread.currentThread().join();
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>SauceDemo Tests</title>"
                + "<style>pre{background:#f4f4f4;padding:10px;height:400px;overflow:auto;}</style></head><body>"
                + "<h1>SauceDemo автотесты</h1>"
                + "<form id='f'>"
                + "<label><input type='checkbox' name='t' value='LoginTest#testSuccessfulLogin'> LoginTest.testSuccessfulLogin</label><br>"
                + "<label><input type='checkbox' name='t' value='ApiTest#testMainPageAvailability'> ApiTest.testMainPageAvailability</label><br>"
                + "<button type='submit' id='runBtn' disabled>Запустить выбранные</button>"
                + "</form>"
                // Обернули кнопки управления логами в div и выровняли по правому краю
                + "<div style='text-align: right; margin-top: 10px; margin-bottom: 5px;'>"
                + "<button type='button' id='clearBtn' disabled>Очистить лог</button>"
                + "<button type='button' id='copyBtn' disabled>Копировать лог</button>"
                + "</div>"
                + "<p id='s'>Статус: ожидание</p>"
                + "<div id='reportLink' style='display:none; margin-top: 10px;'><a id='allureLink' href='/allure' target='_blank' style='font-size: 18px; font-weight: bold; pointer-events: none; opacity: 0.5;'>Открыть отчет Allure</a></div>"
                + "<pre id='log'></pre>"
                + "<script>"
                + "var isRunning = false;"
                + "var btn=document.getElementById('runBtn');"
                + "var copyBtn=document.getElementById('copyBtn');"
                + "var clearBtn=document.getElementById('clearBtn');"
                + "var rLink=document.getElementById('reportLink');"
                + "document.querySelectorAll('input[name=t]').forEach(function(cb){cb.addEventListener('change',function(){var checked=document.querySelectorAll('input[name=t]:checked').length;btn.disabled=isRunning||(checked===0);});});"
                + "document.getElementById('f').onsubmit=function(e){e.preventDefault();"
                + "var v=[...document.querySelectorAll('input[name=t]:checked')].map(x=>x.value);"
                + "if(!v.length){alert('Выберите хотя бы один тест');return;}"
                + "fetch('/run',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'tests='+encodeURIComponent(v.join(','))});};"
                + "copyBtn.onclick=function(){"
                + "var text = document.getElementById('log').innerText;"
                + "navigator.clipboard.writeText(text).then(function(){"
                + "var originalText = copyBtn.innerText;"
                + "copyBtn.innerText = 'Скопировано!';"
                + "setTimeout(function(){ copyBtn.innerText = originalText; }, 2000);"
                + "});};"
                // Добавлен обработчик нажатия на кнопку очистки
                + "clearBtn.onclick=function(){"
                + "fetch('/clear', {method: 'POST'}).then(function() {"
                + "document.getElementById('log').innerText = '';"
                + "});};"
                + "setInterval(function(){"
                + "fetch('/status').then(r=>r.text()).then(t=>{"
                + "document.getElementById('s').innerText='Статус: '+t;"
                + "isRunning = (t==='выполняется');"
                + "var checked = document.querySelectorAll('input[name=t]:checked').length;"
                + "btn.disabled = isRunning || (checked === 0);"
                + "if(t==='ожидание'){rLink.style.display='block';}else{rLink.style.display='none';}"
                + "});"
                + "fetch('/logs').then(r=>r.text()).then(t=>{"
                + "var l=document.getElementById('log');l.innerText=t;l.scrollTop=l.scrollHeight;"
                + "var hasLog = t.trim().length > 0;"
                // Управление состоянием кнопки очистки
                + "clearBtn.disabled = isRunning || !hasLog;"
                + "copyBtn.disabled = isRunning || !hasLog;"
                + "var allureLink = document.getElementById('allureLink');"
                + "if(!hasLog){allureLink.style.pointerEvents='none';allureLink.style.opacity='0.5';}else{allureLink.style.pointerEvents='auto';allureLink.style.opacity='1';}"
                + "});"
                + "},2000);"
                + "</script>"
                + "</body></html>";
        send(ex, 200, html);
    }

    private static void handleRun(HttpExchange ex) throws IOException {
        if (running) { send(ex, 200, "уже выполняется"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String tests = "";
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals("tests")) {
                tests = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        final String testParam = tests;
        running = true;
        synchronized (logBuffer) {
            logBuffer.setLength(0);
        }

        new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

                ProcessBuilder testPb;
                if (testParam.isEmpty()) {
                    testPb = isWindows
                            ? new ProcessBuilder("cmd", "/c", "mvn", "clean", "test")
                            : new ProcessBuilder("mvn", "clean", "test");
                } else {
                    testPb = isWindows
                            ? new ProcessBuilder("cmd", "/c", "mvn", "clean", "test", "-Dtest=" + testParam)
                            : new ProcessBuilder("mvn", "clean", "test", "-Dtest=" + testParam);
                }
                testPb.directory(new File("."));
                testPb.redirectErrorStream(true);
                Process p = testPb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (logBuffer) {
                            logBuffer.append(line).append("\n");
                        }
                        System.out.println(line);
                    }
                }
                p.waitFor();

                ProcessBuilder reportPb = isWindows
                        ? new ProcessBuilder("cmd", "/c", "mvn", "allure:report")
                        : new ProcessBuilder("mvn", "allure:report");
                reportPb.directory(new File("."));
                reportPb.redirectErrorStream(true);
                Process rp = reportPb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(rp.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (logBuffer) {
                            logBuffer.append(line).append("\n");
                        }
                        System.out.println(line);
                    }
                }
                rp.waitFor();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                running = false;
            }
        }).start();
        send(ex, 200, "запущено");
    }

    private static void handleStatus(HttpExchange ex) throws IOException {
        send(ex, 200, running ? "выполняется" : "ожидание");
    }

    private static void handleLogs(HttpExchange ex) throws IOException {
        String currentLogs;
        synchronized (logBuffer) {
            currentLogs = logBuffer.toString();
        }
        send(ex, 200, currentLogs);
    }

    // Новый метод для очистки буфера логов
    private static void handleClear(HttpExchange ex) throws IOException {
        synchronized (logBuffer) {
            logBuffer.setLength(0);
        }
        send(ex, 200, "очищено");
    }

    private static void handleAllure(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/allure") || path.equals("/allure/")) {
            path = "/allure/index.html";
        }
        File file = new File("target/site/allure-maven-plugin" + path.replace("/allure", ""));
        if (!file.exists() || file.isDirectory()) {
            send(ex, 404, "Not found");
            return;
        }

        String mime = "text/plain";
        if (path.endsWith(".html")) mime = "text/html";
        else if (path.endsWith(".js")) mime = "application/javascript";
        else if (path.endsWith(".css")) mime = "text/css";
        else if (path.endsWith(".png")) mime = "image/png";
        else if (path.endsWith(".svg")) mime = "image/svg+xml";
        else if (path.endsWith(".woff2")) mime = "font/woff2";
        else if (path.endsWith(".woff")) mime = "font/woff";
        else if (path.endsWith(".ttf")) mime = "font/ttf";
        else if (path.endsWith(".json")) mime = "application/json";

        byte[] bytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().add("Content-Type", mime);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}