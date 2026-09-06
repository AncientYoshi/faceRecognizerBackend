#include <WiFi.h>
#include <WiFiClientSecure.h>
#include "esp_camera.h"
#include <ArduinoJson.h>
#include <esp_arduino_version.h>
#include <esp32-hal-cpu.h>
#include <time.h>

// ============================================================
// 1) EDIT THESE BEFORE UPLOADING
// ============================================================
const char* WIFI_SSID = "Redmi Note 10";
const char* WIFI_PASSWORD = "arkarlay";

const char* SERVER_HOST = "smart-attendance-api.duckdns.org";
const uint16_t SERVER_PORT = 443;
const bool RUN_TLS_DIAGNOSTICS = false;

// Nginx supports HTTP/1.1; explicitly advertise it during TLS negotiation.
static const char* TLS_ALPN_PROTOCOLS[] = {"http/1.1", nullptr};

// Must match a device registered in Spring Boot /hardware-devices.
const char* DEVICE_ID = "ROOM101";
const char* DEVICE_KEY = "hyNmRusYfNvnJ6qTD_6n3nFLdHr3wWj-Qke_u2WOvgE";

// Let's Encrypt ISRG Root X1 trust anchor.
// Keep this certificate block unchanged.
static const char ISRG_ROOT_X1[] PROGMEM = R"CERT(
-----BEGIN CERTIFICATE-----
MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw
TzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh
cmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMTUwNjA0MTEwNDM4
WhcNMzUwNjA0MTEwNDM4WjBPMQswCQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJu
ZXQgU2VjdXJpdHkgUmVzZWFyY2ggR3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBY
MTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBAK3oJHP0FDfzm54rVygc
h77ct984kIxuPOZXoHj3dcKi/vVqbvYATyjb3miGbESTtrFj/RQSa78f0uoxmyF+
0TM8ukj13Xnfs7j/EvEhmkvBioZxaUpmZmyPfjxwv60pIgbz5MDmgK7iS4+3mX6U
A5/TR5d8mUgjU+g4rk8Kb4Mu0UlXjIB0ttov0DiNewNwIRt18jA8+o+u3dpjq+sW
T8KOEUt+zwvo/7V3LvSye0rgTBIlDHCNAymg4VMk7BPZ7hm/ELNKjD+Jo2FR3qyH
B5T0Y3HsLuJvW5iB4YlcNHlsdu87kGJ55tukmi8mxdAQ4Q7e2RCOFvu396j3x+UC
B5iPNgiV5+I3lg02dZ77DnKxHZu8A/lJBdiB3QW0KtZB6awBdpUKD9jf1b0SHzUv
KBds0pjBqAlkd25HN7rOrFleaJ1/ctaJxQZBKT5ZPt0m9STJEadao0xAH0ahmbWn
OlFuhjuefXKnEgV4We0+UXgVCwOPjdAvBbI+e0ocS3MFEvzG6uBQE3xDk3SzynTn
jh8BCNAw1FtxNrQHusEwMFxIt4I7mKZ9YIqioymCzLq9gwQbooMDQaHWBfEbwrbw
qHyGO0aoSCqI3Haadr8faqU9GY/rOPNk3sgrDQoo//fb4hVC1CLQJ13hef4Y53CI
rU7m2Ys6xt0nUW7/vGT1M0NPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNV
HRMBAf8EBTADAQH/MB0GA1UdDgQWBBR5tFnme7bl5AFzgAiIyBpY9umbbjANBgkq
hkiG9w0BAQsFAAOCAgEAVR9YqbyyqFDQDLHYGmkgJykIrGF1XIpu+ILlaS/V9lZL
ubhzEFnTIZd+50xx+7LSYK05qAvqFyFWhfFQDlnrzuBZ6brJFe+GnY+EgPbk6ZGQ
3BebYhtF8GaV0nxvwuo77x/Py9auJ/GpsMiu/X1+mvoiBOv/2X/qkSsisRcOj/KK
NFtY2PwByVS5uCbMiogziUwthDyC3+6WVwW6LLv3xLfHTjuCvjHIInNzktHCgKQ5
ORAzI4JMPJ+GslWYHb4phowim57iaztXOoJwTdwJx4nLCgdNbOhdjsnvzqvHu7Ur
TkXWStAmzOVyyghqpZXjFaH3pO3JLF+l+/+sKAIuvtd7u+Nxe5AW0wdeRlN8NwdC
jNPElpzVmbUq4JUagEiuTDkHzsxHpFKVK7q4+63SM1N95R1NbdWhscdCb+ZAJzVc
oyi3B43njTOQ5yOf+1CceWxG1bQVs5ZufpsMljq4Ui0/1lvh+wjChP4kqKOJ2qxq
4RgqsahDYVvTH9w7jXbyLeiNdd8XM2w9U/t7y0Ff/9yi0GE44Za4rF2LN9d11TPA
mRGunUHBcnWEvgJBQl9nJEiU0Zsnvgc/ubhPgXRR4Xq37Z0j4r7g1SgEEzwxA57d
emyPxgcYxn/eR44/KJ4EBs+lVDR3veyJm+kXQ99b21/+jh5Xos1AnX5iItreGCc=
-----END CERTIFICATE-----
)CERT";

