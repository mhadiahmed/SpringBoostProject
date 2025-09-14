# Spring Messaging Integration Guidelines

## Overview

Comprehensive guidelines for integrating Spring Boot applications with messaging systems including RabbitMQ, Apache Kafka, AWS SQS, and Redis Pub/Sub. Covers asynchronous processing, event-driven architecture, and reliable message delivery.

## RabbitMQ Integration

### Configuration and Setup

```yaml
# application.yml - RabbitMQ Configuration
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: ${RABBITMQ_VHOST:/}
    
    # Connection configuration
    connection-timeout: ${RABBITMQ_CONNECTION_TIMEOUT:30000}
    requested-heartbeat: ${RABBITMQ_HEARTBEAT:60}
    
    # Publisher configuration
    publisher:
      confirm-type: correlated
      returns: true
      
    # Consumer configuration
    listener:
      simple:
        concurrency: ${RABBITMQ_CONCURRENCY:5}
        max-concurrency: ${RABBITMQ_MAX_CONCURRENCY:10}
        prefetch: ${RABBITMQ_PREFETCH:10}
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
          max-interval: 10000
          multiplier: 2
        default-requeue-rejected: false
        acknowledge-mode: manual
        
    # Template configuration
    template:
      mandatory: true
      receive-timeout: 5000
      reply-timeout: 10000
      retry:
        enabled: true
        initial-interval: 1000
        max-attempts: 3
        
  # SSL configuration (if needed)
    ssl:
      enabled: ${RABBITMQ_SSL_ENABLED:false}
      key-store: ${RABBITMQ_SSL_KEYSTORE:}
      key-store-password: ${RABBITMQ_SSL_KEYSTORE_PASSWORD:}
      trust-store: ${RABBITMQ_SSL_TRUSTSTORE:}
      trust-store-password: ${RABBITMQ_SSL_TRUSTSTORE_PASSWORD:}
```

### RabbitMQ Configuration Classes

```java
@Configuration
@EnableRabbit
public class RabbitMQConfig {
    
    // Exchange definitions
    @Bean
    public TopicExchange userExchange() {
        return ExchangeBuilder
                .topicExchange("user.exchange")
                .durable(true)
                .build();
    }
    
    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder
                .directExchange("dlx.exchange")
                .durable(true)
                .build();
    }
    
    // Queue definitions
    @Bean
    public Queue userCreatedQueue() {
        return QueueBuilder
                .durable("user.created.queue")
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "user.created.dlq")
                .withArgument("x-message-ttl", 300000) // 5 minutes
                .build();
    }
    
    @Bean
    public Queue userUpdatedQueue() {
        return QueueBuilder
                .durable("user.updated.queue")
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "user.updated.dlq")
                .build();
    }
    
    @Bean
    public Queue userCreatedDLQ() {
        return QueueBuilder
                .durable("user.created.dlq")
                .build();
    }
    
    // Bindings
    @Bean
    public Binding userCreatedBinding() {
        return BindingBuilder
                .bind(userCreatedQueue())
                .to(userExchange())
                .with("user.created");
    }
    
    @Bean
    public Binding userUpdatedBinding() {
        return BindingBuilder
                .bind(userUpdatedQueue())
                .to(userExchange())
                .with("user.updated");
    }
    
    @Bean
    public Binding userCreatedDLQBinding() {
        return BindingBuilder
                .bind(userCreatedDLQ())
                .to(dlxExchange())
                .with("user.created.dlq");
    }
    
    // RabbitTemplate configuration
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Message not delivered to exchange: {}", cause);
            }
        });
        template.setReturnsCallback(returnedMessage -> {
            log.error("Message returned: {}", returnedMessage);
        });
        return template;
    }
    
    // Listener container factory
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(10);
        
        // Error handler
        factory.setErrorHandler(new ConditionalRejectingErrorHandler(
                new ConditionalRejectingErrorHandler.DefaultExceptionStrategy()));
        
        return factory;
    }
}
```

### Message Producer

