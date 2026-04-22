# Smart Campus Sensor & Room Management API

A RESTful API built with JAX-RS (Jersey) deployed on Apache Tomcat for managing university campus rooms and IoT sensors.

---

## How to Build and Run

### Prerequisites
- Java 11+
- Apache Maven 3.6+
- Apache Tomcat 11

### Option 1 — Run via NetBeans
1. Open the project in NetBeans
2. Add Tomcat 11 under Tools → Servers
3. Right-click project → Run
4. API starts at: `http://localhost:8080/smartcampus-api/api/v1/discovery`
5. Tomcat credentials if prompted: **username: admin / password: admin**

### Option 2 — Deploy WAR manually
1. Run `mvn clean package` to build
2. Copy `target/smartcampus-api.war` into Tomcat's `webapps/` folder
3. Start Tomcat by running `bin/startup.bat`
4. API is available at: `http://localhost:8080/smartcampus-api/api/v1/discovery`

---

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | /api/v1/discovery | API metadata and links |
| GET | /api/v1/rooms | Get all rooms |
| POST | /api/v1/rooms | Create a room |
| GET | /api/v1/rooms/{id} | Get room by ID |
| DELETE | /api/v1/rooms/{id} | Delete room |
| GET | /api/v1/sensors | Get all sensors |
| GET | /api/v1/sensors?type=CO2 | Filter sensors by type |
| POST | /api/v1/sensors | Create a sensor |
| GET | /api/v1/sensors/{id} | Get sensor by ID |
| GET | /api/v1/sensors/{id}/readings | Get reading history |
| POST | /api/v1/sensors/{id}/readings | Add a new reading |

---

## Sample curl Commands

### 1. Discovery endpoint
```bash
curl -X GET http://localhost:8080/smartcampus-api/api/v1/discovery
```

### 2. Get all rooms
```bash
curl -X GET http://localhost:8080/smartcampus-api/api/v1/rooms
```

### 3. Create a new room
```bash
curl -X POST http://localhost:8080/smartcampus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-201","name":"Engineering Lab","capacity":40}'
```

### 4. Create sensor with valid roomId
```bash
curl -X POST http://localhost:8080/smartcampus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":20.0,"roomId":"LIB-301"}'
```

### 5. Filter sensors by type
```bash
curl -X GET http://localhost:8080/smartcampus-api/api/v1/sensors?type=CO2
```

### 6. Post a sensor reading
```bash
curl -X POST http://localhost:8080/smartcampus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.7}'
```

### 7. Get all readings for a sensor
```bash
curl -X GET http://localhost:8080/smartcampus-api/api/v1/sensors/TEMP-001/readings
```

### 8. Delete room with sensors — triggers 409 Conflict
```bash
curl -X DELETE http://localhost:8080/smartcampus-api/api/v1/rooms/LIB-301
```

### 9. Create sensor with invalid roomId — triggers 422
```bash
curl -X POST http://localhost:8080/smartcampus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEST-001","type":"CO2","status":"ACTIVE","currentValue":0.0,"roomId":"FAKE-999"}'
```

### 10. Post reading to MAINTENANCE sensor — triggers 403
```bash
curl -X POST http://localhost:8080/smartcampus-api/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5.0}'
```

---

## Report: Answers to Coursework Questions

### Part 1.1 — JAX-RS Resource Lifecycle

By default, JAX-RS creates a new instance of each resource class for every incoming HTTP request (per-request lifecycle). This means resource classes are not singletons — the JAX-RS runtime instantiates and discards them on every call.

This has a critical implication for state management: if data were stored as instance fields inside a resource class, each request would start with an empty map, losing all previously created data. To solve this, this implementation uses a singleton DataStore class — a separate object holding all ConcurrentHashMap instances that persists across the application lifetime. All resource classes call DataStore.getInstance() to access shared state.

Additionally, ConcurrentHashMap is used instead of HashMap to prevent race conditions such as lost updates or ConcurrentModificationException during simultaneous reads and writes.

---

### Part 1.2 — HATEOAS

HATEOAS (Hypermedia as the Engine of Application State) means API responses include navigational links to related resources, allowing clients to discover functionality at runtime rather than relying on hardcoded URLs from external documentation.

