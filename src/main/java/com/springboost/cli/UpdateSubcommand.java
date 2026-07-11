package com.springboost.cli;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * `spring-boost update` - refreshes already-published AI guidelines/skills so
 * they reflect the version bundled in this jar, mirroring
 * `php artisan boost:update` from Laravel Boost. `--discover` also publishes
 * any bundled guideline/skill not yet present in the project.
 */
@CommandLine.Command(
    name = "update",
    description = "Refresh already-published AI guidelines/skills"
)
public class UpdateSubcommand implements Callable<Integer> {

    private final GuidelinesPublisher publisher;

    @CommandLine.Option(names = "--discover", description = "Also publish guidelines/skills not yet present in this project")
    private boolean discover;

    public UpdateSubcommand(GuidelinesPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public Integer call() throws Exception {
        Path targetDir = Path.of(System.getProperty("user.dir"));
        var mode = discover ? GuidelinesPublisher.Mode.UPDATE_DISCOVER : GuidelinesPublisher.Mode.UPDATE;
        var result = publisher.publish(targetDir, mode, false, true);

        System.out.printf("Updated %d guideline/skill file(s) in %s/.ai%n",
                result.written().size(), targetDir);
        if (!discover && !result.skipped().isEmpty()) {
            System.out.printf("%d file(s) not yet published were skipped (rerun with --discover to add them)%n",
                    result.skipped().size());
        }

        return 0;
    }
}