```java
@Component
public class UserEventProducer {
    
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;
    
    public UserEventProducer(RabbitTemplate rabbitTemplate, MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistry = meterRegistry;
    }
    
    public void publishUserCreated(UserCreatedEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            
            Message message = createMessage(event, correlationData.getId());
            
            rabbitTemplate.convertAndSend(
                    "user.exchange",
                    "user.created",
                    message,
                    correlationData
            );
            
            meterRegistry.counter("rabbitmq.message.sent", "event", "user.created").increment();
            log.info("User created event published: {}", event.getUserId());
            
        } catch (Exception e) {
            meterRegistry.counter("rabbitmq.message.send.error", "event", "user.created").increment();
            log.error("Failed to publish user created event", e);
            throw new MessagePublishingException("Failed to publish user created event", e);
        }
    }
    
    public void publishUserUpdated(UserUpdatedEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            
            MessageProperties properties = new MessageProperties();
            properties.setCorrelationId(correlationData.getId());
            properties.setTimestamp(new Date());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType("application/json");
            properties.setHeader("eventType", "USER_UPDATED");
            properties.setHeader("version", "1.0");
            
            Message message = new Message(convertToBytes(event), properties);
            
            rabbitTemplate.send(
                    "user.exchange",
                    "user.updated",
                    message,
                    correlationData
            );
            
            meterRegistry.counter("rabbitmq.message.sent", "event", "user.updated").increment();
            
        } catch (Exception e) {
            meterRegistry.counter("rabbitmq.message.send.error", "event", "user.updated").increment();
            throw new MessagePublishingException("Failed to publish user updated event", e);
        }
    }
    
    private Message createMessage(Object event, String correlationId) {
        MessageProperties properties = new MessageProperties();
        properties.setCorrelationId(correlationId);
        properties.setTimestamp(new Date());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setContentType("application/json");
        properties.setHeader("eventType", event.getClass().getSimpleName());
        properties.setHeader("version", "1.0");
        properties.setHeader("source", "spring-boost");
        
        return new Message(convertToBytes(event), properties);
    }
    
    private byte[] convertToBytes(Object event) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new MessageSerializationException("Failed to serialize event", e);
        }
    }
}
```

### Message Consumer

```java
@Component
public class UserEventConsumer {
    
    private final UserService userService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;
    
    public UserEventConsumer(UserService userService, 
                            NotificationService notificationService,
                            MeterRegistry meterRegistry) {
        this.userService = userService;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }
    
    @RabbitListener(queues = "user.created.queue")
    public void handleUserCreated(
            @Payload UserCreatedEvent event,
            @Header Map<String, Object> headers,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Processing user created event: {}", event);
            
            // Process the event
            processUserCreatedEvent(event);
            
            // Manual acknowledgment
            channel.basicAck(deliveryTag, false);
            
            sample.stop(Timer.builder("rabbitmq.message.processing.duration")
                    .tag("event", "user.created")
                    .tag("status", "success")
                    .register(meterRegistry));
            
            meterRegistry.counter("rabbitmq.message.processed", 
                    "event", "user.created", "status", "success").increment();
            
        } catch (Exception e) {
            log.error("Failed to process user created event: {}", event, e);
            
            try {
                // Reject and requeue or send to DLQ based on retry count
                if (shouldRequeue(headers)) {
                    channel.basicNack(deliveryTag, false, true);
                } else {
                    channel.basicNack(deliveryTag, false, false);
                }
            } catch (IOException ioException) {
                log.error("Failed to nack message", ioException);
            }
            
            sample.stop(Timer.builder("rabbitmq.message.processing.duration")
                    .tag("event", "user.created")
                    .tag("status", "error")
                    .register(meterRegistry));
            
            meterRegistry.counter("rabbitmq.message.processed", 
                    "event", "user.created", "status", "error").increment();
        }
    }
    
    @RabbitListener(queues = "user.updated.queue")
    public void handleUserUpdated(UserUpdatedEvent event, Channel channel, 
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("Processing user updated event: {}", event);
            
            // Idempotent processing
            if (isAlreadyProcessed(event.getEventId())) {
                log.info("Event already processed: {}", event.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }
            
            processUserUpdatedEvent(event);
            markAsProcessed(event.getEventId());
            
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("Failed to process user updated event: {}", event, e);
            
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("Failed to nack message", ioException);
            }
        }
    }
    
    @RabbitListener(queues = "user.created.dlq")
    public void handleUserCreatedDLQ(UserCreatedEvent event) {
        log.error("Processing user created event from DLQ: {}", event);
        
        // Handle failed messages - alert, store for manual processing, etc.
        alertService.sendAlert("DLQ Message", "User created event in DLQ: " + event);
        
        meterRegistry.counter("rabbitmq.message.dlq", "event", "user.created").increment();
    }
    
    private void processUserCreatedEvent(UserCreatedEvent event) {
        // Send welcome email
        notificationService.sendWelcomeEmail(event.getUserId());
        
        // Create user profile
        userService.createDefaultProfile(event.getUserId());
        
        // Track user registration
        analyticsService.trackUserRegistration(event);
    }
    
    private void processUserUpdatedEvent(UserUpdatedEvent event) {
        // Update search index
        searchService.updateUserIndex(event.getUserId());
        
        // Invalidate cache
        cacheService.invalidateUserCache(event.getUserId());
        
        // Send notification if significant changes
        if (event.hasSignificantChanges()) {
            notificationService.sendUpdateNotification(event.getUserId());
        }
    }
    
    private boolean shouldRequeue(Map<String, Object> headers) {
        Integer retryCount = (Integer) headers.get("x-retry-count");
        return retryCount == null || retryCount < 3;
    }
    
    private boolean isAlreadyProcessed(String eventId) {
        return redisTemplate.hasKey("processed:event:" + eventId);
    }
    
    private void markAsProcessed(String eventId) {
        redisTemplate.opsForValue().set("processed:event:" + eventId, "true", Duration.ofHours(24));
    }
}
```