// ============================================================
// 2) YOUR ESP32-S3 OUTPUT WIRING
// ============================================================
#define GREEN_LED 4
#define RED_LED   5
#define BUZZER_PIN 6

// ============================================================
// 3) YOUR ESP32-S3 + EXTERNAL OV2640 WIRING
//    PWDN is physically tied to GND.
//    RET/RESET is physically tied to 3.3V.
// ============================================================
#define PWDN_GPIO_NUM   -1
#define RESET_GPIO_NUM  -1

#define SIOC_GPIO_NUM   1
#define SIOD_GPIO_NUM   2

#define VSYNC_GPIO_NUM  7
#define HREF_GPIO_NUM   8
#define PCLK_GPIO_NUM   9
#define XCLK_GPIO_NUM   10

#define Y2_GPIO_NUM     11
#define Y3_GPIO_NUM     12
#define Y4_GPIO_NUM     13
#define Y5_GPIO_NUM     14
#define Y6_GPIO_NUM     15
#define Y7_GPIO_NUM     16
#define Y8_GPIO_NUM     17
#define Y9_GPIO_NUM     18

struct AttendanceResult {
  bool requestSuccessful = false;
  bool verified = false;
  bool attendanceRecorded = false;
  String rollNumber;
  String courseCode;
  int rollCallCount = 0;
  String reason;
};


// ============================================================
// OUTPUT SIGNALS
// Your YL-44 is wired VCC -> 3.3V, GND -> GND, I/O -> GPIO6.
// This module is driven with a square wave because that behavior was verified
// on the physical prototype. Camera XCLK uses a different LEDC timer/channel.
// ============================================================
void allOutputsOff() {
  digitalWrite(GREEN_LED, LOW);
  digitalWrite(RED_LED, LOW);
  digitalWrite(BUZZER_PIN, HIGH);  // active-low module idle state
}

void beep(unsigned int durationMs) {
  tone(BUZZER_PIN, 2000);
  delay(durationMs);
  noTone(BUZZER_PIN);

  // Arduino-ESP32 detaches the LEDC channel in noTone(). Restore GPIO mode
  // before returning the module input to its idle state.
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);
}

void successSignal() {
  digitalWrite(RED_LED, LOW);
  digitalWrite(GREEN_LED, HIGH);

  beep(180);
  delay(100);
  beep(180);

  digitalWrite(GREEN_LED, LOW);
}

void failSignal(unsigned int durationMs = 700) {
  digitalWrite(GREEN_LED, LOW);
  digitalWrite(RED_LED, HIGH);

  beep(durationMs);

  digitalWrite(RED_LED, LOW);
}

// ============================================================
// CAMERA
// ============================================================
bool initCamera() {
  camera_config_t config = {};

  // Leave LEDC channel/timer 0 available for tone() on the buzzer.
  config.ledc_channel = LEDC_CHANNEL_1;
  config.ledc_timer = LEDC_TIMER_1;

  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;

  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;

  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;

  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;

  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  if (psramFound()) {
    // Face detection needs more facial pixels than QVGA usually provides.
    config.frame_size = FRAMESIZE_VGA;  // 640 x 480
    config.jpeg_quality = 10;
    config.fb_count = 2;
    config.fb_location = CAMERA_FB_IN_PSRAM;
    config.grab_mode = CAMERA_GRAB_LATEST;
  } else {
    // Conservative fallback for boards without external PSRAM.
    config.frame_size = FRAMESIZE_QVGA; // 320 x 240
    config.jpeg_quality = 12;
    config.fb_count = 1;
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  }

  esp_err_t error = esp_camera_init(&config);

  if (error != ESP_OK) {
    Serial.printf("Camera initialization failed: 0x%x\n", error);
    return false;
  }

  Serial.println("Camera initialized successfully");
  return true;
}

