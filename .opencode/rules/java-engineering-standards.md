---
description: "Use when writing Java production code, reviewing code quality, configuring Lombok/MapStruct, designing exception hierarchy, or establishing team coding conventions."
alwaysApply: false
globs: "**/*.java,**/pom.xml"
---

# Java Engineering Standards

Establish and enforce Java production coding standards: Lombok conventions, MapStruct mapping, JSR303 validation, global exception handling, logging guidelines, and code review checklists.

## Use this skill when

- Setting up project coding conventions
- Writing DTO/VO/Entity mapping with MapStruct
- Configuring Lombok annotations (avoid anti-patterns)
- Implementing JSR303 Bean Validation
- Designing global exception hierarchy and handling
- Establishing logging standards (SLF4J + Logback)
- Performing code review for Java code quality

## Do not use this skill when

- Using Kotlin or other JVM languages
- The project already has an established standard that overrides these

## Instructions

### Lombok Convention

| Annotation | Use | Anti-Pattern |
|------------|-----|-------------|
| `@Data` | DTO / VO only | Do NOT use on JPA/MyBatis-Plus Entity |
| `@Getter` + `@Setter` | Entity classes | Preferred over @Data for entities |
| `@Builder` | Complex DTO construction | Don't combine with @Data on same class |
| `@Slf4j` | All service classes | Always use, never `System.out.println` |
| `@RequiredArgsConstructor` | Constructor injection | Preferred over @Autowired on field |

### Exception Hierarchy

```
BaseException (extends RuntimeException)
 ├── BusinessException       # Known business errors
 ├── ResourceNotFoundException # 404 cases
 ├── ValidationException     # Input validation failures
 ├── AuthException           # Auth / authorization
 └── SystemException         # Infrastructure errors
```

### Logging Levels

| Level | Use Case | Example |
|-------|----------|---------|
| ERROR | System failures requiring immediate attention | DB connection fail |
| WARN | Unexpected but recoverable | Retry exhausted |
| INFO | Business milestones | Order created: #12345 |
| DEBUG | Development diagnostics | SQL parameters |
| TRACE | Step-by-step flow | Filter chain traversal |

## Spring Boot 防幻觉禁止清单

### 1. NEVER 使用 @Autowired 字段注入 — 必须构造器注入

❌ **DO NOT** — 字段注入，不可测试、隐藏依赖：
```java
@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentService paymentService;
}
```

✅ **DO** — 构造器注入（Lombok `@RequiredArgsConstructor` 精简）：
```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
}
```

### 2. NEVER `catch (Exception e) {}` 静默吞异常

❌ **DO NOT** — 吞掉异常，无日志、无重抛，问题不可追踪：
```java
try {
    orderRepository.save(order);
} catch (Exception e) {
    // silent swallow — 生产排障噩梦
}
```

✅ **DO** — 记录日志并重新抛出业务异常：
```java
try {
    orderRepository.save(order);
} catch (DataAccessException e) {
    log.error("订单持久化失败: orderId={}", order.getId(), e);
    throw new BusinessException("订单创建失败，请稍后重试", e);
}
```

### 3. NEVER 在 @Data 实体上使用 @ToString 导致循环引用

❌ **DO NOT** — JPA/MyBatis-Plus 实体上用 `@Data`（自带 `@ToString`），双向关联触发 `StackOverflowError`：
```java
@Data
@Entity
@Table(name = "t_order")
public class Order {
    @Id
    private Long id;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;  // @ToString 会递归 items → order → ...
}
```

✅ **DO** — 仅用 `@Getter`/`@Setter`，显式排除双向关联：
```java
@Getter
@Setter
@Entity
@Table(name = "t_order")
public class Order {
    @Id
    private Long id;

    @OneToMany(mappedBy = "order")
    @ToString.Exclude
    private List<OrderItem> items;
}
```

### 4. NEVER 在 Controller 中写业务逻辑

❌ **DO NOT** — Controller 混杂校验、计算、持久化：
```java
@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderRepository orderRepository;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody OrderDTO dto) {
        if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("金额必须大于0");
        }
        Order order = new Order();
        order.setAmount(dto.getAmount().multiply(new BigDecimal("1.13")));
        order.setStatus("CREATED");
        orderRepository.save(order);
        return ResponseEntity.ok(order);
    }
}
```

✅ **DO** — Controller 仅做路由，Service 承载逻辑：
```java
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderVO> create(@Valid @RequestBody OrderDTO dto) {
        return ResponseEntity.ok(orderService.createOrder(dto));
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderVO createOrder(OrderDTO dto) {
        Order order = OrderMapper.INSTANCE.toEntity(dto);
        order.calculateTax();
        order.setStatus(OrderStatus.CREATED);
        orderRepository.save(order);
        return OrderMapper.INSTANCE.toVO(order);
    }
}
```

## 中文执行层

### 触发条件
- Use when writing Java production code, reviewing code quality, configuring annotations, designing exception hierarchy.

### 前置条件
- 确认项目使用的 Java 版本和框架版本
- 确认已引入 Lombok / MapStruct 等依赖

### 执行步骤
1. 确定目标编码维度（Lombok / MapStruct / 验证 / 异常 / 日志）
2. 按本 skill 的约定规则编码
3. 确保所有异常通过 GlobalExceptionHandler 统一处理
4. 日志按级别分级输出，避免敏感信息泄露

### 完成证据
- 代码无 IDE 编译警告
- 异常返回统一错误响应格式 `{code, message, data}`
- 日志按 RollingFile 归档配置

### 失败回退
- 降级使用 `@Data` 替代精细 Lombok 配置
- 手动 Setter/Getter 替代 Lombok（如果版本不兼容）