## Apache Kafka Integration

### Configuration and Setup

```yaml
# application.yml - Kafka Configuration
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    
    # Producer configuration
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      batch-size: 16384
      linger-ms: 5
      buffer-memory: 33554432
      compression-type: snappy
      enable-idempotence: true
      max-in-flight-requests-per-connection: 5
      properties:
        max.request.size: 1048576
        
    # Consumer configuration
    consumer:
      group-id: ${KAFKA_CONSUMER_GROUP:spring-boost-group}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      fetch-min-size: 1024
      fetch-max-wait: 500
      max-poll-records: 500
      session-timeout-ms: 30000
      heartbeat-interval-ms: 10000
      properties:
        spring.json.trusted.packages: "com.springboost.event"
        max.poll.interval.ms: 300000
        
    # Admin configuration
    admin:
      properties:
        bootstrap.servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
        
    # SSL configuration (if needed)
    ssl:
      trust-store-location: ${KAFKA_SSL_TRUSTSTORE:}
      trust-store-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD:}
      key-store-location: ${KAFKA_SSL_KEYSTORE:}
      key-store-password: ${KAFKA_SSL_KEYSTORE_PASSWORD:}
      
    # SASL configuration (if needed)
    jaas:
      enabled: ${KAFKA_SASL_ENABLED:false}
      login-module: org.apache.kafka.common.security.plain.PlainLoginModule
      control-flag: required
      options:
        username: ${KAFKA_SASL_USERNAME:}
        password: ${KAFKA_SASL_PASSWORD:}
```

### Kafka Configuration Classes

```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    // Topic definitions
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name("user-events")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000") // 1 day
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy")
                .build();
    }
    
    @Bean
    public NewTopic userNotificationsTopic() {
        return TopicBuilder.name("user-notifications")
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000") // 7 days
                .build();
    }
    
    // Producer factory
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setProducerInterceptors(Collections.singletonList(new ProducerInterceptor<String, Object>() {
            @Override
            public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
                // Add headers or modify record before sending
                return record;
            }
            
            @Override
            public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
                if (exception != null) {
                    log.error("Failed to send message to topic: {}", metadata.topic(), exception);
                }
            }
            
            @Override
            public void close() {}
            
            @Override
            public void configure(Map<String, ?> configs) {}
        }));
        
        return template;
    }
    
    // Consumer factory
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "spring-boost-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.springboost.event");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(3000);
        
        // Error handling
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 3)));
                
        return factory;
    }
    
    // Kafka Streams configuration
    @Bean
    public KafkaStreamsConfiguration kafkaStreamsConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "spring-boost-streams");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        return new KafkaStreamsConfiguration(props);
    }
}
```