// ============================================================
// WIFI + CLOCK
// ============================================================
bool connectWiFi() {
  Serial.print("Connecting to WiFi");

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  for (int attempt = 0;
       attempt < 40 && WiFi.status() != WL_CONNECTED;
       attempt++) {
    delay(500);
    Serial.print(".");
  }

  Serial.println();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi connection failed");
    return false;
  }

  // Avoid Wi-Fi power-save interruptions during the TLS handshake/upload.
  WiFi.setSleep(false);

  Serial.println("WiFi connected");
  Serial.print("ESP32 IP: ");
  Serial.println(WiFi.localIP());
  Serial.print("WiFi RSSI: ");
  Serial.print(WiFi.RSSI());
  Serial.println(" dBm");
  return true;
}

bool syncClock() {
  Serial.println("Synchronizing clock for HTTPS...");

  configTime(0, 0, "pool.ntp.org", "time.google.com");

  const time_t minimumValidTime = 1704067200; // 2024-01-01 UTC

  for (int attempt = 0; attempt < 40; attempt++) {
    if (time(nullptr) >= minimumValidTime) {
      Serial.println("TLS clock synchronized");
      return true;
    }
    delay(500);
  }

  Serial.println("Clock synchronization failed");
  return false;
}

bool testTlsHandshake(const char* label, const char* host) {
  Serial.print("TLS diagnostic [");
  Serial.print(label);
  Serial.print("]: ");
  Serial.println(host);

  IPAddress address;
  if (WiFi.hostByName(host, address) != 1) {
    Serial.println("  DNS failed");
    return false;
  }

  Serial.print("  IP: ");
  Serial.println(address);
  Serial.printf("  Free heap: %u bytes\n", ESP.getFreeHeap());

  WiFiClientSecure diagnosticClient;
  diagnosticClient.setInsecure();
  diagnosticClient.setAlpnProtocols(TLS_ALPN_PROTOCOLS);
  diagnosticClient.setHandshakeTimeout(15);
  diagnosticClient.setTimeout(10000);

  if (!diagnosticClient.connect(host, 443)) {
    char errorBuffer[160] = {};
    int errorCode = diagnosticClient.lastError(
        errorBuffer,
        sizeof(errorBuffer)
    );

    Serial.printf("  FAILED: %d, %s\n", errorCode, errorBuffer);
    return false;
  }

  Serial.println("  SUCCESS");
  diagnosticClient.stop();
  return true;
}

// ============================================================
// HTTP RESPONSE HELPERS
// ============================================================
bool readByteWithTimeout(
    Client& client,
    char& value,
    unsigned long timeoutMs = 5000) {
  unsigned long started = millis();

  while (!client.available()) {
    if (!client.connected() || millis() - started > timeoutMs) {
      return false;
    }
    delay(2);
  }

  value = static_cast<char>(client.read());
  return true;
}

String readFixedBody(Client& client, int contentLength) {
  String body;

  if (contentLength > 0) {
    body.reserve(contentLength);
  }

  for (int index = 0; index < contentLength; index++) {
    char value;

    if (!readByteWithTimeout(client, value)) {
      break;
    }

    body += value;
  }

  return body;
}

String readChunkedBody(Client& client) {
  String body;

  while (true) {
    String sizeLine = client.readStringUntil('\n');
    sizeLine.trim();

    int extension = sizeLine.indexOf(';');
    if (extension >= 0) {
      sizeLine = sizeLine.substring(0, extension);
    }

    long chunkSize = strtol(sizeLine.c_str(), nullptr, 16);

    if (chunkSize <= 0) {
      while (client.connected() || client.available()) {
        String trailer = client.readStringUntil('\n');

        if (trailer == "\r" || trailer.length() == 0) {
          break;
        }
      }
      break;
    }

    for (long index = 0; index < chunkSize; index++) {
      char value;

      if (!readByteWithTimeout(client, value)) {
        return body;
      }

      body += value;
    }

    client.readStringUntil('\n');
  }

  return body;
}

String readUntilClosed(Client& client) {
  String body;
  unsigned long lastDataAt = millis();

  while (client.connected() || client.available()) {
    while (client.available()) {
      body += static_cast<char>(client.read());
      lastDataAt = millis();
    }

    if (millis() - lastDataAt > 5000) {
      break;
    }

    delay(2);
  }

  return body;
}

