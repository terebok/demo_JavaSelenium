import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebUi {

    private static volatile boolean running = false;
    private static final StringBuilder logBuffer = new StringBuilder();
    private static String projectRootPath = "";

    public static void main(String[] args) throws Exception {
        // Определяем корень проекта при старте
        projectRootPath = new File(".").getCanonicalPath();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", WebUi::handleIndex);
        server.createContext("/run", WebUi::handleRun);
        server.createContext("/status", WebUi::handleStatus);
        server.createContext("/logs", WebUi::handleLogs);
        server.createContext("/logs-obfuscated", WebUi::handleLogsObfuscated);
        server.createContext("/clear", WebUi::handleClear);
        server.createContext("/git-push", WebUi::handleGit);
        server.createContext("/allure", WebUi::handleAllure);
        server.start();
        System.out.println("WebUI: http://localhost:8080");
        Thread.currentThread().join();
    }

    private static boolean isRunningInDocker() {
        if (new File("/.dockerenv").exists()) return true;
        if ("true".equalsIgnoreCase(System.getenv("DOCKER_CONTAINER"))) return true;
        try {
            File cgroup = new File("/proc/self/cgroup");
            if (cgroup.exists()) {
                String content = new String(Files.readAllBytes(cgroup.toPath()), StandardCharsets.UTF_8);
                if (content.contains("docker") || content.contains("kubepods")) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String obfuscateLog(String text) {
        if (text == null || text.isEmpty()) return text;

        // 1. Замена конкретных данных из проекта
        text = text.replace("terebok", "[[MASK]]");
        text = text.replace("standard_user", "[[MASK]]");
        text = text.replace("secret_sauce", "[[MASK]]");

        // 2. Замена корневого пути проекта на метку (сохраняя структуру папок)
        if (!projectRootPath.isEmpty()) {
            // Заменяем разные варианты слэшей для кроссплатформенности
            text = text.replace(projectRootPath, "[[PROJECT_ROOT]]");
            text = text.replace(projectRootPath.replace("\\", "/"), "[[PROJECT_ROOT]]");
            text = text.replace(projectRootPath.replace("/", "\\"), "[[PROJECT_ROOT]]");
        }

        // 3. Замена оставшихся путей (если они не относятся к корню проекта)
        text = Pattern.compile("[A-Za-z]:\\\\[^:*?\"<>|\\r\\n]+")
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement("[[LOCAL_PATH]]"));

        // 4. Замена IP-адресов
        text = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement("[[IP]]"));

        // 5. Замена email
        text = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .matcher(text)
                .replaceAll(Matcher.quoteReplacement("[[EMAIL]]"));

        return text;
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        boolean inDocker = isRunningInDocker();
        String envStatus = inDocker ? " (в Docker)" : " (Local)";

        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>SauceDemo Tests" + envStatus + "</title>"
                + "<style>pre{background:#f4f4f4;padding:10px;height:400px;overflow:auto;}</style></head><body>"
                + "<h1>SauceDemo автотесты" + envStatus + "</h1>"
                + "<div>"
                + "<label><input type='checkbox' name='t' value='LoginTest#testSuccessfulLogin'> LoginTest.testSuccessfulLogin</label><br>"
                + "<label><input type='checkbox' name='t' value='ApiTest#testMainPageAvailability'> ApiTest.testMainPageAvailability</label>"
                + "</div>"
                + "<div style='margin-top: 10px; display: flex; gap: 10px; align-items: center;'>"
                + "<button type='button' id='runBtn' disabled>Запустить выбранное</button>"
                + "<button type='button' id='clearBtn' disabled>Очистить лог</button>"
                + "<button type='button' id='copyBtn' disabled>Копировать лог</button>"
                + "<button type='button' id='gitBtn' disabled>->Git</button>"
                + "</div>"
                + "<p id='s'>Статус: ожидание</p>"
                + "<div id='reportLink' style='display:none; margin-top: 10px;'><a id='allureLink' href='/allure' target='_blank' style='font-size: 18px; font-weight: bold; pointer-events: none; opacity: 0.5;'>Открыть отчет Allure</a></div>"
                + "<pre id='log'></pre>"
                + "<script>"
                + "var isRunning = false;"
                + "var btn=document.getElementById('runBtn');"
                + "var copyBtn=document.getElementById('copyBtn');"
                + "var clearBtn=document.getElementById('clearBtn');"
                + "var gitBtn=document.getElementById('gitBtn');"
                + "var rLink=document.getElementById('reportLink');"
                + "document.querySelectorAll('input[name=t]').forEach(function(cb){cb.addEventListener('change',function(){var checked=document.querySelectorAll('input[name=t]:checked').length;btn.disabled=isRunning||(checked===0);});});"
                + "btn.onclick=function(){"
                + "var v=[...document.querySelectorAll('input[name=t]:checked')].map(x=>x.value);"
                + "if(!v.length){alert('Выберите хотя бы один тест');return;}"
                + "fetch('/run',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'tests='+encodeURIComponent(v.join(','))});};"
                + "copyBtn.onclick=function(){"
                + "fetch('/logs-obfuscated').then(r=>r.text()).then(text => {"
                + "navigator.clipboard.writeText(text).then(function(){"
                + "var originalText = copyBtn.innerText;"
                + "copyBtn.innerText = 'Скопировано!';"
                + "setTimeout(function(){ copyBtn.innerText = originalText; }, 2000);"
                + "});"
                + "});};"
                + "clearBtn.onclick=function(){"
                + "fetch('/clear', {method: 'POST'}).then(function() {"
                + "document.getElementById('log').innerText = '';"
                + "});};"
                + "gitBtn.onclick=function(){"
                + "fetch('/git-push', {method: 'POST'});"
                + "};"
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
                + "clearBtn.disabled = isRunning || !hasLog;"
                + "copyBtn.disabled = isRunning || !hasLog;"
                + "gitBtn.disabled = isRunning;"
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

    private static void handleGit(HttpExchange ex) throws IOException {
        if (running) { send(ex, 200, "уже выполняется"); return; }
        running = true;
        synchronized (logBuffer) {
            logBuffer.setLength(0);
        }

        new Thread(() -> {
            try {
                boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

                ProcessBuilder pbStatus = isWindows
                        ? new ProcessBuilder("cmd", "/c", "git", "status", "--porcelain")
                        : new ProcessBuilder("git", "status", "--porcelain");
                pbStatus.directory(new File("."));
                pbStatus.redirectErrorStream(true);
                Process pStatus = pbStatus.start();

                StringBuilder statusOut = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pStatus.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        statusOut.append(line).append("\n");
                    }
                }
                pStatus.waitFor();

                String statusText = statusOut.toString().trim();
                if (statusText.isEmpty()) {
                    synchronized (logBuffer) {
                        logBuffer.append("Нет изменений для коммита.\n");
                    }
                    return;
                }

                StringBuilder commitMsg = new StringBuilder("Auto-commit:");
                String[] lines = statusText.split("\n");
                for (String line : lines) {
                    if (line.length() > 3) {
                        String statusCode = line.substring(0, 2).trim();
                        String fileName = line.substring(3).trim();
                        String action = "изменен";
                        if (statusCode.equals("??") || statusCode.equals("A")) action = "добавлен";
                        else if (statusCode.equals("D")) action = "удален";
                        commitMsg.append("\n- ").append(action).append(": ").append(fileName);
                    }
                }

                synchronized (logBuffer) {
                    logBuffer.append("Найдены изменения:\n").append(statusText).append("\n");
                }

                runGitCommand(isWindows, "add", ".");
                runGitCommand(isWindows, "commit", "-m", commitMsg.toString());
                runGitCommand(isWindows, "push");

                synchronized (logBuffer) {
                    logBuffer.append("Git операции успешно завершены.\n");
                }

            } catch (Exception e) {
                synchronized (logBuffer) {
                    logBuffer.append("Ошибка Git: ").append(e.getMessage()).append("\n");
                }
                e.printStackTrace();
            } finally {
                running = false;
            }
        }).start();
        send(ex, 200, "запущено");
    }

    private static void runGitCommand(boolean isWindows, String... args) throws Exception {
        String[] cmd = new String[args.length + (isWindows ? 3 : 1)];
        if (isWindows) {
            cmd[0] = "cmd"; cmd[1] = "/c"; cmd[2] = "git";
            System.arraycopy(args, 0, cmd, 3, args.length);
        } else {
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File("."));
        pb.redirectErrorStream(true);
        Process p = pb.start();

        synchronized (logBuffer) {
            logBuffer.append("> git ").append(String.join(" ", args)).append("\n");
        }

        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                synchronized (logBuffer) {
                    logBuffer.append(line).append("\n");
                }
            }
        }
        p.waitFor();
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

    private static void handleLogsObfuscated(HttpExchange ex) throws IOException {
        String currentLogs;
        synchronized (logBuffer) {
            currentLogs = logBuffer.toString();
        }
        String obfuscated = obfuscateLog(currentLogs);
        send(ex, 200, obfuscated);
    }

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