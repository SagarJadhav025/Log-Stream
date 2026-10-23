# 📜 Project 2: Log Stream

## 🎯 About the Project
This project is a custom observability and log analytics platform. It is designed to act like an ELK stack but built from scratch. It allows organizations to ingest millions of log lines, index them instantly, and trigger alerts for specific error patterns.

## ✨ Key Features
*   🚀 **High-Speed Ingestion:** Uses a Java gRPC server to receive up to 10,000 logs per second.
*   🔍 **Instant Search:** Embeds Apache Lucene to search 1 million indexed log records in under 50 milliseconds.
*   🚨 **Smart Alerts:** A background Java engine that runs custom queries and triggers webhooks for error spikes.
*   📊 **Visual Dashboard:** A React frontend with Apache ECharts to show log volumes over time.
*   ⚡ **Live Tail:** Streams incoming logs directly to the UI using WebSockets.

## 💻 Tech Stack
*   **Backend:** Java, Spring Boot
*   **API & Streaming:** gRPC, WebSockets
*   **Database & Search:** Apache Lucene
*   **Frontend:** React.js
*   **Data Visualization:** Apache ECharts

## 🧑‍🤝‍🧑 Team Members & Roles
*   🧑‍💻 **@SagarJadhav025:** Java gRPC Server & Data Ingestion
*   ⚙️ **@kaustubh-ingle:** Apache Lucene Integration & Indexing
*   🗂️ **@Mehrajfathima-782:** Java Alerting Engine & Webhooks
*   🖥️ **@mirfan-dev:** React Dashboard & ECharts Setup
*   🔍 **@sakshikakade788-alt:** Search Logic & WebSocket Live Tail

## 🚀 How to Run the Project
1. **Clone the repository:**
   `git clone https://github.com/SagarJadhav025/Log-Stream.git`
2. **Open in IDE:** Open the folder in IntelliJ IDEA.
3. **Build the project:** Let Maven download all dependencies (like Spring Boot and gRPC).
4. **Run the server:** Start the Spring Boot application from the main class.