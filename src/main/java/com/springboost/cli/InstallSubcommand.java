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

    @CommandLine.Option(names = "--all", description = "Publish all guidelines regardless of detected project dependencies")
    private boolean all;

    public InstallSubcommand(GuidelinesPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public Integer call() throws Exception {
        Path targetDir = Path.of(System.getProperty("user.dir"));

        if (!all) {
            var detected = GuidelinesPublisher.detectRelevantCategories(targetDir);
            if (detected != null) {
                System.out.printf("Detected project dependencies — publishing relevant guidelines only (use --all for everything)%n");
            }
        }

        var result = publisher.publish(targetDir, GuidelinesPublisher.Mode.INSTALL, force, all);

        System.out.printf("Published %d guideline/skill file(s) to %s/.ai%n",
                result.written().size(), targetDir);
        if (!result.skipped().isEmpty()) {
            System.out.printf("Skipped %d file(s) (not relevant to this project, or already present; use --force/--all to override)%n",
                    result.skipped().size());
        }

        EditorSetup.printInstructions();
        return 0;
    }
}
