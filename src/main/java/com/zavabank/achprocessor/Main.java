package com.zavabank.achprocessor;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class Main {
    private static volatile boolean running = true;

    public static void main(String[] args) {
        final Properties config = loadConfig("achprocessor.properties");
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                running = false;
                log("Shutdown requested.");
            }
        }));

        String incomingPath = config.getProperty("ach.incoming.path", "/shared/ach-incoming");
        String processedPath = config.getProperty("ach.processed.path", "/shared/ach-incoming/processed");
        String errorPath = config.getProperty("ach.error.path", "/shared/ach-incoming/error");
        long pollMs = parseLong(config.getProperty("ach.poll.interval.ms", "5000"), 5000L);

        ensureDirectory(processedPath);
        ensureDirectory(errorPath);
        log("ZavaACHProcessor started. Polling " + incomingPath + " every " + pollMs + " ms.");

        while (running) {
            processIncomingFiles(config, incomingPath, processedPath, errorPath);
            sleepQuietly(pollMs);
        }

        log("ZavaACHProcessor stopped.");
    }

    private static void processIncomingFiles(Properties config, String incomingPath, String processedPath, String errorPath) {
        File incomingDir = new File(incomingPath);
        if (!incomingDir.exists() || !incomingDir.isDirectory()) {
            log("Incoming path missing or not a directory: " + incomingPath);
            return;
        }

        File[] files = incomingDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        int i;
        for (i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isFile()) {
                continue;
            }
            if ("processed".equalsIgnoreCase(file.getName()) || "error".equalsIgnoreCase(file.getName())) {
                continue;
            }
            processSingleFile(config, file, processedPath, errorPath);
        }
    }

    private static void processSingleFile(Properties config, File file, String processedPath, String errorPath) {
        log("Processing ACH file: " + file.getName());
        AchBatch achBatch = parseNachaFile(file);
        if (achBatch == null) {
            moveFile(file.toPath(), Paths.get(errorPath, file.getName()));
            publishFailureEvent(config, file.getName(), "parse_error");
            return;
        }

        int successCount = 0;
        int failCount = 0;
        int i;
        for (i = 0; i < achBatch.entries.size(); i++) {
            AchEntry entry = achBatch.entries.get(i);
            boolean posted = postTransactionToLedger(config, file.getName(), achBatch, entry);
            if (posted) {
                successCount++;
                publishProcessedEvent(config, file.getName(), achBatch, entry);
            } else {
                failCount++;
            }
        }

        if (failCount == 0) {
            moveFile(file.toPath(), Paths.get(processedPath, file.getName()));
            log("Completed ACH file: " + file.getName() + " success=" + successCount + " failed=" + failCount);
        } else {
            moveFile(file.toPath(), Paths.get(errorPath, file.getName()));
            publishFailureEvent(config, file.getName(), "transaction_post_failed");
            log("ACH file failed: " + file.getName() + " success=" + successCount + " failed=" + failCount);
        }
    }

    private static AchBatch parseNachaFile(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            AchBatch batch = new AchBatch();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }
                char recordType = line.charAt(0);
                if (recordType == '5') {
                    batch.batchNumber = safeSlice(line, 87, 94).trim();
                    batch.companyName = safeSlice(line, 4, 20).trim();
                    batch.effectiveDate = safeSlice(line, 69, 75).trim();
                } else if (recordType == '6') {
                    AchEntry entry = new AchEntry();
                    entry.transactionCode = safeSlice(line, 1, 3).trim();
                    entry.routingNumber = safeSlice(line, 3, 11).trim();
                    entry.accountNumber = safeSlice(line, 12, 29).trim();
                    entry.amountCents = safeSlice(line, 29, 39).trim();
                    entry.individualId = safeSlice(line, 39, 54).trim();
                    entry.individualName = safeSlice(line, 54, 76).trim();
                    entry.traceNumber = safeSlice(line, 79, 94).trim();
                    batch.entries.add(entry);
                } else if (recordType == '8') {
                    batch.entryCount = safeSlice(line, 4, 10).trim();
                    batch.totalDebit = safeSlice(line, 20, 32).trim();
                    batch.totalCredit = safeSlice(line, 32, 44).trim();
                }
            }
            if (batch.entries.size() == 0) {
                log("No ACH entries found in file: " + file.getName());
                return null;
            }
            return batch;
        } catch (Exception ex) {
            log("Failed to parse ACH file " + file.getName() + ": " + ex.getMessage());
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean postTransactionToLedger(Properties config, String fileName, AchBatch batch, AchEntry entry) {
        HttpURLConnection connection = null;
        OutputStream output = null;
        InputStream responseStream = null;
        try {
            String endpoint = config.getProperty("ledger.url", "http://localhost:8080") + config.getProperty("ledger.ach.endpoint", "/api/ach/transactions");
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(parseInt(config.getProperty("http.timeout.ms", "15000"), 15000));
            connection.setReadTimeout(parseInt(config.getProperty("http.timeout.ms", "15000"), 15000));
            connection.setRequestProperty("Content-Type", "application/json");

            String payload = "{"
                + "\"source\":\"ach\","
                + "\"fileName\":\"" + escape(fileName) + "\","
                + "\"batchNumber\":\"" + escape(batch.batchNumber) + "\","
                + "\"routingNumber\":\"" + escape(entry.routingNumber) + "\","
                + "\"accountNumber\":\"" + escape(entry.accountNumber) + "\","
                + "\"amountCents\":\"" + escape(entry.amountCents) + "\","
                + "\"traceNumber\":\"" + escape(entry.traceNumber) + "\""
                + "}";

            output = connection.getOutputStream();
            output.write(payload.getBytes(StandardCharsets.UTF_8));
            output.flush();

            int status = connection.getResponseCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            responseStream = connection.getErrorStream();
            String err = readStream(responseStream);
            log("Ledger post failed [" + status + "] for trace " + entry.traceNumber + ": " + err);
            return false;
        } catch (Exception ex) {
            log("Ledger post exception for trace " + entry.traceNumber + ": " + ex.getMessage());
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
            if (responseStream != null) {
                try {
                    responseStream.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void publishProcessedEvent(Properties config, String fileName, AchBatch batch, AchEntry entry) {
        publishRabbitEvent(
            config,
            config.getProperty("rabbitmq.events.exchange", "zava.events"),
            config.getProperty("rabbitmq.ach.routingKey", "ach.processed"),
            "{"
                + "\"eventType\":\"ach.processed\","
                + "\"fileName\":\"" + escape(fileName) + "\","
                + "\"batchNumber\":\"" + escape(batch.batchNumber) + "\","
                + "\"traceNumber\":\"" + escape(entry.traceNumber) + "\","
                + "\"amountCents\":\"" + escape(entry.amountCents) + "\","
                + "\"processedAt\":\"" + nowIso() + "\""
                + "}"
        );
    }

    private static void publishFailureEvent(Properties config, String fileName, String reason) {
        publishRabbitEvent(
            config,
            config.getProperty("rabbitmq.deadletter.exchange", "zava.dlx"),
            config.getProperty("rabbitmq.ach.failure.routingKey", "ach.failed"),
            "{"
                + "\"eventType\":\"ach.failed\","
                + "\"fileName\":\"" + escape(fileName) + "\","
                + "\"reason\":\"" + escape(reason) + "\","
                + "\"failedAt\":\"" + nowIso() + "\""
                + "}"
        );
    }

    private static void publishRabbitEvent(Properties config, String exchange, String routingKey, String payload) {
        Connection connection = null;
        Channel channel = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(config.getProperty("rabbitmq.host", "localhost"));
            factory.setPort(parseInt(config.getProperty("rabbitmq.port", "5672"), 5672));
            factory.setUsername(config.getProperty("rabbitmq.username", "guest"));
            factory.setPassword(config.getProperty("rabbitmq.password", "guest"));
            factory.setVirtualHost(config.getProperty("rabbitmq.vhost", "/"));
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(exchange, "topic", true);
            channel.basicPublish(exchange, routingKey, null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log("RabbitMQ publish failed (" + routingKey + "): " + ex.getMessage());
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void moveFile(Path source, Path target) {
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            log("Failed to move file " + source.toString() + " -> " + target.toString() + ": " + ex.getMessage());
        }
    }

    private static String safeSlice(String value, int start, int end) {
        if (value == null || value.length() <= start) {
            return "";
        }
        int safeEnd = end;
        if (safeEnd > value.length()) {
            safeEnd = value.length();
        }
        return value.substring(start, safeEnd);
    }

    private static Properties loadConfig(String classpathFile) {
        Properties props = new Properties();
        InputStream stream = null;
        try {
            stream = Main.class.getClassLoader().getResourceAsStream(classpathFile);
            if (stream != null) {
                props.load(stream);
            }
        } catch (Exception ex) {
            log("Could not load config file " + classpathFile + ": " + ex.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
        applyEnvOverride(props, "RABBITMQ_HOST", "rabbitmq.host");
        applyEnvOverride(props, "RABBITMQ_PORT", "rabbitmq.port");
        applyEnvOverride(props, "RABBITMQ_USERNAME", "rabbitmq.username");
        applyEnvOverride(props, "RABBITMQ_PASSWORD", "rabbitmq.password");
        applyEnvOverride(props, "RABBITMQ_VHOST", "rabbitmq.vhost");
        applyEnvOverride(props, "ACH_INCOMING_PATH", "ach.incoming.path");
        applyEnvOverride(props, "ACH_PROCESSED_PATH", "ach.processed.path");
        applyEnvOverride(props, "ACH_ERROR_PATH", "ach.error.path");
        applyEnvOverride(props, "ACH_POLL_INTERVAL_MS", "ach.poll.interval.ms");
        applyEnvOverride(props, "LEDGER_URL", "ledger.url");
        applyEnvOverride(props, "LEDGER_ACH_ENDPOINT", "ledger.ach.endpoint");
        return props;
    }

    private static void applyEnvOverride(Properties props, String envName, String key) {
        String value = System.getenv(envName);
        if (value != null && value.trim().length() > 0) {
            props.setProperty(key, value);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void ensureDirectory(String path) {
        try {
            Files.createDirectories(Paths.get(path));
        } catch (Exception ex) {
            log("Could not create directory " + path + ": " + ex.getMessage());
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date());
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return builder.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void log(String message) {
        System.out.println("[ZavaACHProcessor] " + message);
    }

    private static class AchBatch {
        private String batchNumber = "";
        private String companyName = "";
        private String effectiveDate = "";
        private String entryCount = "";
        private String totalDebit = "";
        private String totalCredit = "";
        private final List<AchEntry> entries = new ArrayList<AchEntry>();
    }

    private static class AchEntry {
        private String transactionCode;
        private String routingNumber;
        private String accountNumber;
        private String amountCents;
        private String individualId;
        private String individualName;
        private String traceNumber;
    }
}