### Kafka Producer

```java
@Component
public class KafkaEventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    public KafkaEventProducer(KafkaTemplate<String, Object> kafkaTemplate, 
                             MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }
    
    public CompletableFuture<SendResult<String, Object>> publishUserEvent(UserEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "user-events",
                event.getUserId().toString(),
                event
        );
        
        // Add headers
        record.headers().add("eventType", event.getEventType().getBytes());
        record.headers().add("version", "1.0".getBytes());
        record.headers().add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes());
        record.headers().add("source", "spring-boost".getBytes());
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);
        
        future.whenComplete((result, exception) -> {
            sample.stop(Timer.builder("kafka.message.send.duration")
                    .tag("topic", "user-events")
                    .tag("status", exception == null ? "success" : "error")
                    .register(meterRegistry));
            
            if (exception == null) {
                meterRegistry.counter("kafka.message.sent", "topic", "user-events").increment();
                log.info("User event sent successfully: partition={}, offset={}", 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                meterRegistry.counter("kafka.message.send.error", "topic", "user-events").increment();
                log.error("Failed to send user event", exception);
            }
        });
        
        return future;
    }
    
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void publishUserNotification(UserNotification notification) {
        try {
            String key = notification.getUserId().toString();
            
            ListenableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send("user-notifications", key, notification);
            
            future.addCallback(
                    result -> {
                        log.info("Notification sent: partition={}, offset={}", 
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        meterRegistry.counter("kafka.notification.sent").increment();
                    },
                    failure -> {
                        log.error("Failed to send notification", failure);
                        meterRegistry.counter("kafka.notification.send.error").increment();
                    }
            );
            
        } catch (Exception e) {
            log.error("Error publishing notification", e);
            throw e;
        }
    }
    
    // Transactional producer
    @Transactional("kafkaTransactionManager")
    public void publishEventsInTransaction(List<UserEvent> events) {
        events.forEach(event -> {
            kafkaTemplate.send("user-events", event.getUserId().toString(), event);
        });
    }
}
```

### Kafka Consumer

