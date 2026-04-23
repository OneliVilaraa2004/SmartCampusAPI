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

The default JAX-RS configuration is a single instance of a resource class with each incoming HTTP request (per-request lifecycle). This implies that resource classes are not singletons - the JAX-RS runtime creates and destroys them each time a call is made. A severe consequence of this on state management is that should data be represented as instance fields in a resource class, every request would begin with an empty map, destroying all data previously generated. To address this, the current implementation is based on a singleton DataStore instance - an independent object storing all ConcurrentHashMap instances that will exist throughout the application life. DataStore is called by all resource classes.to access shared state, getinstance. Also, ConcurrentHashMap is employed in place of HashMap to eliminate race conditions like lost updates or ConcurrentModificationException when multiple readers and writers are accessing the same map.

---

### Part 1.2 — HATEOAS

HATEOAS (Hypermedia as the Engine of Application State) implies that API responses contain links to related resources to enable clients to learn about the functionality at runtime, as opposed to following hardcoded URLs in external documentation. Benefit to client developers: A client that visits the discovery endpoint is immediately given links to rooms and sensors resources. In the event of a change in the API URL structure in a future release, codeless adaptation to hypermedia links is made. This limits the interconnection between client and server and the API is more evolvable and self-documenting than when using static documentation.

---

### Part 2.1 — IDs vs Full Objects in List Responses

Sending IDs only lowers payload size by thousands of rooms, but causes the client to redo a request per room to retrieve details, doubling round trips and latency. Sending entire objects decreases the number of HTTP calls but increases the payload size. In the vast majority of practical applications with moderate data volumes, the default is better to use full objects unless the use of pagination and field projection are provided.

---

### Part 2.2 — DELETE Idempotency

Yes, the DELETE operation in this implementation is idempotent. The initial DELETE /rooms/{roomId} deletes the room and responds with 204 No Content. Any further identical DELETEs discover that the room is not in existence anymore and also send 204 No Content. After the initial and repeat calls, the server state remains the same. And this is proper RESTful behavior idempotency ensures that side effects of network retries are unintentional.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

The @Consumes(MediaType.@APPLICATION_JSON) annotation states that the endpoint will accept only requests that have Content-type: application/json. When a client submits data in text /plain or application/xml, JAX-RS responds to it with HTTP 415 Unsupported Media Type without even making a call to the resource method. This is because the runtime message body reader chain is unable to locate a deserialiser which can be used to deserialise the incoming format to the target Java POJO, and thus the request is rejected at the framework level before the business logic of the developer is hit.

---

### Part 3.2 — @QueryParam vs Path Segment for Filtering

Filtering with the help of the semantically correct use of @QueryParam (e.g. GET /sensors?type=CO2) is that the resource at the end of the query remains /sensors. The query parameter limits the result set without modifying the identity of the resource. The path segment (/sensors/type/CO2) suggests that type/CO2 is a sub-resource, rather than a filter. Naturally, query parameters can be compounded with several filters: ?type=CO2&status=ACTIVE. They are consistent with RFC 3986 definition of queries as non-hierarchical refinements and the meaning of the API is immediately obvious.

---

### Part 4.1 — Sub-Resource Locator Pattern

The sub-resource locator pattern transfers processing a URL sub-tree to another class. SensorResource returns an instance of SensorReadingResource to paths /{ sensorid }/ readings in this API. Architectural advantage: It imposes isolation of concerns. The resources are single-purpose: SensorResource is in charge of sensors, SensorReadingResource is in charge of reading history. In its absence, a monolithic, resource-class would process all nested paths, up to hundreds of lines, making it hard to maintain and test. With deep nesting and large APIs, sub-resource locators enable the development and testing of each layer individually.

---

### Part 5.2 — HTTP 422 vs 404 for Missing Referenced Resource

A 404 Not Found is an indication that the resource requested by the URL is not present. A 422 Unprocessable Entity indicates that the request URL and body are syntactically sound, but the content of the body is incorrectly semantically defined - the roomId field refers to a room which does not exist. The client is not providing the incorrect URL; they are giving a logically invalid payload. 422 is conveying the exact meaning that the entity cannot be processed as submitted, and to instruct the client to repair their payload, and not their URL.

---

### Part 5.4 — Security Risk of Exposing Stack Traces

Revealing Java stack traces is a serious security threat since: 
1. Technology fingerprinting: Stack traces indicate the framework and library versions, allowing attacks on known CVEs. 
2. Disclosure of internal architecture: Both package names and class hierarchies reveal system design and minimize attacker reconnaissance. 
3. Path traversal vectors: Path traces of files reveal server directory layouts. 
4. Error amplification: Understanding the line of code that failed will enable an attacker to make repeated inputs to induce exceptions intentionally. The GlobalExceptionMapper avoids this by intercepting all Throwables, and responding with a generic 500 message with no internal information.


---

### Part 5.5 — Filters for Cross-Cutting Concerns

Placing Logger.info() right in between resource methods contravenes the DRY principle. There are numerous endpoints, and that implies numerous places to maintain. A JAX-RS ContainerRequestFilter and ContainerResponseFilter performs logging once, in a single class, and automatically performs on each request and response. This is a pattern of cross-cutting concern - logging, authentication, CORS headers, and rate limiting are independent of business logic. These system-wide policies are enforced uniformly by filters, and can be changed in a single place.
