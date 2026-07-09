---
name: mcp-development
package: Spring Boost / MCP
---

# MCP Tool Development

Load when adding or modifying an MCP tool inside Spring Boost itself (i.e.
working on this repo, not a consumer application).

## Checklist

- Implement `McpTool` (`com.springboost.mcp.tools.McpTool`), register it as a
  `@Component` — `McpToolRegistry` discovers it automatically via
  `ApplicationContext.getBeansOfType`.
- Set `isCore()` deliberately: `true` only if the tool has a direct Laravel
  Boost equivalent (see the 9 tools listed in `docs/tools.md`). Everything
  else is `isCore() = false` so it stays opt-in behind
  `spring-boost.mcp.tools.extensions-enabled`.
- Pick an existing `getCategory()` value (`application`, `database`, `web`,
  `logging`, `monitoring`, `execution`, `documentation`) unless the tool
  genuinely doesn't fit any of them.
- `getParameterSchema()` must be a valid JSON Schema `Map` — this is what gets
  sent verbatim in `tools/list` responses to the AI client.
- Protocol changes (new JSON-RPC methods) go in `McpMessageProcessor`, not in
  `McpServer` (WebSocket-specific) or `McpSubcommand` (stdio-specific) — both
  transports share that processor so logic isn't duplicated per transport.

## Testing

Add a case to `ToolValidationTest` (schema + basic execution) and, for
protocol-level changes, `McpServerIntegrationTest`.