Benefit for client developers: A client hitting /api/v1/discovery immediately receives links like "rooms": "/api/v1/rooms". If the API URL structure changes in a future version, clients following hypermedia links adapt automatically. This reduces coupling between client and server, making the API more evolvable and self-documenting.

---

### Part 2.1 — IDs vs Full Objects in List Responses

Returning only IDs reduces payload size dramatically when there are thousands of rooms but forces the client to make a second request per room to get details, increasing round trips and latency. Returning full objects increases payload size but reduces the number of HTTP calls. For most real-world use cases with moderate data volumes, returning full objects is the better default unless pagination and field projection are implemented.

---

### Part 2.2 — DELETE Idempotency

Yes, the DELETE operation is idempotent in this implementation. The first DELETE /rooms/{roomId} removes the room and returns 204 No Content. Any subsequent identical DELETE requests find that the room no longer exists and also return 204 No Content. The server state is identical after the first and any repeat calls. This is correct RESTful behaviour — idempotency guarantees that network retries do not cause unintended side effects.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

The @Consumes(MediaType.APPLICATION_JSON) annotation declares that the endpoint only accepts requests with Content-Type: application/json. If a client sends data as text/plain or application/xml, JAX-RS automatically returns HTTP 415 Unsupported Media Type without invoking the resource method at all. The runtime message body reader chain cannot find a reader capable of deserialising the incoming format into the target Java POJO, so the request is rejected at the framework level before the developer's business logic is reached.

---

### Part 3.2 — @QueryParam vs Path Segment for Filtering

Using @QueryParam (e.g. GET /sensors?type=CO2) is semantically correct for filtering because the base resource being addressed is still /sensors. The query parameter narrows the result set without changing the resource identity. Using a path segment (/sensors/type/CO2) implies type/CO2 is a distinct sub-resource, not a filter. Query parameters also compose naturally for multiple filters: ?type=CO2&status=ACTIVE. They align with RFC 3986's definition of queries as non-hierarchical refinements, making the API intent immediately clear.

---

### Part 4.1 — Sub-Resource Locator Pattern

The sub-resource locator pattern delegates handling of a URL subtree to a separate class. In this API, SensorResource returns a SensorReadingResource instance for paths matching /{sensorId}/readings.

Architectural benefit: It enforces separation of concerns. Each resource class has a single responsibility — SensorResource manages sensors, SensorReadingResource manages reading history. Without this pattern, a monolithic resource class would handle every nested path, growing to hundreds of lines and becoming difficult to maintain and test. In large APIs with deep nesting, sub-resource locators allow each layer to be developed and tested independently.

---

### Part 5.2 — HTTP 422 vs 404 for Missing Referenced Resource

A 404 Not Found means the requested URL resource does not exist. A 422 Unprocessable Entity means the request URL and payload are syntactically valid but the semantic content of the payload is invalid — the roomId field references a room that does not exist. The client is not requesting the wrong URL; they are submitting a logically inconsistent payload. 422 communicates precisely that the entity cannot be processed as submitted, guiding the client to fix their payload rather than their URL.

---

### Part 5.4 — Security Risk of Exposing Stack Traces

Exposing Java stack traces is a significant security risk because:
1. Technology fingerprinting: Stack traces reveal the framework and library versions, enabling targeted attacks against known CVEs.
2. Internal architecture disclosure: Package names and class hierarchies reveal system design, reducing attacker reconnaissance effort.
3. Path traversal vectors: File paths in traces expose server directory structures.
4. Error amplification: Knowing exactly which line of code failed allows an attacker to craft repeated inputs that deliberately trigger exceptions.

The GlobalExceptionMapper prevents this by catching every Throwable and returning only a generic 500 message.

---

### Part 5.5 — Filters for Cross-Cutting Concerns

Inserting Logger.info() directly inside every resource method violates the DRY principle. With 15+ endpoints, that means 15+ places to maintain. A JAX-RS ContainerRequestFilter and ContainerResponseFilter implements logging once, in one class, and executes automatically for every request and response. This is the cross-cutting concern pattern — logging, authentication, CORS headers, and rate limiting are orthogonal to business logic. Filters enforce these system-wide policies consistently and allow them to be modified in one location.
