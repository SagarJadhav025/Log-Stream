# 📜 Project 2: Log Stream

## 🎯 About the Project
This project is a custom observability and log analytics platform. It is designed to act like an ELK stack but built from scratch. It allows organizations to ingest millions of log lines, index them instantly, and trigger alerts for specific error patterns.

## ✨ Key Features
*   🚀 **High-Speed Ingestion:** Uses a Java gRPC server to receive up to 10,000 logs per second.
*   🔍 **Instant Search:** Embeds Apache Lucene to search 1 million indexed log records in under 50 milliseconds.
*   🚨 **Smart Alerts & Webhook Triggers:** A background Java rule engine that evaluates queries against Lucene thresholds and triggers webhooks/emails.
*   📊 **Visual Dashboard:** A React frontend with Apache ECharts to show log volumes over time.
*   ⚡ **Live Tail:** Streams incoming logs directly to the UI using WebSockets.

## 💻 Tech Stack
*   **Backend:** Java 21, Spring Boot
*   **API & Streaming:** gRPC, WebSockets, REST APIs
*   **Database & Search:** Apache Lucene 10
*   **Frontend:** React.js
*   **Data Visualization:** Apache ECharts

## 🧑‍🤝‍🧑 Team Members & Roles
*   🧑‍💻 **@SagarJadhav025:** Java gRPC Server & Data Ingestion
*   ⚙️ **@kaustubh-ingle:** Apache Lucene Integration & Indexing
*   🗂️ **@Mehrajfathima-782:** Java Alerting Engine & Webhooks *(Completed by Sagar Jadhav)*
*   🖥️ **@mirfan-dev:** React Dashboard & ECharts Setup
*   🔍 **@sakshikakade788-alt:** Search Logic & WebSocket Live Tail

## 🚨 Alerting & Webhooks API
*   **`GET /api/alerts/rules`**: List configured alert rules.
*   **`POST /api/alerts/rules`**: Create or update an alert rule (query, threshold, window, webhook URL, email).
*   **`POST /api/alerts/evaluate`**: Run an immediate scan of all enabled rules against Lucene.
*   **`POST /api/alerts/evaluate/{id}`**: Manually evaluate a specific rule.
*   **`POST /api/alerts/test-webhook`**: Send a test notification payload to verify webhook endpoints.
*   **`GET /api/alerts/history`**: View the delivery audit history of dispatched webhooks and simulated emails.
*   **`POST /api/alerts/webhook-sink`**: Built-in mock webhook endpoint for testing incoming alerts locally.

## 🚀 How to Run the Project
1. **Clone the repository:**
   `git clone https://github.com/SagarJadhav025/Log-Stream.git`
2. **Open in IDE:** Open the folder in IntelliJ IDEA / VS Code.
3. **Build the project:** Let Maven download all dependencies (like Spring Boot and gRPC).
4. **Seed 1 Million Logs (Optional):**
   Run with `--logstream.seed=true` to index 1,000,000 mock logs for benchmarking and alert testing.
5. **Run the server:** Start the Spring Boot application from `LogstreamApplication`.

[//]: # (   [logstream] [nio-8080-exec-2] c.a.l.a.d.WebhookNotificationDispatcher)