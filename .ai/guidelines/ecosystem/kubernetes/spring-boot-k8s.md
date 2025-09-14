# Spring Boot Kubernetes Integration Guidelines

## Overview

Guidelines for deploying Spring Boot applications on Kubernetes, including service discovery, configuration management, health checks, observability, and production-ready patterns.

## Basic Kubernetes Manifests

### Deployment Configuration

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-boost-app
  namespace: spring-boost
  labels:
    app: spring-boost
    version: v1.0.0
    component: backend
  annotations:
    deployment.kubernetes.io/revision: "1"
    kubernetes.io/change-cause: "Initial deployment"
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: spring-boost
      component: backend
  template:
    metadata:
      labels:
        app: spring-boost
        component: backend
        version: v1.0.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: spring-boost-sa
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
      containers:
      - name: spring-boost
        image: spring-boost:1.0.0
        imagePullPolicy: Always
        ports:
        - name: http
          containerPort: 8080
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes,prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: spring-boost-db-secret
              key: url
        - name: DATABASE_USERNAME
          valueFrom:
            secretKeyRef:
              name: spring-boost-db-secret
              key: username
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: spring-boost-db-secret
              key: password
        - name: KUBERNETES_NAMESPACE
          valueFrom:
            fieldRef:
              fieldPath: metadata.namespace
        - name: POD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: POD_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
        envFrom:
        - configMapRef:
            name: spring-boost-config
        - secretRef:
            name: spring-boost-secrets
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: http
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: http
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: http
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 5
          failureThreshold: 30
        volumeMounts:
        - name: app-logs
          mountPath: /app/logs
        - name: tmp
          mountPath: /tmp
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
      volumes:
      - name: app-logs
        emptyDir: {}
      - name: tmp
        emptyDir: {}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - spring-boost
              topologyKey: kubernetes.io/hostname
      tolerations:
      - key: "node.kubernetes.io/not-ready"
        operator: "Exists"
        effect: "NoExecute"
        tolerationSeconds: 300
      - key: "node.kubernetes.io/unreachable"
        operator: "Exists"
        effect: "NoExecute"
        tolerationSeconds: 300
```

### Service Configuration

```yaml
# k8s/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: spring-boost-service
  namespace: spring-boost
  labels:
    app: spring-boost
    component: backend
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
spec:
  type: LoadBalancer
  selector:
    app: spring-boost
    component: backend
  ports:
  - name: http
    port: 80
    targetPort: http
    protocol: TCP
  sessionAffinity: None

---
# Internal service for service discovery
apiVersion: v1
kind: Service
metadata:
  name: spring-boost-internal
  namespace: spring-boost
  labels:
    app: spring-boost
    component: backend
spec:
  type: ClusterIP
  selector:
    app: spring-boost
    component: backend
  ports:
  - name: http
    port: 8080
    targetPort: http
    protocol: TCP
```

### ConfigMap Configuration

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-boost-config
  namespace: spring-boost
  labels:
    app: spring-boost
data:
  # Application configuration
  SPRING_APPLICATION_NAME: "spring-boost"
  SERVER_PORT: "8080"
  
  # Database configuration
  DATABASE_DRIVER: "org.postgresql.Driver"
  JPA_DDL_AUTO: "validate"
  JPA_SHOW_SQL: "false"
  
  # Redis configuration
  REDIS_HOST: "redis-service"
  REDIS_PORT: "6379"
  
  # Logging configuration
  LOG_LEVEL_ROOT: "INFO"
  LOG_LEVEL_APP: "INFO"
  LOG_FILE_PATH: "/app/logs"
  
  # JVM configuration
  JVM_OPTS: "-Xms512m -Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication"
  
  # Kubernetes-specific
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics,prometheus"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
  MANAGEMENT_HEALTH_READINESS_STATE_ENABLED: "true"
  MANAGEMENT_HEALTH_LIVENESS_STATE_ENABLED: "true"

---
# Application configuration file
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-boost-app-config
  namespace: spring-boost
  labels:
    app: spring-boost
data:
  application-kubernetes.yml: |
    spring:
      application:
        name: spring-boost
      cloud:
        kubernetes:
          discovery:
            enabled: true
            all-namespaces: false
          config:
            enabled: true
            sources:
              - name: spring-boost-config
              - name: shared-config
                namespace: default
      datasource:
        url: ${DATABASE_URL}
        username: ${DATABASE_USERNAME}
        password: ${DATABASE_PASSWORD}
        driver-class-name: ${DATABASE_DRIVER}
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
      redis:
        host: ${REDIS_HOST}
        port: ${REDIS_PORT}
        password: ${REDIS_PASSWORD:}
        timeout: 2000ms
        lettuce:
          pool:
            max-active: 10
            max-idle: 10
            min-idle: 1
    
    server:
      port: ${SERVER_PORT:8080}
      shutdown: graceful
    
    management:
      endpoints:
        web:
          exposure:
            include: ${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE}
      endpoint:
        health:
          probes:
            enabled: ${MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED:true}
          show-details: when-authorized
      health:
        readiness-state:
          enabled: ${MANAGEMENT_HEALTH_READINESS_STATE_ENABLED:true}
        liveness-state:
          enabled: ${MANAGEMENT_HEALTH_LIVENESS_STATE_ENABLED:true}
      metrics:
        export:
          prometheus:
            enabled: true
    
    logging:
      level:
        com.springboost: ${LOG_LEVEL_APP:INFO}
        org.springframework: INFO
        org.hibernate: WARN
      file:
        path: ${LOG_FILE_PATH:/app/logs}
```

