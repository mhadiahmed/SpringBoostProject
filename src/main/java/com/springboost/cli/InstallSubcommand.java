package com.springboost.cli;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * `spring-boost install` - publishes AI guidelines/skills into the current
 * project and prints MCP registration instructions, mirroring
 * `php artisan boost:install` from Laravel Boost.
 */
@CommandLine.Command(
    name = "install",
    description = "Publish AI guidelines/skills into this project and print editor setup instructions"
)
public class InstallSubcommand implements Callable<Integer> {

    private final GuidelinesPublisher publisher;

    @CommandLine.Option(names = "--force", description = "Overwrite guideline/skill files already published in this project")
    private boolean force;

    public InstallSubcommand(GuidelinesPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public Integer call() throws Exception {
        Path targetDir = Path.of(System.getProperty("user.dir"));
        var result = publisher.publish(targetDir, GuidelinesPublisher.Mode.INSTALL, force);

        System.out.printf("Published %d guideline/skill file(s) to %s/.ai%n",
                result.written().size(), targetDir);
        if (!result.skipped().isEmpty()) {
            System.out.printf("Skipped %d already-present file(s) (use --force to overwrite)%n",
                    result.skipped().size());
        }

        EditorSetup.printInstructions();
        return 0;
    }
}
