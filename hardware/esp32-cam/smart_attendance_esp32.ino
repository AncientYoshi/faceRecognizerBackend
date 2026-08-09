#include <WiFi.h>
#include "esp_camera.h"
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <ArduinoJson.h>

// Update these values before flashing the board.
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Use the LAN IP of the computer running Spring Boot, never localhost.
const char* SERVER_HOST = "192.168.1.10";
const uint16_t SERVER_PORT = 8080;
const char* SESSION_ID = "YOUR-ACTIVE-ATTENDANCE-SESSION-UUID";
const char* DEVICE_ID = "CLASSROOM-01";
const char* DEVICE_KEY = "change-this-device-secret";

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_SDA 13
#define OLED_SCL 14
#define OLED_ADDRESS 0x3C
#define BUZZER_PIN 15

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// AI-Thinker ESP32-CAM + OV2640 pin mapping.
#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27
#define Y9_GPIO_NUM 35
#define Y8_GPIO_NUM 34
#define Y7_GPIO_NUM 39
#define Y6_GPIO_NUM 36
#define Y5_GPIO_NUM 21
#define Y4_GPIO_NUM 19
#define Y3_GPIO_NUM 18
#define Y2_GPIO_NUM 5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22

struct AttendanceResult {
  bool requestSuccessful = false;
  bool verified = false;
  bool attendanceRecorded = false;
  String rollNumber;
  String reason;
};

void showMessage(const String& title, const String& large = "", const String& footer = "") {
  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(1);
  display.setCursor(0, 5);
  display.println(title);

  if (large.length() > 0) {
    display.setTextSize(2);
    display.setCursor(0, 25);
    display.println(large);
  }
  if (footer.length() > 0) {
    display.setTextSize(1);
    display.setCursor(0, 52);
    display.println(footer);
  }
  display.display();
}

void beep(unsigned int durationMs) {
  digitalWrite(BUZZER_PIN, HIGH);
  delay(durationMs);
  digitalWrite(BUZZER_PIN, LOW);
}

void successBeep() {
  beep(140);
  delay(100);
  beep(140);
}

bool initCamera() {
  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
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
  config.frame_size = FRAMESIZE_VGA;
  config.jpeg_quality = 10;
  config.fb_count = psramFound() ? 2 : 1;
  config.fb_location = psramFound() ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;
  config.grab_mode = CAMERA_GRAB_LATEST;

  esp_err_t error = esp_camera_init(&config);
  if (error != ESP_OK) {
    Serial.printf("Camera initialization failed: 0x%x\n", error);
    return false;
  }
  return true;
}

bool connectWiFi() {
  showMessage("SMART ATTENDANCE", "", "Connecting WiFi");
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  for (int attempt = 0; attempt < 30 && WiFi.status() != WL_CONNECTED; attempt++) {
    delay(500);
    Serial.print('.');
  }
  if (WiFi.status() != WL_CONNECTED) {
    showMessage("WIFI ERROR", "", "Check credentials");
    return false;
  }

  Serial.println();
  Serial.print("ESP32 IP: ");
  Serial.println(WiFi.localIP());
  showMessage("SMART ATTENDANCE", "", "WiFi connected");
  return true;
}

