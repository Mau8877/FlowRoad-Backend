package sw1.backend.flowroad.services.notifications;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FirebasePushService {

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @PostConstruct
    public void init() {
        if (!firebaseEnabled) {
            log.info("[FCM] Firebase push desactivado.");
            return;
        }

        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("[FCM] Firebase está activado, pero no se configuró firebase.credentials.path.");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM] Firebase ya estaba inicializado.");
            return;
        }

        try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("[FCM] Firebase inicializado correctamente.");
        } catch (IOException exception) {
            log.error("[FCM] Error inicializando Firebase.", exception);
        }
    }

    public void sendToToken(
            String deviceToken,
            String title,
            String body,
            Map<String, String> data) {

        if (!firebaseEnabled) {
            log.info("[FCM] Push omitido porque firebase.enabled=false.");
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("[FCM] Push omitido porque Firebase no está inicializado.");
            return;
        }

        if (deviceToken == null || deviceToken.isBlank()) {
            log.warn("[FCM] Push omitido porque el deviceToken está vacío.");
            return;
        }

        try {
            Map<String, String> safeData = data != null
                    ? new HashMap<>(data)
                    : new HashMap<>();

            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build())
                            .build())
                    .putAllData(safeData)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] Push enviado correctamente. response={}", response);
        } catch (Exception exception) {
            log.error("[FCM] Error enviando push al deviceToken={}", deviceToken, exception);
        }
    }
}