### Secrets Configuration

```yaml
# k8s/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: spring-boost-db-secret
  namespace: spring-boost
  labels:
    app: spring-boost
type: Opaque
data:
  url: amRiYzpwb3N0Z3Jlc3FsOi8vcG9zdGdyZXM6NTQzMi9zcHJpbmdib29zdA== # jdbc:postgresql://postgres:5432/springboost
  username: c3ByaW5nYm9vc3Q= # springboost
  password: cGFzc3dvcmQ= # password

---
apiVersion: v1
kind: Secret
metadata:
  name: spring-boost-secrets
  namespace: spring-boost
  labels:
    app: spring-boost
type: Opaque
data:
  JWT_SECRET: bXlfc3VwZXJfc2VjcmV0X2p3dF9rZXk= # my_super_secret_jwt_key
  API_SECRET: YXBpX3NlY3JldF9rZXk= # api_secret_key
  REDIS_PASSWORD: cmVkaXNfcGFzc3dvcmQ= # redis_password
```

## Advanced Kubernetes Configurations

### Horizontal Pod Autoscaler

```yaml
# k8s/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: spring-boost-hpa
  namespace: spring-boost
  labels:
    app: spring-boost
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-boost-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
      - type: Pods
        value: 2
        periodSeconds: 60
      selectPolicy: Max
```

### Pod Disruption Budget

```yaml
# k8s/pdb.yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: spring-boost-pdb
  namespace: spring-boost
  labels:
    app: spring-boost
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: spring-boost
      component: backend
```

### Network Policy

```yaml
# k8s/network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: spring-boost-network-policy
  namespace: spring-boost
spec:
  podSelector:
    matchLabels:
      app: spring-boost
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: nginx-ingress
    - namespaceSelector:
        matchLabels:
          name: monitoring
    - podSelector:
        matchLabels:
          app: spring-boost
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: database
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - namespaceSelector:
        matchLabels:
          name: redis
    ports:
    - protocol: TCP
      port: 6379
  - to: []
    ports:
    - protocol: TCP
      port: 53
    - protocol: UDP
      port: 53
  - to: []
    ports:
    - protocol: TCP
      port: 443
```

### Service Account and RBAC

```yaml
# k8s/rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-boost-sa
  namespace: spring-boost
  labels:
    app: spring-boost

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: spring-boost-role
  namespace: spring-boost
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets", "services", "endpoints"]
  verbs: ["get", "list", "watch"]
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: spring-boost-rolebinding
  namespace: spring-boost
subjects:
- kind: ServiceAccount
  name: spring-boost-sa
  namespace: spring-boost
roleRef:
  kind: Role
  name: spring-boost-role
  apiGroup: rbac.authorization.k8s.io
```

## Ingress Configuration

### NGINX Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring-boost-ingress
  namespace: spring-boost
  labels:
    app: spring-boost
  annotations:
    kubernetes.io/ingress.class: "nginx"
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/use-regex: "true"
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/proxy-connect-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "30"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "30"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - api.springboost.com
    secretName: spring-boost-tls
  rules:
  - host: api.springboost.com
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: spring-boost-service
            port:
              number: 80
      - path: /actuator/health
        pathType: Exact
        backend:
          service:
            name: spring-boost-service
            port:
              number: 80