bool readByteWithTimeout(WiFiClient& client, char& value, unsigned long timeoutMs = 5000) {
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

String readFixedBody(WiFiClient& client, int contentLength) {
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

String readChunkedBody(WiFiClient& client) {
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
      while (client.connected()) {
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

String readUntilClosed(WiFiClient& client) {
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

AttendanceResult sendImage(camera_fb_t* frame) {
  AttendanceResult result;
  WiFiClient client;
  client.setTimeout(10000);

  if (!client.connect(SERVER_HOST, SERVER_PORT)) {
    result.reason = "SERVER_OFFLINE";
    return result;
  }

  const String boundary = "----ESP32SmartAttendanceBoundary";
  const String head = "--" + boundary + "\r\n"
      "Content-Disposition: form-data; name=\"image\"; filename=\"face.jpg\"\r\n"
      "Content-Type: image/jpeg\r\n\r\n";
  const String tail = "\r\n--" + boundary + "--\r\n";
  const size_t contentLength = head.length() + frame->len + tail.length();
  const String path = "/hardware/v1/attendance/identify?sessionId=" + String(SESSION_ID);

  client.print("POST " + path + " HTTP/1.1\r\n");
  client.print("Host: " + String(SERVER_HOST) + ":" + String(SERVER_PORT) + "\r\n");
  client.print("X-Device-Id: " + String(DEVICE_ID) + "\r\n");
  client.print("X-Device-Key: " + String(DEVICE_KEY) + "\r\n");
  client.print("Accept: application/json\r\n");
  client.print("Content-Type: multipart/form-data; boundary=" + boundary + "\r\n");
  client.print("Content-Length: " + String(contentLength) + "\r\n");
  client.print("Connection: close\r\n\r\n");
  client.print(head);

  const size_t chunkSize = 1024;
  for (size_t sent = 0; sent < frame->len;) {
    size_t amount = min(chunkSize, frame->len - sent);
    size_t written = client.write(frame->buf + sent, amount);
    if (written == 0) {
      result.reason = "UPLOAD_FAILED";
      client.stop();
      return result;
    }
    sent += written;
  }
  client.print(tail);

  String statusLine = client.readStringUntil('\n');
  statusLine.trim();
  int firstSpace = statusLine.indexOf(' ');
  int statusCode = firstSpace < 0 ? 0 : statusLine.substring(firstSpace + 1).toInt();
  int responseLength = -1;
  bool chunked = false;

  while (client.connected()) {
    String line = client.readStringUntil('\n');
    if (line == "\r" || line.length() == 0) {
      break;
    }
    line.trim();
    String lower = line;
    lower.toLowerCase();
    if (lower.startsWith("content-length:")) {
      responseLength = line.substring(line.indexOf(':') + 1).toInt();
    } else if (lower.startsWith("transfer-encoding:") && lower.indexOf("chunked") >= 0) {
      chunked = true;
    }
  }

  String body = chunked
      ? readChunkedBody(client)
      : (responseLength >= 0 ? readFixedBody(client, responseLength) : readUntilClosed(client));
  client.stop();

  Serial.println(statusLine);
  Serial.println(body);
  if (statusCode != 200) {
    result.reason = "HTTP_" + String(statusCode);
    return result;
  }

  JsonDocument document;
  DeserializationError jsonError = deserializeJson(document, body);
  if (jsonError) {
    result.reason = "INVALID_RESPONSE";
    return result;
  }

  result.requestSuccessful = true;
  result.verified = document["verified"] | false;
  result.attendanceRecorded = document["attendanceRecorded"] | false;
  result.rollNumber = document["rollNumber"] | "";
  result.reason = document["reason"] | "";
  return result;
}

void checkAttendance() {
  showMessage("SMART ATTENDANCE", "", "Look at camera");
  delay(1000);

  camera_fb_t* frame = esp_camera_fb_get();
  if (!frame) {
    showMessage("CAMERA ERROR", "", "Capture failed");
    delay(2000);
    return;
  }

  showMessage("VERIFYING", "", "Please wait");
  AttendanceResult result = sendImage(frame);
  esp_camera_fb_return(frame);

  if (!result.requestSuccessful) {
    showMessage("SERVER ERROR", "", result.reason);
    delay(2500);
    return;
  }
  if (result.verified) {
    showMessage(
        "ATTENDANCE",
        result.rollNumber,
        result.attendanceRecorded ? "VERIFIED" : "ALREADY VERIFIED"
    );
    successBeep();
    delay(5000);
    return;
  }
  if (result.reason == "NO_FACE_DETECTED") {
    showMessage("SMART ATTENDANCE", "", "Look at camera");
    delay(1200);
    return;
  }

  showMessage("NOT RECOGNIZED", "", result.reason);
  beep(220);
  delay(2500);
}

void setup() {
  Serial.begin(115200);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  Wire.begin(OLED_SDA, OLED_SCL);
  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS)) {
    Serial.println("OLED initialization failed");
  }
  showMessage("SMART ATTENDANCE", "", "Starting");

  if (!initCamera()) {
    showMessage("CAMERA ERROR", "", "Restart device");
    while (true) {
      delay(1000);
    }
  }
  connectWiFi();
  delay(1000);
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWiFi();
    delay(1000);
    return;
  }
  checkAttendance();
  delay(1000);
}
