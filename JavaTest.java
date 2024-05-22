import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Lock lock;
    private final int requestLimit;
    private final long intervalInMillis;
    private final AtomicInteger requestCount;
    private long lastResetTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.lock = new ReentrantLock();
        this.requestLimit = requestLimit;
        this.intervalInMillis = timeUnit.toMillis(1);
        this.requestCount = new AtomicInteger(0);
        this.lastResetTime = System.currentTimeMillis();
    }

    public void createDocument(Document document, String signature) {
        try {
            lock.lock();
            waitForRateLimit();

            String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Content-Type", "application/json");

            String jsonPayload = objectMapper.writeValueAsString(document);
            StringEntity entity = new StringEntity(jsonPayload, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);

            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();

            if (responseEntity != null) {
                responseEntity.getContent().close();
            }

            updateRequestCount();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void waitForRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastReset = currentTime - lastResetTime;

        if (timeSinceLastReset >= intervalInMillis) {
            requestCount.set(0);
            lastResetTime = currentTime;
        } else {
            while (requestCount.get() >= requestLimit) {
                lock.unlock();
                Thread.sleep(intervalInMillis - timeSinceLastReset);
                lock.lock();
                timeSinceLastReset = System.currentTimeMillis() - lastResetTime;
            }
        }
    }

    private void updateRequestCount() {
        requestCount.incrementAndGet();
    }

    static class Document {
        private String participantInn;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        // Other fields as needed
    }
}
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);

        // Создание документа
        CrptApi.Document document = new CrptApi.Document();
        document.setParticipantInn("1234567890");
        document.setDocId("123");
        document.setDocStatus("draft");
        document.setDocType("LP_INTRODUCE_GOODS");
        document.setImportRequest(true);
        document.setOwnerInn("0987654321");

        String signature = "signature_string";

        crptApi.createDocument(document, signature);
    }
}
