# mcp-neo4j-server-sse-java
mcp-neo4j-server-sse-java is an MCP server implemented by Java using `SSE` (Server-Sent Events) server transport or `STDIO` transport as the transport protocol.

## Overview

A Model Context Protocol (MCP) server implementation that provides database interaction and allows for graph exploration capabilities through Neo4j. This server enables the execution of Cypher graph queries, analysis of complex domain data, and supports the selection of remotely accessible databases. Inspired by [neo4j-contrib/mcp-neo4j](https://github.com/neo4j-contrib/mcp-neo4j/tree/main/servers/mcp-neo4j-cypher).

The project showcases:

- Integration with `spring-ai-mcp-server-webflux-spring-boot-starter`
- Support for both SSE (Server-Sent Events) and STDIO transports
- Automatic tool registration using Spring AI's `@Tool` annotation

### Prompts

The server provides a demonstration prompt:

- `mcp-demo`: Interactive prompt that guides users through database operations
  - Generates appropriate database schemas and sample data

### Tools

The server offers six core tools:

#### Query Tools

- `read-neo4j-cypher`
  - Execute Cypher read queries to read data from the database
  - Input: 
    - `query` (string): The Cypher query to execute
  - Returns: Query results as array of objects

- `write-neo4j-cypher`
  - Execute updating Cypher queries
  - Input:
    - `query` (string): The Cypher update query
  - Returns: a result summary counter with `{ nodes_updated: number, relationships_created: number, ... }`

#### Schema Tools

- `get-neo4j-schema`
  - Get a list of all nodes types in the graph database, their attributes with name, type and relationships to other node types
  - No input required
  - Returns: List of node label with two dictionaries one for attributes and one for relationships

## Usage with Cline client

1.Clone the repository

```cmd
git clone https://github.com/dsimile/mcp-neo4j-server-sse-java.git
```

2.Building the Project

- Java 17+

```cmd
cd mcp-neo4j-server-sse-java

mvn clean install -DskipTests
```

3.Running the Server

- WebFlux SSE Mode (Default)
  - spring.ai.mcp.server.stdio (Default): false
  - Server ip (Default): 0.0.0.0
  - Server port (Default): 8543
  - Neo4j uri (Default): neo4j://localhost:7687
  - Neo4j username (Default): neo4j
  - Neo4j password (Default): neo4j
  - Neo4j database (Default): neo4j

```cmd
java -jar target/mcp-neo4j-server-sse-java-1.0-SNAPSHOT.jar
```

- STDIO Mode

To enable STDIO transport, set the appropriate properties:

```
java -Dspring.ai.mcp.server.stdio=true \
     -Dspring.main.web-application-type=none \
     -Dneo4j.uri=neo4j://localhost:7687 \
     -Dneo4j.username=neo4j \
     -Dneo4j.password=<your password> \
     -Dneo4j.database=<your database> \
     -jar target/mcp-neo4j-server-sse-java-1.0-SNAPSHOT.jar
```

> Note: Please ensure that Neo4j is running and accessible for remote connections.

### Released Package

Add the server to your `cline_mcp_settings.json`.

- WebFlux SSE Mode (Default)

```json
{
  "mcpServers": {
    "neo4j-remote": {
      "url": "http://0.0.0.0:8543/sse",
      "disabled": false,
      "autoApprove": []
    }
  }
}
```

- STDIO Mode

```json
{
  "mcpServers": {
    "neo4j-local": {
      "disabled": false,
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dneo4j.uri=neo4j://localhost:7687",
        "-Dneo4j.username=neo4j",
        "-Dneo4j.password=<your password>",
        "-Dneo4j.database=<your database>",
        "-Dlog.path=/absolute/path/to/",
        "-jar",
        "/absolute/path/to/mcp-neo4j-server-sse-java-1.0-SNAPSHOT.jar"
      ],
      "transportType": "stdio"
    }
  }
}
```

Replace `/absolute/path/to/` with the actual path to your built jar file or log file.

## License

This MCP server is licensed under the MIT License. This means you are free to use, modify, and distribute the software, subject to the terms and conditions of the MIT License. For more details, please see the LICENSE file in the project repository.