```java
@Component
public class KafkaEventConsumer {
    
    private final UserService userService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;
    
    public KafkaEventConsumer(UserService userService, 
                             NotificationService notificationService,
                             MeterRegistry meterRegistry) {
        this.userService = userService;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }
    
    @KafkaListener(topics = "user-events", groupId = "user-processor-group")
    public void handleUserEvent(
            @Payload UserEvent event,
            @Header Map<String, Object> headers,
            ConsumerRecord<String, UserEvent> record,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Processing user event: type={}, userId={}, partition={}, offset={}", 
                    event.getEventType(), event.getUserId(), 
                    record.partition(), record.offset());
            
            // Idempotency check
            String eventId = generateEventId(record);
            if (isEventProcessed(eventId)) {
                log.info("Event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process based on event type
            switch (event.getEventType()) {
                case USER_CREATED:
                    handleUserCreated((UserCreatedEvent) event);
                    break;
                case USER_UPDATED:
                    handleUserUpdated((UserUpdatedEvent) event);
                    break;
                case USER_DELETED:
                    handleUserDeleted((UserDeletedEvent) event);
                    break;
                default:
                    log.warn("Unknown event type: {}", event.getEventType());
            }
            
            // Mark as processed
            markEventAsProcessed(eventId);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            sample.stop(Timer.builder("kafka.message.processing.duration")
                    .tag("topic", "user-events")
                    .tag("status", "success")
                    .register(meterRegistry));
            
            meterRegistry.counter("kafka.message.processed", 
                    "topic", "user-events", "status", "success").increment();
            
        } catch (Exception e) {
            log.error("Failed to process user event: {}", event, e);
            
            sample.stop(Timer.builder("kafka.message.processing.duration")
                    .tag("topic", "user-events")
                    .tag("status", "error")
                    .register(meterRegistry));
            
            meterRegistry.counter("kafka.message.processed", 
                    "topic", "user-events", "status", "error").increment();
            
            // Don't acknowledge on error - message will be redelivered
            throw e;
        }
    }
    
    @KafkaListener(topics = "user-notifications", 
                   groupId = "notification-processor-group",
                   concurrency = "3")
    public void handleUserNotification(UserNotification notification, 
                                      ConsumerRecord<String, UserNotification> record,
                                      Acknowledgment acknowledgment) {
        try {
            log.info("Processing notification: type={}, userId={}", 
                    notification.getType(), notification.getUserId());
            
            notificationService.processNotification(notification);
            acknowledgment.acknowledge();
            
            meterRegistry.counter("kafka.notification.processed").increment();
            
        } catch (Exception e) {
            log.error("Failed to process notification: {}", notification, e);
            meterRegistry.counter("kafka.notification.processing.error").increment();
            throw e;
        }
    }
    
    // Batch processing
    @KafkaListener(topics = "user-analytics", 
                   groupId = "analytics-batch-group",
                   containerFactory = "batchKafkaListenerContainerFactory")
    public void handleUserAnalyticsBatch(
            List<ConsumerRecord<String, UserAnalyticsEvent>> records,
            Acknowledgment acknowledgment) {
        
        log.info("Processing batch of {} analytics events", records.size());
        
        try {
            List<UserAnalyticsEvent> events = records.stream()
                    .map(ConsumerRecord::value)
                    .collect(Collectors.toList());
            
            analyticsService.processBatch(events);
            acknowledgment.acknowledge();
            
            meterRegistry.counter("kafka.analytics.batch.processed", 
                    "size", String.valueOf(events.size())).increment();
            
        } catch (Exception e) {
            log.error("Failed to process analytics batch", e);
            meterRegistry.counter("kafka.analytics.batch.error").increment();
            throw e;
        }
    }
    
    private void handleUserCreated(UserCreatedEvent event) {
        // Send welcome email
        notificationService.sendWelcomeEmail(event.getUserId());
        
        // Initialize user profile
        userService.initializeProfile(event.getUserId());
        
        // Track registration
        analyticsService.trackRegistration(event);
    }
    
    private void handleUserUpdated(UserUpdatedEvent event) {
        // Update search index
        searchService.updateUserIndex(event.getUserId());
        
        // Invalidate cache
        cacheService.evictUser(event.getUserId());
    }
    
    private void handleUserDeleted(UserDeletedEvent event) {
        // Clean up user data
        userService.cleanupUserData(event.getUserId());
        
        // Remove from search index
        searchService.removeUserFromIndex(event.getUserId());
    }
    
    private String generateEventId(ConsumerRecord<?, ?> record) {
        return String.format("%s-%d-%d", record.topic(), record.partition(), record.offset());
    }
    
    private boolean isEventProcessed(String eventId) {
        return redisTemplate.hasKey("processed:kafka:" + eventId);
    }
    
    private void markEventAsProcessed(String eventId) {
        redisTemplate.opsForValue().set("processed:kafka:" + eventId, "true", Duration.ofDays(7));
    }
}
```

## AWS SQS Integration

### Configuration

```yaml
# application.yml - AWS SQS Configuration
cloud:
  aws:
    region:
      static: ${AWS_REGION:us-east-1}
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID:}
      secret-key: ${AWS_SECRET_ACCESS_KEY:}
    sqs:
      endpoint: ${AWS_SQS_ENDPOINT:}
    stack:
      auto: false

# SQS specific configuration
sqs:
  queues:
    user-events: ${SQS_USER_EVENTS_QUEUE:user-events}
    user-notifications: ${SQS_USER_NOTIFICATIONS_QUEUE:user-notifications}
    dead-letter-queue: ${SQS_DLQ:dead-letter-queue}
  max-number-of-messages: 10
  wait-time-seconds: 20
  visibility-timeout-seconds: 30
```

