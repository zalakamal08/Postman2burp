# Postman2Burp 🚀

Transform Burp Suite into a fully-fledged API testing workstation! **Postman2Burp** is a modern Burp Suite extension that natively imports Postman Collections, cURL commands, and OpenAPI specifications right into your Burp workspace.

Say goodbye to manually copy-pasting headers, wrestling with `{{variables}}`, or switching back and forth between tools.


## ✨ Key Features

1. **Multi-Format Import Engine**
   - **Postman Collections (v2 / v2.1):** Retains your folder structures and request names natively.
   - **OpenAPI / Swagger (v3):** Import from local `.json`/`.yaml` files or directly from a live remote URL.
   - **Raw cURL Commands:** Paste multi-line cURL commands directly into the GUI.

2. **Live Environment Variables (`{{placeholders}}`)**
   - Seamlessly handles `{{baseUrl}}`, `{{token}}`, and other variables.
   - Variables are resolved *on-the-fly* when you click Send.
   - Includes a built-in Environment Manager to edit values live without needing to re-import collections.

3. **Postman-Like Workspace UI**
   - Clean, searchable tree navigation.
   - Distinct, color-coded HTTP method badges (e.g., `GET`, `POST`, `DELETE`).
   - Detailed Request Inspector (Headers, Body, Auth).

4. **Actionable Workflows**
   - **Manual Testing:** Click any request to inspect it, then send it to **Repeater**, **Intruder**, or **Active Scanner** with one click.
   - **Batch Dispatch:** Right-click a folder to silently dispatch *all* requests inside directly to Repeater or Intruder.

## 📦 Installation

To use the extension, download the pre-compiled `.jar` file from GitHub Releases.

1. Go to the [Releases page](../../releases/latest).
2. Download the latest `Postman2Burp.jar`.
3. Open Burp Suite.
4. Go to **Extensions** -> **Installed** -> click **Add**.
5. Set *Extension Type* to **Java**, browse to the downloaded `Postman2Burp.jar`, and click **Next**.
6. A new tab named **"Postman2Burp"** will appear in the top bar!

## 🛠️ Building from Source

If you want to compile the extension yourself, you'll need JDK 17+ and Maven.

```bash
# Clone the repository
git clone https://github.com/zalakamal08/Postman2burp.git
cd Postman2burp

# Build the fat JAR using Maven
mvn clean package -DskipTests
```

The compiled `.jar` will be available in the `target/` directory (e.g., `target/postmantoburp-1.0.jar`), which you can load directly into Burp Suite.

## 🤝 Contributing

Pull requests are welcome! If you find a bug or want to suggest a feature:
1. Open an issue detailing the problem/feature.
2. Fork the repository and create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 📄 License
This project is open-source. Please check the LICENSE file for details.
