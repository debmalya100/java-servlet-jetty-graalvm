# 🎯 Microservice SSE + REST + GraalVM Native

This high-performance Java microservice delivers real-time data using **Server‑Sent Events (SSE)** with **GraalVM Native Image** compilation for ultra-fast startup and minimal memory footprint. It exposes RESTful endpoints, supports JWT authentication, and integrates with Cloud Run for scalable deployment.

![Server Sent Events](https://tse2.mm.bing.net/th/id/OIP.8LNjGUSBSNXUosFoTr8m8gHaEK?pid=Api)

---

## 📁 Project Structure

```
microservice-sse-rest-php-as-clirnet/
├── .github/workflows       # CI/CD pipelines
├── src/
│   ├── main/java/com/example/sse/
│   │   ├── cache           # Redis caching (Jedis)
│   │   ├── config          # Configuration management
│   │   ├── database        # MySQL with HikariCP
│   │   ├── exception       # Global exception handling
│   │   ├── model           # Data models & POJOs
│   │   ├── service         # Business logic & async init
│   │   ├── servlet         # SSE servlet implementation
│   │   ├── util            # JWT utilities & helpers
│   │   └── test            # Unit & integration tests
│   └── resources/
│       ├── templates/      # HTML templates
│       ├── META-INF/native-image/ # GraalVM config
│       └── application.properties # App configuration
├── www/                    # PHP client examples
├── Dockerfile              # Standard Docker build
├── Dockerfile.native       # GraalVM native image build
├── docker-compose.yml      # Local development
├── build-native.sh/.bat    # Native build scripts
└── cloud-run.env          # Cloud Run environment config
```

---

## ⚙️ Built With

| Feature              | Technologies                              |
|----------------------|-------------------------------------------|
| **Core**             | Java 21, GraalVM Native Image, Jetty v11 |
| **Caching**          | Redis (Jedis) with TTL strategies        |
| **Storage**          | MySQL with HikariCP connection pooling   |
| **Data Format**      | Jackson (JSON) with native image support |
| **Security**         | JWT (`jjwt`) with nested token parsing   |
| **Testing**          | JUnit 4                                   |
| **Logging**          | Logback with native image compatibility  |
| **Containerization**| Docker with multi-stage builds           |
| **Cloud Platform**   | Google Cloud Run with Cloud SQL          |
| **Build Tool**       | Maven with native compilation profiles   |

---

## 🚀 GraalVM Native Image Features

- **Ultra-fast startup**: ~50ms startup time vs ~2-3s JVM
- **Minimal memory footprint**: ~50MB RAM vs ~200MB+ JVM
- **Reflection configuration**: Pre-configured for Jackson, JDBC, JWT
- **Native compilation**: Ahead-of-time compilation for optimal performance
- **Docker optimization**: Multi-stage builds with minimal base images
- **Cloud Run ready**: Environment detection for local vs cloud deployment

---

## ☁️ Cloud Run Deployment

### Environment Detection
- **Local**: Direct IP connection to MySQL (35.244.38.178)
- **Cloud Run**: Cloud SQL Socket Factory with IAM authentication
- **Auto-detection**: Uses `CLOUD_SQL_CONNECTION_NAME` environment variable

### Key Features
- **Zero-downtime deployments**: Native image enables instant scaling
- **Cost optimization**: Pay only for actual request processing time
- **Automatic scaling**: From 0 to 1000+ instances based on demand
- **Integrated monitoring**: Cloud Logging and Cloud Monitoring support

---

## 🏛 Architecture & Patterns

- **Singletons**: `ConfigManager`, `DataServiceImpl`, Factories  
- **Factory**: `DatabaseProviderFactory`, `CacheProviderFactory`  
- **Template Method**: `JettySSEServer` lifecycle  
- **Global Exception Handling**: Central error-to-response mapping  
- **Async Init**: `CompletableFuture` for fail-fast service startup  
- **Graceful Degradation**: Partial functionality if Redis/DB fail

---

## 📡 SSE Servlet Flow

1. **Authentication**: Client requests `/api/sse` with JWT token
2. **Token Validation**: JWT parsed and user ID extracted from nested structure
3. **User Status Check**: Validates user status = "3" (active users only)
4. **Connection Setup**: Establishes persistent SSE connection with proper headers
5. **Data Streaming**: Real-time polling for comments, polls, and session data
6. **Caching Strategy**: Redis caching with TTL for optimal performance
7. **Error Handling**: Graceful degradation with proper HTTP status codes
8. **Connection Management**: Automatic cleanup and resource management

---

## 🧪 Testing

* Unit & integration via JUnit
* Connectivity checks for Redis/MySQL
* SSE lifecycle tested with Jetty
* Test suite located: `src/main/java/com/example/sse/test/`

To ignore test failures in Maven:

```xml
<configuration>
  <testFailureIgnore>true</testFailureIgnore>
</configuration>
```

---

## 🚀 Getting Started

### Standard JVM Build
```bash
git clone https://github.com/clirnetspl/microservice-sse-rest-php-as-clirnet.git
cd microservice-sse-rest-php-as-clirnet
mvn clean package
java -jar target/sse-jetty-server-1.0-SNAPSHOT.jar
```

### GraalVM Native Image Build
```bash
# Using Docker (Recommended)
docker build -f Dockerfile.native -t sse-server-native .
docker run -p 8080:8080 sse-server-native

# Or using local GraalVM installation
./build-native.sh  # Linux/Mac
build-native.bat   # Windows
```

### Cloud Run Deployment
```bash
# Build and deploy to Cloud Run
gcloud run deploy sse-server \
  --source . \
  --platform managed \
  --region asia-south1 \
  --set-env-vars CLOUD_SQL_CONNECTION_NAME=your-project:region:instance
```

**Default port**: 8080 (configurable via `PORT` environment variable)

---

## ⚙️ Configuration

### Application Properties
```properties
# Database Configuration
db.host=35.244.38.178
db.port=3306
db.name=clirnetDBmain
db.user=prod_dashboard
db.password=your_password

# Redis Configuration
redis.host=redis-13421.fcrce171.ap-south-1-1.ec2.redns.redis-cloud.com
redis.port=13421
redis.password=your_redis_password

# Server Configuration
server.port=8080
```

### Environment Variables (Cloud Run)
```bash
CLOUD_SQL_CONNECTION_NAME=clirnetapp:asia-south1:clirnet-db-mysql8
DB_NAME=clirnetDBmain
DB_USER=prod_dashboard
DB_PASSWORD=your_password
PORT=8080
```

### User Status Validation
- **Active users**: Status = "3" (not "1")
- **JWT Authentication**: Nested token structure supported
- **Session-based streaming**: Real-time polls and comments

---

## 🧭 Endpoints

| Path          | Method | Description                    |
|---------------|--------|--------------------------------|
| `/api/sse`    | GET    | SSE stream with real-time data |
| `/api/health` | GET    | JSON health check              |
| `/api/status` | GET    | HTML page with uptime & stats  |

---

## ⚡ Performance Metrics

### GraalVM Native vs JVM
| Metric              | Native Image | JVM        | Improvement |
|---------------------|--------------|------------|-------------|
| **Startup Time**    | ~50ms        | ~2-3s      | 40-60x      |
| **Memory Usage**    | ~50MB        | ~200MB+    | 4x          |
| **Image Size**      | ~350MB       | ~500MB+    | 30%         |
| **Cold Start**      | Instant      | 2-3s       | Near-zero   |

### Connection Handling
- **Concurrent SSE connections**: 1000+ per instance
- **Database connection pooling**: HikariCP with optimized settings
- **Redis caching**: Sub-millisecond response times
- **Memory efficiency**: Minimal garbage collection overhead

---

## 📸 Visual Reference

![SSE Overview](https://tse1.mm.bing.net/th/id/OIP.cFB91AzwY6dM-xojDa99KQHaDi?pid=Api)
![SSE Lifecycle](https://tse1.mm.bing.net/th/id/OIP.KLTMSoUy_70gkuHks4FVyQHaEc?pid=Api)
![Reconnect Flow](https://tse1.mm.bing.net/th/id/OIP.n_0kQCZVQcW3BOzCXEmcIQHaGW?pid=Api)

---

## 📋 License

MIT License. Use responsibly and extend as needed.

---