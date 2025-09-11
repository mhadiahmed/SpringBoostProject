package com.springboost;

import com.springboost.mcp.tools.McpToolRegistry;
import com.springboost.mcp.tools.impl.ApplicationInfoTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SpringBoostApplicationTests {

    @Autowired
    private McpToolRegistry toolRegistry;

    @Test
    void contextLoads() {
        // Verify that the Spring context loads successfully
        assertThat(toolRegistry).isNotNull();
    }

    @Test
    void applicationInfoToolIsRegistered() {
        // Verify that our ApplicationInfoTool is registered
        var tool = toolRegistry.getTool("application-info");
        assertThat(tool).isPresent();
        assertThat(tool.get()).isInstanceOf(ApplicationInfoTool.class);
    }

    @Test
    void applicationInfoToolCanExecute() throws Exception {
        // Test basic execution of the ApplicationInfoTool
        var tool = toolRegistry.getTool("application-info");
        assertThat(tool).isPresent();
        
        var result = tool.get().execute(java.util.Map.of());
        assertThat(result).isNotNull();
    }
}
