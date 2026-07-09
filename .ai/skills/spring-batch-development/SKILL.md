---
name: spring-batch-development
package: Spring Batch
---

# Spring Batch Development

Load when building a batch job: chunked processing, step-based ETL, or a
scheduled bulk operation.

## Checklist

- Chunk-oriented step (`reader`/`processor`/`writer`) for anything reading
  more rows than fit comfortably in memory — don't load a whole table into a
  `Tasklet` and loop in Java.
- Pick a chunk size that bounds a single transaction (100–1000 rows is a
  reasonable default); tune from there based on row size and commit latency.
- `ItemProcessor` returning `null` filters the item out of the chunk — use
  that instead of throwing to skip invalid rows, and reserve
  `faultTolerant().skip(...)` for genuinely exceptional records.
- Make jobs restartable: rely on `JobRepository`'s execution tracking rather
  than hand-rolled "already processed" flags, so a failed job resumes from the
  last successful chunk.

## Minimal job

```java
@Bean
Job importUsersJob(JobRepository jobRepository, Step importUsersStep) {
    return new JobBuilder("importUsersJob", jobRepository)
        .start(importUsersStep)
        .build();
}

@Bean
Step importUsersStep(JobRepository jobRepository, PlatformTransactionManager tx,
                      ItemReader<UserCsvRow> reader, ItemProcessor<UserCsvRow, User> processor,
                      ItemWriter<User> writer) {
    return new StepBuilder("importUsersStep", jobRepository)
        .<UserCsvRow, User>chunk(500, tx)
        .reader(reader).processor(processor).writer(writer)
        .faultTolerant().skipLimit(50).skip(ValidationException.class)
        .build();
}
```
