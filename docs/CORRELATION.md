# Correlation id contract

- Canonical header: `X-Correlation-Id`. `X-Request-ID` is accepted as an alternate and echoed by the gateway.
- Valid id: `^[A-Za-z0-9._-]{8,64}$`. Anything else is replaced by a generated id (never reflected).
- MDC key: `correlationId`. Request attribute: `ono.correlationId`.
- Error bodies carry `correlationId` (same value as the response header and the log lines).
- Propagated automatically (when the class is on the classpath): RestTemplate, RestClient, WebClient, `TaskDecorator`,
  RabbitMQ publish/listen, Kafka producer/listener, and (opt in) the PostgreSQL session:
  `ono.observability.correlation.database-session=true` runs `set_config('app.correlation_id', id, false)` per connection checkout.
- Hand-made executors: wrap with `CorrelationPropagation.wrap(...)`.
