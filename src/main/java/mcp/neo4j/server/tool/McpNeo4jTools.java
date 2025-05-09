package mcp.neo4j.server.tool;

import mcp.neo4j.server.service.Neo4jService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

/**
 * @author dsimile
 * @date 2025-5-7 13:30
 * @description
 */
@Service
public class McpNeo4jTools {

    @Bean
    public ToolCallbackProvider neo4jTools(Neo4jService neo4jService) {
        return MethodToolCallbackProvider.builder().toolObjects(neo4jService).build();
    }
}