// ============================================================
// SEND CAMERA JPEG TO SPRING BOOT
// POST /hardware/v1/attendance/identify
// ============================================================
AttendanceResult sendImage(camera_fb_t* frame) {
  AttendanceResult result;

  IPAddress serverIp;

  Serial.print("Resolving ");
  Serial.println(SERVER_HOST);

  if (WiFi.hostByName(SERVER_HOST, serverIp) != 1) {
    Serial.println("DNS resolution failed");
    result.reason = "DNS_FAILED";
    return result;
  }

  Serial.print("Resolved IP: ");
  Serial.println(serverIp);

  WiFiClientSecure client;

  // Diagnostic only. Restore setCACert after resolving the issue.
  client.setInsecure();
  client.setAlpnProtocols(TLS_ALPN_PROTOCOLS);
  client.setHandshakeTimeout(30);
  client.setTimeout(15000);

  Serial.printf("Free heap before TLS: %u bytes\n", ESP.getFreeHeap());

  if (!client.connect(SERVER_HOST, SERVER_PORT)) {
    char errorBuffer[160] = {};
    int errorCode = client.lastError(errorBuffer, sizeof(errorBuffer));

    Serial.printf(
        "TLS connection failed: %d, %s\n",
        errorCode,
        errorBuffer
    );

    result.reason = "TLS_FAILED";
    return result;
  }

  Serial.println("HTTPS connection successful");

  const String boundary = "----ESP32SmartAttendanceBoundary";

  const String head =
      "--" + boundary + "\r\n"
      "Content-Disposition: form-data; name=\"image\"; filename=\"face.jpg\"\r\n"
      "Content-Type: image/jpeg\r\n\r\n";

  const String tail =
      "\r\n--" + boundary + "--\r\n";

  const size_t contentLength =
      head.length() + frame->len + tail.length();

  const String path =
      "/hardware/v1/attendance/identify";

  client.print("POST " + path + " HTTP/1.1\r\n");
  client.print("Host: " + String(SERVER_HOST) + "\r\n");
  client.print("X-Device-Id: " + String(DEVICE_ID) + "\r\n");
  client.print("X-Device-Key: " + String(DEVICE_KEY) + "\r\n");
  client.print("Accept: application/json\r\n");
  client.print(
      "Content-Type: multipart/form-data; boundary=" +
      boundary +
      "\r\n");
  client.print(
      "Content-Length: " +
      String(contentLength) +
      "\r\n");
  client.print("Connection: close\r\n\r\n");

  client.print(head);

  const size_t chunkSize = 1024;

  for (size_t sent = 0; sent < frame->len;) {
    size_t amount =
        min(chunkSize, frame->len - sent);

    size_t written =
        client.write(frame->buf + sent, amount);

    if (written == 0) {
      result.reason = "UPLOAD_FAILED";
      Serial.println("JPEG upload failed");
      client.stop();
      return result;
    }

    sent += written;
  }

  client.print(tail);

  String statusLine = client.readStringUntil('\n');
  statusLine.trim();

  Serial.print("HTTP: ");
  Serial.println(statusLine);

  int firstSpace = statusLine.indexOf(' ');

  int statusCode =
      firstSpace < 0
          ? 0
          : statusLine.substring(firstSpace + 1).toInt();

  int responseLength = -1;
  bool chunked = false;

  while (client.connected() || client.available()) {
    String line = client.readStringUntil('\n');

    if (line == "\r" || line.length() == 0) {
      break;
    }

    line.trim();

    String lower = line;
    lower.toLowerCase();

    if (lower.startsWith("content-length:")) {
      responseLength =
          line.substring(line.indexOf(':') + 1).toInt();
    } else if (
        lower.startsWith("transfer-encoding:") &&
        lower.indexOf("chunked") >= 0) {
      chunked = true;
    }
  }

  String body =
      chunked
          ? readChunkedBody(client)
          : (
              responseLength >= 0
                  ? readFixedBody(client, responseLength)
                  : readUntilClosed(client)
            );

  client.stop();

  Serial.print("Response: ");
  Serial.println(body);

  if (statusCode != 200) {
    JsonDocument errorDocument;

    if (
        deserializeJson(errorDocument, body) ==
        DeserializationError::Ok) {

      String message =
          errorDocument["message"] | "";

      if (message.startsWith("No active attendance")) {
        result.reason = "NO_ACTIVE_SESSION";
      } else if (message.startsWith("Multiple active")) {
        result.reason = "DEVICE_BINDING_ERROR";
      }
    }

    if (result.reason.length() == 0) {
      result.reason =
          "HTTP_" + String(statusCode);
    }

    return result;
  }

  JsonDocument document;

  DeserializationError jsonError =
      deserializeJson(document, body);

  if (jsonError) {
    result.reason = "INVALID_RESPONSE";
    return result;
  }

  result.requestSuccessful = true;
  result.verified =
      document["verified"] | false;
  result.attendanceRecorded =
      document["attendanceRecorded"] | false;
  result.rollNumber =
      document["rollNumber"] | "";
  result.courseCode =
      document["courseCode"] | "";
  result.rollCallCount =
      document["rollCallCount"] | 0;
  result.reason =
      document["reason"] | "";

  return result;
}