### SQS Configuration Class

```java
@Configuration
@EnableSqs
public class SQSConfig {
    
    @Value("${cloud.aws.region.static}")
    private String region;
    
    @Bean
    public AmazonSQS amazonSQS() {
        return AmazonSQSClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
    }
    
    @Bean
    public QueueMessagingTemplate queueMessagingTemplate(AmazonSQS amazonSQS) {
        return new QueueMessagingTemplate(amazonSQS);
    }
    
    @Bean
    @Primary
    public SimpleMessageListenerContainerFactory simpleMessageListenerContainerFactory(AmazonSQS amazonSQS) {
        SimpleMessageListenerContainerFactory factory = new SimpleMessageListenerContainerFactory();
        factory.setAmazonSqs(amazonSQS);
        factory.setMaxNumberOfMessages(10);
        factory.setWaitTimeOut(20);
        factory.setVisibilityTimeout(30);
        return factory;
    }
}
```

### SQS Producer and Consumer

```java
@Component
public class SQSEventProducer {
    
    private final QueueMessagingTemplate queueMessagingTemplate;
    private final MeterRegistry meterRegistry;
    
    @Value("${sqs.queues.user-events}")
    private String userEventsQueue;
    
    public SQSEventProducer(QueueMessagingTemplate queueMessagingTemplate, 
                           MeterRegistry meterRegistry) {
        this.queueMessagingTemplate = queueMessagingTemplate;
        this.meterRegistry = meterRegistry;
    }
    
    public void sendUserEvent(UserEvent event) {
        try {
            MessageHeaders headers = new MessageHeaders(Map.of(
                    "eventType", event.getEventType().toString(),
                    "timestamp", System.currentTimeMillis(),
                    "source", "spring-boost"
            ));
            
            Message<UserEvent> message = new GenericMessage<>(event, headers);
            
            queueMessagingTemplate.send(userEventsQueue, message);
            
            meterRegistry.counter("sqs.message.sent", "queue", "user-events").increment();
            log.info("User event sent to SQS: {}", event);
            
        } catch (Exception e) {
            meterRegistry.counter("sqs.message.send.error", "queue", "user-events").increment();
            log.error("Failed to send user event to SQS", e);
            throw e;
        }
    }
}

@Component
public class SQSEventConsumer {
    
    private final UserService userService;
    private final MeterRegistry meterRegistry;
    
    public SQSEventConsumer(UserService userService, MeterRegistry meterRegistry) {
        this.userService = userService;
        this.meterRegistry = meterRegistry;
    }
    
    @SqsListener(value = "${sqs.queues.user-events}", deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
    public void handleUserEvent(@Payload UserEvent event, 
                               @Header Map<String, Object> headers) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Processing SQS user event: {}", event);
            
            // Process the event
            processUserEvent(event);
            
            sample.stop(Timer.builder("sqs.message.processing.duration")
                    .tag("queue", "user-events")
                    .tag("status", "success")
                    .register(meterRegistry));
            
            meterRegistry.counter("sqs.message.processed", 
                    "queue", "user-events", "status", "success").increment();
            
        } catch (Exception e) {
            sample.stop(Timer.builder("sqs.message.processing.duration")
                    .tag("queue", "user-events")
                    .tag("status", "error")
                    .register(meterRegistry));
            
            meterRegistry.counter("sqs.message.processed", 
                    "queue", "user-events", "status", "error").increment();
            
            log.error("Failed to process SQS user event", e);
            throw e;
        }
    }
    
    private void processUserEvent(UserEvent event) {
        switch (event.getEventType()) {
            case USER_CREATED:
                userService.handleUserCreated((UserCreatedEvent) event);
                break;
            case USER_UPDATED:
                userService.handleUserUpdated((UserUpdatedEvent) event);
                break;
            default:
                log.warn("Unknown event type: {}", event.getEventType());
        }
    }
}
```

This comprehensive messaging integration guide provides production-ready patterns for integrating Spring Boot applications with various messaging systems, ensuring reliable, scalable, and observable message processing.