```

### Application Gateway (Azure)

```yaml
# k8s/ingress-agic.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring-boost-agic-ingress
  namespace: spring-boost
  annotations:
    kubernetes.io/ingress.class: azure/application-gateway
    appgw.ingress.kubernetes.io/backend-path-prefix: "/"
    appgw.ingress.kubernetes.io/ssl-redirect: "true"
    appgw.ingress.kubernetes.io/connection-draining: "true"
    appgw.ingress.kubernetes.io/connection-draining-timeout: "30"
    appgw.ingress.kubernetes.io/cookie-based-affinity: "false"
    appgw.ingress.kubernetes.io/request-timeout: "30"
    appgw.ingress.kubernetes.io/health-probe-path: "/actuator/health"
    appgw.ingress.kubernetes.io/health-probe-interval: "30"
    appgw.ingress.kubernetes.io/health-probe-timeout: "5"
    appgw.ingress.kubernetes.io/health-probe-unhealthy-threshold: "3"
spec:
  tls:
  - hosts:
    - api.springboost.com
    secretName: spring-boost-tls
  rules:
  - host: api.springboost.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: spring-boost-service
            port:
              number: 80
```

## Monitoring and Observability

### ServiceMonitor for Prometheus

```yaml
# k8s/monitoring/servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: spring-boost-metrics
  namespace: spring-boost
  labels:
    app: spring-boost
    prometheus: kube-prometheus
spec:
  selector:
    matchLabels:
      app: spring-boost
      component: backend
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
    scrapeTimeout: 10s
  namespaceSelector:
    matchNames:
    - spring-boost
```

### Grafana Dashboard ConfigMap

```yaml
# k8s/monitoring/grafana-dashboard.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-boost-dashboard
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
data:
  spring-boost-dashboard.json: |
    {
      "dashboard": {
        "id": null,
        "title": "Spring Boost Application Dashboard",
        "tags": ["spring-boot", "spring-boost"],
        "timezone": "browser",
        "panels": [
          {
            "title": "Application Health",
            "type": "stat",
            "targets": [
              {
                "expr": "up{job=\"spring-boost-metrics\"}",
                "legendFormat": "{{instance}}"
              }
            ]
          },
          {
            "title": "HTTP Request Rate",
            "type": "graph",
            "targets": [
              {
                "expr": "rate(http_server_requests_seconds_count[5m])",
                "legendFormat": "{{method}} {{uri}}"
              }
            ]
          },
          {
            "title": "Response Time",
            "type": "graph",
            "targets": [
              {
                "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))",
                "legendFormat": "95th percentile"
              }
            ]
          },
          {
            "title": "JVM Memory Usage",
            "type": "graph",
            "targets": [
              {
                "expr": "jvm_memory_used_bytes / jvm_memory_max_bytes",
                "legendFormat": "{{area}}"
              }
            ]
          }
        ],
        "time": {
          "from": "now-1h",
          "to": "now"
        },
        "refresh": "30s"
      }
    }
```

## Deployment Strategies

### Blue-Green Deployment

```yaml
# k8s/deployment-blue-green.yaml
# Blue deployment (current)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-boost-blue
  namespace: spring-boost
  labels:
    app: spring-boost
    version: blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: spring-boost
      version: blue
  template:
    metadata:
      labels:
        app: spring-boost
        version: blue
    spec:
      containers:
      - name: spring-boost
        image: spring-boost:1.0.0
        # ... container spec

---
# Green deployment (new)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-boost-green
  namespace: spring-boost
  labels:
    app: spring-boost
    version: green
spec:
  replicas: 3
  selector:
    matchLabels:
      app: spring-boost
      version: green
  template:
    metadata:
      labels:
        app: spring-boost
        version: green
    spec:
      containers:
      - name: spring-boost
        image: spring-boost:2.0.0
        # ... container spec

---
# Service pointing to active deployment
apiVersion: v1
kind: Service
metadata:
  name: spring-boost-service
  namespace: spring-boost
spec:
  selector:
    app: spring-boost
    version: blue  # Switch to 'green' for blue-green deployment
  ports:
  - port: 80
    targetPort: 8080
```

### Canary Deployment with Istio

```yaml
# k8s/istio/virtual-service.yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: spring-boost-vs
  namespace: spring-boost
spec:
  hosts:
  - spring-boost-service
  http:
  - match:
    - headers:
        canary:
          exact: "true"
    route:
    - destination:
        host: spring-boost-service
        subset: v2
      weight: 100
  - route:
    - destination:
        host: spring-boost-service
        subset: v1
      weight: 90
    - destination:
        host: spring-boost-service
        subset: v2
      weight: 10

