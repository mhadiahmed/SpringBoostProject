package com.springboost.cli;

/**
 * Prints MCP registration snippets for the AI editors Laravel Boost documents
 * support for (stdio transport, one process per editor session).
 */
final class EditorSetup {

    private EditorSetup() {
    }

    static void printInstructions() {
        System.out.println();
        System.out.println("Register the MCP server with your editor (stdio transport):");
        System.out.println();
        System.out.println("  Claude Code:  claude mcp add -s local -t stdio spring-boost -- java -jar spring-boost.jar mcp");
        System.out.println("  Codex:        codex mcp add spring-boost -- java -jar spring-boost.jar mcp");
        System.out.println("  Gemini CLI:   gemini mcp add -s project -t stdio spring-boost java -jar spring-boost.jar mcp");
        System.out.println("  Cursor:       add the JSON below via Settings > MCP");
        System.out.println();
        System.out.println("  {");
        System.out.println("    \"mcpServers\": {");
        System.out.println("      \"spring-boost\": {");
        System.out.println("        \"command\": \"java\",");
        System.out.println("        \"args\": [\"-jar\", \"spring-boost.jar\", \"mcp\"]");
        System.out.println("      }");
        System.out.println("    }");
        System.out.println("  }");
    }
}