// ============================================================
// ONE ATTENDANCE ATTEMPT
// ============================================================
void checkAttendance() {
  Serial.println();
  Serial.println("================================");
  Serial.println("Center your face 30-60 cm from the camera...");
  delay(1500);

  // Discard anything queued before the user moved into position, then allow
  // the sensor to produce a new frame for this attendance attempt.
  camera_fb_t* staleFrame = esp_camera_fb_get();
  if (staleFrame) {
    esp_camera_fb_return(staleFrame);
  }
  delay(200);

  camera_fb_t* frame =
      esp_camera_fb_get();

  if (!frame) {
    Serial.println("Camera capture FAILED");
    failSignal(900);
    return;
  }

  Serial.println("Photo captured successfully");
  Serial.print("Resolution: ");
  Serial.print(frame->width);
  Serial.print(" x ");
  Serial.println(frame->height);
  Serial.print("JPEG size: ");
  Serial.print(frame->len);
  Serial.println(" bytes");

  AttendanceResult result =
      sendImage(frame);

  esp_camera_fb_return(frame);

  if (!result.requestSuccessful) {
    Serial.print("Request failed: ");
    Serial.println(result.reason);

    failSignal(700);
    return;
  }

  if (result.verified) {
    Serial.println("FACE VERIFIED");
    Serial.print("Roll number: ");
    Serial.println(result.rollNumber);
    Serial.print("Course: ");
    Serial.println(result.courseCode);
    Serial.print("Attendance recorded: ");
    Serial.println(
        result.attendanceRecorded
            ? "YES"
            : "NO - already verified");

    if (result.rollCallCount > 0) {
      Serial.print("Roll call count: ");
      Serial.println(result.rollCallCount);
    }

    successSignal();
    return;
  }

  Serial.print("FACE NOT VERIFIED: ");
  Serial.println(result.reason);
  failSignal(900);
}

// ============================================================
// SETUP
// ============================================================
void setup() {
  Serial.begin(115200);
  delay(2000);

  if (!setCpuFrequencyMhz(240)) {
    Serial.println("WARNING: Could not set CPU frequency to 240 MHz");
  }
  Serial.printf(
      "Arduino-ESP32 core: %d.%d.%d\n",
      ESP_ARDUINO_VERSION_MAJOR,
      ESP_ARDUINO_VERSION_MINOR,
      ESP_ARDUINO_VERSION_PATCH
  );
  Serial.printf("CPU frequency: %u MHz\n", getCpuFrequencyMhz());

  pinMode(GREEN_LED, OUTPUT);
  pinMode(RED_LED, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  allOutputsOff();

  Serial.println();
  Serial.println("===== SMART ATTENDANCE ESP32-S3 =====");
  Serial.println("Buzzer self-test...");
  beep(300);

  if (psramFound()) {
    Serial.println("PSRAM detected");
  } else {
    Serial.println("WARNING: PSRAM not detected");
  }

  if (!initCamera()) {
    Serial.println("Stopping because camera initialization failed");
    failSignal(1200);

    while (true) {
      delay(1000);
    }
  }

  if (!connectWiFi()) {
    failSignal(800);
    return;
  }

  if (!syncClock()) {
    failSignal(800);
    return;
  }

  if (RUN_TLS_DIAGNOSTICS) {
    Serial.println();
    Serial.println("===== TLS DIAGNOSTICS =====");
    testTlsHandshake("control", "www.google.com");
    testTlsHandshake("attendance API", SERVER_HOST);
    Serial.println("===== END TLS DIAGNOSTICS =====");
    Serial.println();
  }

  Serial.println("System ready");
  Serial.println("Camera -> HTTPS Spring Boot -> FastAPI -> attendance");
}

// ============================================================
// LOOP
// ============================================================
void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi disconnected. Reconnecting...");

    if (!connectWiFi()) {
      failSignal(500);
      delay(5000);
      return;
    }

    syncClock();
  }

  if (
      time(nullptr) < 1704067200 &&
      !syncClock()) {
    failSignal(500);
    delay(5000);
    return;
  }

  checkAttendance();

  // For bench testing, attempt once every 5 seconds.
  delay(5000);
}