---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: spring-boost-dr
  namespace: spring-boost
spec:
  host: spring-boost-service
  subsets:
  - name: v1
    labels:
      version: v1.0.0
  - name: v2
    labels:
      version: v2.0.0
```

## Application Configuration for Kubernetes

### Kubernetes-specific Spring Boot Configuration

```java
@Configuration
@Profile("kubernetes")
public class KubernetesConfig {
    
    @Bean
    @ConditionalOnProperty(name = "spring.cloud.kubernetes.discovery.enabled", havingValue = "true")
    public DiscoveryClient kubernetesDiscoveryClient() {
        return new KubernetesDiscoveryClient();
    }
    
    @Bean
    public HealthContributor kubernetesHealthContributor() {
        return new KubernetesHealthContributor();
    }
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> kubernetesMetricsCustomizer(
            @Value("${KUBERNETES_NAMESPACE:default}") String namespace,
            @Value("${POD_NAME:unknown}") String podName) {
        
        return registry -> registry.config()
                .commonTags(
                        "namespace", namespace,
                        "pod", podName,
                        "application", "spring-boost"
                );
    }
}

@Component
@ConditionalOnKubernetes
public class KubernetesReadinessProbe {
    
    private final ApplicationContext applicationContext;
    private volatile boolean ready = false;
    
    public KubernetesReadinessProbe(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @EventListener
    public void handleApplicationReady(ApplicationReadyEvent event) {
        ready = true;
        log.info("Application is ready for traffic");
    }
    
    @EventListener
    public void handleApplicationFailure(ApplicationFailedEvent event) {
        ready = false;
        log.error("Application failed to start", event.getException());
    }
    
    public boolean isReady() {
        return ready && applicationContext instanceof WebApplicationContext;
    }
}
```

## Helm Chart Structure

### Chart.yaml

```yaml
# helm/Chart.yaml
apiVersion: v2
name: spring-boost
description: A Helm chart for Spring Boost application
type: application
version: 1.0.0
appVersion: "1.0.0"
keywords:
  - spring-boot
  - microservice
  - java
home: https://github.com/yourorg/spring-boost
sources:
  - https://github.com/yourorg/spring-boost
maintainers:
  - name: Your Team
    email: team@yourorg.com
dependencies:
  - name: postgresql
    version: "12.1.2"
    repository: https://charts.bitnami.com/bitnami
    condition: postgresql.enabled
  - name: redis
    version: "17.3.7"
    repository: https://charts.bitnami.com/bitnami
    condition: redis.enabled
```

### Values.yaml

```yaml
# helm/values.yaml
replicaCount: 3

image:
  repository: spring-boost
  pullPolicy: IfNotPresent
  tag: "1.0.0"

nameOverride: ""
fullnameOverride: ""

serviceAccount:
  create: true
  annotations: {}
  name: ""

podAnnotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/actuator/prometheus"

podSecurityContext:
  runAsNonRoot: true
  runAsUser: 10001
  runAsGroup: 10001
  fsGroup: 10001

securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop:
    - ALL

service:
  type: ClusterIP
  port: 80
  targetPort: 8080

ingress:
  enabled: true
  className: "nginx"
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: letsencrypt-prod
  hosts:
    - host: api.springboost.local
      paths:
        - path: /
          pathType: Prefix
  tls:
    - secretName: spring-boost-tls
      hosts:
        - api.springboost.local

resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 500m
    memory: 512Mi

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80

nodeSelector: {}

tolerations: []

affinity:
  podAntiAffinity:
    preferredDuringSchedulingIgnoredDuringExecution:
    - weight: 100
      podAffinityTerm:
        labelSelector:
          matchExpressions:
          - key: app.kubernetes.io/name
            operator: In
            values:
            - spring-boost
        topologyKey: kubernetes.io/hostname

# Application configuration
config:
  springProfiles: "kubernetes,prod"
  logLevel: "INFO"
  jvmOpts: "-Xms512m -Xmx1g -XX:+UseG1GC"

# Database configuration
postgresql:
  enabled: true
  auth:
    postgresPassword: "postgres"
    username: "springboost"
    password: "password"
    database: "springboost"

redis:
  enabled: true
  auth:
    enabled: false

# Monitoring
monitoring:
  serviceMonitor:
    enabled: true
    interval: 30s
    path: /actuator/prometheus
```

This comprehensive Kubernetes integration guide provides production-ready patterns for deploying Spring Boot applications on Kubernetes with proper security, scalability, and observability configurations.
