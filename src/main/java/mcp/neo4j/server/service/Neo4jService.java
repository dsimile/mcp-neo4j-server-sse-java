package mcp.neo4j.server.service;

import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.neo4j.driver.types.MapAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author neo4j-contrib and dsimile
 * @date 2025-5-7 10:56
 * @description
 */
@Service
public class Neo4jService implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jService.class);
    private final Driver driver;
    private final String databaseName;
    private static final String SCHEMA = """
            call apoc.meta.data() yield label, property, type, other, unique, index, elementType
            where elementType = 'node' and not label starts with '_'
            with label,\040
                collect(case when type <> 'RELATIONSHIP' then [property, type + case when unique then " unique" else "" end + case when index then " indexed" else "" end] end) as attributes,
                collect(case when type = 'RELATIONSHIP' then [property, head(other)] end) as relationships
            RETURN label, apoc.map.fromPairs(attributes) as attributes, apoc.map.fromPairs(relationships) as relationships
                                """;

    // Checks if a Cypher query contains common write clauses.
    private static final Pattern WRITE_QUERY_PATTERN = Pattern.compile(
            "\\b(MERGE|CREATE|SET|DELETE|REMOVE|ADD)\\b", Pattern.CASE_INSENSITIVE
    );

    /**
     * Initialize connection to the neo4j database.
     *
     * @param uri          Neo4j connection URI (e.g., "neo4j://localhost:7687")
     * @param username     Database username
     * @param password     Database password
     * @param databaseName Database name (e.g., "neo4j")
     */
    public Neo4jService(
            @Value("${neo4j.uri}") String uri,
            @Value("${neo4j.username}") String username,
            @Value("${neo4j.password}") String password,
            @Value("${neo4j.database:neo4j}") String databaseName) {
        logger.debug("Initializing database connection to {} for database {}", uri, databaseName);
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), config());
        try {
            driver.verifyConnectivity();
            logger.info("Successfully connected to Neo4j at {} and database {}", uri, databaseName);
        } catch (Exception e) {
            logger.error("Failed to verify connectivity to Neo4j: {}", e.getMessage());
            throw new Neo4jException("Neo4j connectivity failed", e);
        }
        this.databaseName = databaseName;
    }

    /**
     * Defines the configuration for the Neo4j driver.
     *
     * @return The Neo4j Config object.
     */
    private Config config() {
        return Config.builder()
                .withConnectionTimeout(300, TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(100)
                .withMaxConnectionLifetime(1, TimeUnit.HOURS)
                .withConnectionAcquisitionTimeout(600, TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(300, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Checks if a Cypher query contains common write clauses.
     *
     * @param query The Cypher query string.
     * @return true if the query contains write clauses, false otherwise.
     */
    public boolean isWriteQuery(String query) {
        return WRITE_QUERY_PATTERN.matcher(query).find();
    }

    /**
     * Execute a Cypher query and return results as a list of dictionaries (Maps).
     * For write queries, returns a list containing a single map of counters.
     * For read queries, returns a list of maps, where each map represents a record.
     *
     * @param query  The Cypher query string.
     * @param params Optional parameters for the query.
     * @return A list of maps representing the query results or write counters.
     * @throws RuntimeException if a database error occurs.
     */
    public List<Map<String, Object>> executeQuery(String query, Map<String, Object> params) {
        logger.info("Executing query: {}", query);
        if (params == null) {
            params = Collections.emptyMap();
        }
        try (Session session = driver.session(SessionConfig.forDatabase(databaseName))) {
            Result result = session.run(query, params);

            // For write queries, return a map representing the counters
            if (isWriteQuery(query)) {
                ResultSummary summary = result.consume(); // Consume the result to get the summary
                SummaryCounters counters = summary.counters();
                Map<String, Object> counterMap = new HashMap<>();
                counterMap.put("nodesCreated", counters.nodesCreated());
                counterMap.put("nodesDeleted", counters.nodesDeleted());
                counterMap.put("relationshipsCreated", counters.relationshipsCreated());
                counterMap.put("relationshipsDeleted", counters.relationshipsDeleted());
                counterMap.put("propertiesSet", counters.propertiesSet());
                counterMap.put("labelsAdded", counters.labelsAdded());
                counterMap.put("labelsRemoved", counters.labelsRemoved());
                counterMap.put("indexesAdded", counters.indexesAdded());
                counterMap.put("indexesRemoved", counters.indexesRemoved());
                counterMap.put("constraintsAdded", counters.constraintsAdded());
                counterMap.put("constraintsRemoved", counters.constraintsRemoved());
                counterMap.put("systemUpdates", counters.systemUpdates());
                counterMap.put("containsSystemUpdates", counters.containsSystemUpdates());
                counterMap.put("containsUpdates", counters.containsUpdates());
                logger.debug("Write query affected: {}", counterMap);
                return List.of(counterMap);
            } else {
                List<Map<String, Object>> records = result.list(MapAccessor::asMap);
                logger.info("Read query returned {} rows", records.size());
                return records;
            }
        } catch (Neo4jException e) {
            logger.error("Database error executing query: {}\nQuery: {}", e.getMessage(), query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Close the Neo4j Driver.
     */
    @Override
    public void close() {
        if (driver != null) {
            try {
                driver.close();
                logger.info("Neo4j driver closed successfully.");
            } catch (Exception e) {
                logger.error("Error closing Neo4j driver: {}", e.getMessage(), e);
            }
        }
    }

    @Tool(name = "get-neo4j-schema", description = "List all node types, their attributes and their relationships TO other node-types in the neo4j database")
    public List<Map<String, Object>> neo4jSchema() {
        return executeQuery(SCHEMA, Collections.emptyMap());
    }

    @Tool(name = "read-neo4j-cypher", description = "Execute a Cypher query on the neo4j database")
    public List<Map<String, Object>> neo4jRead(@ToolParam(description = "Cypher read query to execute") String query) {
        if (isWriteQuery(query)) {
            throw new IllegalArgumentException("Only MATCH queries are allowed for read-query");
        }
        return executeQuery(query, Collections.emptyMap());
    }

    @Tool(name = "write-neo4j-cypher", description = "Execute a write Cypher query on the neo4j database")
    public List<Map<String, Object>> neo4jWrite(@ToolParam(description = "Cypher write query to execute") String query) {
        if (!isWriteQuery(query)) {
            throw new IllegalArgumentException("Only write queries are allowed for write-query");
        }
        return executeQuery(query, Collections.emptyMap());
    }

}