---
description: "Use when writing tests (Vitest/JUnit/MockMvc), reviewing test quality, setting up test infrastructure, or debugging flaky tests."
alwaysApply: false
globs: "**/*.test.ts,**/*.spec.ts,**/*.test.java,**/*IT.java,**/*Test.java"
---

# 全栈测试规范

> 覆盖前端 Vitest（Vue 3 + TypeScript）、后端 JUnit 5 + Spring Boot Test、MockMvc API 测试的完整测试规范。统一 ✅/❌ 代码对比格式，确保测试可维护、可隔离、可信任。

---

## 1. 前端 Vitest 测试规范

### 1.1 组件测试 — `@vue/test-utils` mount

**核心原则：** 组件测试必须完整注入所有依赖（provide / inject / store / router），不允许隐式依赖导致测试结果不可信。

#### 1.1.1 provide / inject 正确注入

❌ **DO NOT** — 缺少 `provide` 导致子组件 `inject` 拿到 `undefined`，测试静默失败：

```ts
import { mount } from '@vue/test-utils'
import UserProfile from '@/components/UserProfile.vue'

test('renders user name', () => {
  const wrapper = mount(UserProfile)
  // ❌ UserProfile 内 inject('currentUser') 返回 undefined → 渲染空白
  expect(wrapper.text()).toContain('张三')
  // 测试通过可能只是匹配到静态文案，实际逻辑未覆盖
})
```

✅ **DO** — 通过 `global.provide` 精确注入所有依赖：

```ts
import { mount } from '@vue/test-utils'
import UserProfile from '@/components/UserProfile.vue'

test('renders user name', () => {
  const wrapper = mount(UserProfile, {
    global: {
      provide: {
        currentUser: ref({ id: 1, name: '张三', role: 'admin' }),
        permissions: ['user:read', 'user:write']
      }
    }
  })
  expect(wrapper.text()).toContain('张三')
  expect(wrapper.find('[data-testid="role-badge"]').text()).toBe('admin')
})
```

#### 1.1.2 组件异步操作测试

❌ **DO NOT** — 不等待异步完成就断言：

```ts
test('loads and displays user list', () => {
  const wrapper = mount(UserList)
  // ❌ fetchUsers 是 async，此时数据尚未加载
  expect(wrapper.findAll('li').length).toBeGreaterThan(0)
})
```

✅ **DO** — 使用 `flushPromises` 或 `nextTick` 等待 DOM 更新：

```ts
import { flushPromises } from '@vue/test-utils'

test('loads and displays user list', async () => {
  const wrapper = mount(UserList)
  await flushPromises()
  await nextTick()
  expect(wrapper.findAll('li').length).toBe(3)
})
```

#### 1.1.3 事件 emit 测试

❌ **DO NOT** — 不验证 emit 的 payload 内容：

```ts
test('submits form', async () => {
  const wrapper = mount(LoginForm)
  await wrapper.find('form').trigger('submit.prevent')
  // ❌ 只验证 emit 事件名，不验证数据正确性
  expect(wrapper.emitted('submit')).toBeTruthy()
})
```

✅ **DO** — 精确断言 emit 的数量和 payload：

```ts
test('submits form with correct payload', async () => {
  const wrapper = mount(LoginForm)
  await wrapper.find('input[name="username"]').setValue('admin')
  await wrapper.find('input[name="password"]').setValue('123456')
  await wrapper.find('form').trigger('submit.prevent')

  const submitEvents = wrapper.emitted('submit')
  expect(submitEvents).toHaveLength(1)
  expect(submitEvents![0]).toEqual([{ username: 'admin', password: '123456' }])
})
```

### 1.2 Composable 测试

❌ **DO NOT** — 在组件上下文外直接调用 composable：

```ts
import { useCounter } from '@/composables/useCounter'

test('increments counter', () => {
  const { count, increment } = useCounter()
  // ❌ composable 依赖 onMounted / inject / router 等，裸调会报错或行为异常
  increment()
  expect(count.value).toBe(1)
})
```

✅ **DO** — 使用 `@vue/test-utils` 的 `createApp` 或 `withSetup` 包装：

```ts
import { mount } from '@vue/test-utils'
import { useCounter } from '@/composables/useCounter'

function withSetup<T>(composable: () => T): T {
  let result!: T
  const Comp = defineComponent({
    setup() {
      result = composable()
      return () => null
    }
  })
  mount(Comp)
  return result
}

test('increments counter', () => {
  const { count, increment } = withSetup(() => useCounter())
  increment()
  expect(count.value).toBe(1)
})
```

或使用 `@vueuse/core` 的 `createSetup` 工具：

```ts
import { createSetup } from '@vueuse/core/test-utils'

test('increments counter', () => {
  const [result] = createSetup(() => useCounter())
  result.increment()
  expect(result.count.value).toBe(1)
})
```

### 1.3 Store 测试 — Pinia

❌ **DO NOT** — 直接使用真实 store 实例污染测试之间状态：

```ts
import { useAuthStore } from '@/stores/auth'
import { setActivePinia, createPinia } from 'pinia'

// ❌ 全局 store 实例在各测试间共享，导致测试相互影响
setActivePinia(createPinia())

test('user is initially not logged in', () => {
  const store = useAuthStore()
  expect(store.isLoggedIn).toBe(false)
})

test('login sets token', () => {
  const store = useAuthStore()
  store.login('admin', 'password')
  // ❌ 如果上一个测试修改了 store，这里拿到的可能是脏数据
  expect(store.token).toBeDefined()
})
```

✅ **DO** — 使用 `createTestingPinia` 为每个测试创建隔离的 store：

```ts
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAuthStore } from '@/stores/auth'
import LoginForm from '@/components/LoginForm.vue'

test('user is initially not logged in', () => {
  const wrapper = mount(LoginForm, {
    global: {
      plugins: [createTestingPinia({ stubActions: false })]
    }
  })
  const store = useAuthStore()
  // ✅ 每个测试都是全新的 store 实例
  expect(store.isLoggedIn).toBe(false)
})

test('login dispatches action', () => {
  const wrapper = mount(LoginForm, {
    global: {
      plugins: [createTestingPinia({ stubActions: false })]
    }
  })
  const store = useAuthStore()
  store.login('admin', '123456')
  // ✅ 完全隔离，不受其他测试影响
  expect(store.login).toHaveBeenCalledWith('admin', '123456')
})
```

### 1.4 快照测试

❌ **DO NOT** — 无脑快照一切，一旦 UI 微调就全量更新：

```ts
test('matches snapshot', () => {
  const wrapper = mount(EntirePage)
  // ❌ 快照 2000 行，任何子组件变更都导致快照失效
  // ❌ 开发者直接 -u 更新而不审查差异
  expect(wrapper.html()).toMatchSnapshot()
})
```

✅ **DO** — 对有意义的局部做语义化快照，结合明确断言：

```ts
test('renders pricing card for enterprise plan', () => {
  const wrapper = mount(PricingCard, {
    props: { plan: 'enterprise', price: 999, features: ['无限用户', '专属支持'] }
  })
  // ✅ 只快照关键的 DOM 片段
  expect(wrapper.find('.price-value').text()).toBe('¥999')
  expect(wrapper.find('.plan-name').text()).toBe('Enterprise')
  // 快照仅覆盖静态结构部分
  expect(wrapper.find('.feature-list').html()).toMatchSnapshot('enterprise-features')
})
```

---

## 2. 后端 JUnit 5 测试规范

### 2.1 单元测试 — Mock 外部依赖

❌ **DO NOT** — 单元测试依赖真实数据库、Redis、外部 API：

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Test
    void shouldCreateOrder() {
        // ❌ 直接 new 真实 Repository，需要数据库连接
        OrderRepository repo = new OrderRepositoryImpl(dataSource);
        OrderService service = new OrderService(repo);

        Order order = service.createOrder(OrderCreateDTO.builder()
            .userId(1L).amount(new BigDecimal("99.00")).build());

        assertNotNull(order.getId());
        // ❌ 数据真实写入数据库，测试间相互污染
    }
}
```

✅ **DO** — Mock 所有外部依赖，只测试业务逻辑：

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrderAndInvokePayment() {
        OrderCreateDTO dto = OrderCreateDTO.builder()
            .userId(1L).amount(new BigDecimal("99.00")).build();

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1001L);
                return o;
            });
        when(paymentGateway.charge(any())).thenReturn(PaymentResult.success("txn-001"));

        Order result = orderService.createOrder(dto);

        assertNotNull(result.getId());
        verify(paymentGateway).charge(any());
        verify(orderRepository).save(any(Order.class));
    }
}
```

### 2.2 集成测试 — `@SpringBootTest` 正确用法

❌ **DO NOT** — 集成测试直接使用生产配置，污染生产数据或依赖生产环境：

```java
// ❌ 使用 application.yml（可能是生产配置）
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnUsers() throws Exception {
        // ❌ 连的是生产数据库？执行 DELETE 就灾难了
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk());
    }
}
```

✅ **DO** — 使用 `@TestConfiguration` + 测试 profile 隔离环境：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Sql(scripts = "/sql/init-users.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public DataSource dataSource() {
            // ✅ 使用 H2 内存数据库 / Testcontainers
            return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        }
    }

    @Test
    void shouldReturnUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3));
    }
}
```

### 2.3 `@MockBean` vs `@SpyBean` 使用场景

| 注解 | 使用场景 | 行为 |
|------|----------|------|
| `@MockBean` | 完全替换 Spring 容器中的 Bean，不需要真实逻辑 | 所有方法默认返回 null/0/false，除非 `when().thenReturn()` |
| `@SpyBean` | 部分 Mock，保留真实 Bean 逻辑，仅覆写特定方法 | 未 stub 的方法走真实实现 |

❌ **DO NOT** — 该用 Spy 时用 Mock，导致全部逻辑都要 stub：

```java
// ❌ UserService 有 10 个方法，只需要 mock getUserById，却 MockBean 全部
@MockBean
private UserService userService;

@Test
void shouldFormatUserName() {
    // 需要 stub 所有被调用到的方法
    when(userService.getUserById(1L)).thenReturn(mockUser);
    when(userService.formatName(any())).thenReturn("张三");
    when(userService.isActive(any())).thenReturn(true);
    // ... 漏 stub 就会 NPE
}
```

✅ **DO** — 只需要覆写少数方法时用 `@SpyBean`：

```java
@SpyBean
private UserService userService;

@Test
void shouldFormatUserName() {
    // ✅ 只覆写需要控制的方法，其余走真实逻辑
    doReturn(mockUser).when(userService).getUserById(1L);

    String result = userService.formatUserDisplay(1L);
    assertEquals("张三 (活跃)", result);
}
```

**选择指南：**
| 场景 | 推荐 |
|------|------|
| 外部 API 调用（支付、短信） | `@MockBean` |
| 数据库访问层 | `@MockBean` |
| 部分覆写复杂 Service 逻辑 | `@SpyBean` |
| 验证方法被调用次数 | `@SpyBean` |

### 2.4 测试数据隔离 — `@Transactional`

❌ **DO NOT** — 手动在 `@AfterEach` 中清理数据，容易遗漏导致测试污染：

```java
@SpringBootTest
class UserRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        // ❌ 手动清理：容易遗漏新表、级联删除顺序出错
        userRepository.deleteAll();
        orderRepository.deleteAll();
        // ... 如果有 20 张表呢？
    }

    @Test
    void shouldSaveUser() {
        userRepository.save(new User("张三"));
        assertEquals(1, userRepository.count());
    }
}
```

✅ **DO** — 使用 `@Transactional` 自动回滚：

```java
@SpringBootTest
@Transactional
class UserRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveUser() {
        // ✅ 测试结束后自动回滚，不需要手动清理
        userRepository.save(new User("张三"));
        assertEquals(1, userRepository.count());
    }

    @Test
    void shouldFindByEmail() {
        userRepository.save(new User("李四"));
        // ✅ 每个测试独立，数据互不影响
        Optional<User> found = userRepository.findByEmail("lisi@example.com");
        assertTrue(found.isPresent());
    }
}
```

> ⚠️ **注意：** 如果测试方法内使用了 `@Async` 或手动 `new Thread()`，`@Transactional` 回滚不会传播到新线程。此类场景使用 `@Sql` 脚本初始化 + 清理更安全。

---

## 3. API 测试规范 — MockMvc

### 3.1 MockMvc 完整请求链测试

❌ **DO NOT** — 只测 Controller 层，跳过 Filter / Interceptor / 序列化：

```java
@Test
void shouldCreateUser() {
    UserController controller = new UserController(userService);
    // ❌ 直接调 Controller 方法，绕过了：
    //    - JWT 鉴权 Filter
    //    - @Valid 校验
    //    - Jackson 序列化/反序列化
    //    - 全局异常处理
    Result<UserDTO> result = controller.createUser(new UserCreateDTO());
    assertTrue(result.isSuccess());
}
```

✅ **DO** — 使用 MockMvc 覆盖完整请求链路：

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldCreateUserWithValidInput() throws Exception {
        String body = """
            {
                "username": "zhangsan",
                "email": "zhangsan@example.com",
                "password": "Abc@12345"
            }
            """;

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.username").value("zhangsan"))
            .andExpect(jsonPath("$.data.email").value("zhangsan@example.com"))
            // ✅ 不返回 password
            .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void shouldReturn401WithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldReturn400ForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"email\":\"not-an-email\",\"password\":\"Abc@12345\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("邮箱格式不正确")));
    }
}
```

### 3.2 测试数据管理 — `@Sql` 注解

❌ **DO NOT** — 硬编码依赖现有数据，换环境测试就挂：

```java
@Test
void shouldReturnActiveUsers() throws Exception {
    // ❌ 假设数据库里 ID=1,2,3 的用户已经存在且状态是 ACTIVE
    //    换个人跑、换 CI 环境 → 数据不存在 → 测试失败
    mockMvc.perform(get("/api/users/active"))
        .andExpect(jsonPath("$.data.length()").value(3));
}
```

✅ **DO** — 使用 `@Sql` 注解在测试前初始化精确数据集：

```java
@Test
@Sql(scripts = "/sql/init-active-users.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup-users.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
void shouldReturnActiveUsers() throws Exception {
    // ✅ `/sql/init-active-users.sql` 脚本：
    //    INSERT INTO users VALUES (1, 'user1', 'ACTIVE');
    //    INSERT INTO users VALUES (2, 'user2', 'ACTIVE');
    //    INSERT INTO users VALUES (3, 'user3', 'INACTIVE');
    mockMvc.perform(get("/api/users/active"))
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].username").value("user1"))
        .andExpect(jsonPath("$.data[1].username").value("user2"));
}
```

### 3.3 测试环境隔离 — Profile

| Profile | 用途 | 数据库 | 外部依赖 |
|---------|------|--------|----------|
| `test` | 单元测试 + MockMvc 集成测试 | H2 内存 / Testcontainers | 全部 Mock |
| `dev` | 本地开发联调 | 本地 PostgreSQL | 本地服务 |
| `prod` | 生产环境 | 生产 PostgreSQL | 真实服务 |

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always

# ✅ 所有集成测试统一加 @ActiveProfiles("test")
```

---

## 4. 测试反模式禁止清单

### 4.1 NEVER 无断言的测试

```java
// ❌ 只调方法不验证 — 这是"运行测试"不是"通过测试"
@Test
void shouldCreateOrder() {
    orderService.createOrder(dto);
    // 没有 assert → 只要不抛异常就通过 → 假绿
}
```

```java
// ✅ 必须有至少一个断言
@Test
void shouldCreateOrder() {
    Order result = orderService.createOrder(dto);
    assertNotNull(result);
    assertEquals(OrderStatus.CREATED, result.getStatus());
}
```

### 4.2 NEVER 测试间依赖执行顺序

```java
// ❌ test2 依赖 test1 创建的数据
@Test
@Order(1)
void shouldCreateUser() {
    userId = userService.createUser(dto).getId();
}

@Test
@Order(2)
void shouldUpdateUser() {
    // ❌ 如果 test1 失败，test2 也跟着挂，且无法并行运行
    userService.updateUser(userId, updateDto);
}
```

```java
// ✅ 每个测试独立准备自己的数据
@Test
@Sql("/sql/init-user.sql")
void shouldUpdateUser() {
    userService.updateUser(1L, updateDto);
    User updated = userService.getUserById(1L);
    assertEquals("新名字", updated.getName());
}
```

### 4.3 NEVER 硬编码测试数据导致脆弱测试

```java
// ❌ 脆弱测试：数据库 ID 自增策略变了、UUID 格式变了 → 测试全挂
@Test
void shouldReturnUser() throws Exception {
    mockMvc.perform(get("/api/users/1"))
        .andExpect(jsonPath("$.data.createdAt").value("2025-01-15T10:30:00"))
        .andExpect(jsonPath("$.data.id").value(1));
}
```

```java
// ✅ 从响应中动态提取 ID 和时间，只验证结构和关键字段
@Test
@Sql("/sql/init-user.sql")
void shouldReturnUser() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/users/zhangsan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("zhangsan"))
        .andExpect(jsonPath("$.data.createdAt").exists())
        .andReturn();

    String createdAt = JsonPath.read(result.getResponse().getContentAsString(),
        "$.data.createdAt");
    assertNotNull(createdAt);
    // ✅ 用正则验证格式而非精确值
    assertTrue(createdAt.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"));
}
```

### 4.4 NEVER 测试间共享可变状态

```java
// ❌ 静态字段被多个测试并发访问，产生竞态条件
public class SharedStateTest {
    private static List<User> sharedUsers = new ArrayList<>();

    @Test
    void test1() {
        sharedUsers.add(new User("张三")); // ❌ 竞态
        assertEquals(1, sharedUsers.size());
    }

    @Test
    void test2() {
        sharedUsers.add(new User("李四")); // ❌ 竞态
        // 预期是 1，但如果并发运行可能是 2
        assertEquals(1, sharedUsers.size());
    }
}
```

```java
// ✅ 局部变量 + @BeforeEach 重新初始化
public class IsolatedTest {

    private List<User> users;

    @BeforeEach
    void setUp() {
        users = new ArrayList<>(); // ✅ 每个测试全新初始化
    }

    @Test
    void test1() {
        users.add(new User("张三"));
        assertEquals(1, users.size());
    }

    @Test
    void test2() {
        users.add(new User("李四"));
        assertEquals(1, users.size());
    }
}
```

### 4.5 NEVER 忽略异常只打 Log 的测试

```java
// ❌ 异常被吞，测试永远绿
@Test
void shouldHandleError() {
    try {
        riskyOperation();
        // 没有验证异常类型、消息、状态码
    } catch (Exception e) {
        log.error("出错了", e);
    }
}
```

```java
// ✅ assertThrows 精确验证异常
@Test
void shouldThrowBusinessExceptionForNegativeAmount() {
    OrderCreateDTO dto = OrderCreateDTO.builder()
        .amount(new BigDecimal("-100")).build();

    BusinessException ex = assertThrows(BusinessException.class,
        () -> orderService.createOrder(dto));

    assertEquals("ORDER_AMOUNT_INVALID", ex.getCode());
    assertTrue(ex.getMessage().contains("金额不能为负数"));
}
```

---

## 5. 中文执行层

### 触发条件
- 编写前端 `.test.ts` / `.spec.ts` 测试文件
- 编写后端 `*Test.java` / `*IT.java` 测试文件
- 审查测试代码质量、排查脆性测试（flaky test）
- 搭建或调整测试基础设施（H2 / Testcontainers / MockMvc）
- 用户提到"写测试"、"加单元测试"、"集成测试"、"API 测试"、"测试挂了"

### 前置条件
- 前端：`vitest` + `@vue/test-utils` + `@pinia/testing` 已安装
- 后端：`spring-boot-starter-test` + `h2` 或 Testcontainers 依赖已配置
- `application-test.yml` 测试 profile 已存在
- 测试目录结构与源码目录一一对应

### 执行步骤
1. **识别测试类型：** 单元测试（MockitoExtension）vs 集成测试（@SpringBootTest）vs API 测试（MockMvc）
2. **前端组件测试：** mount → global.provide → await flushPromises → 断言
3. **前端 Store 测试：** createTestingPinia({ stubActions: false }) → 隔离 store 实例
4. **前端 Composable 测试：** withSetup 包装 → 调用 composable → 断言响应式值
5. **后端单元测试：** @Mock 外部依赖 → @InjectMocks 测试目标 → when/thenReturn → verify
6. **后端集成测试：** @SpringBootTest + @ActiveProfiles("test") + @Transactional → @Sql 初始化
7. **API 测试：** MockMvc → perform → andExpect(jsonPath) → 验证 HTTP 状态码 + 响应体
8. **检查反模式：** 确认无 4 项 NEVER（无断言/依赖顺序/硬编码/共享状态）
9. **运行测试：** 执行 `npm run test` 或 `mvn test`，确认全部通过

### 完成证据
- 所有测试用例包含明确的 `assert*` / `expect` / `andExpect` 断言
- 每个测试独立可运行，不依赖执行顺序
- `@Transactional` 或 `@Sql` 确保测试间数据隔离
- MockMvc 测试覆盖完整请求链（Filter → Controller → ExceptionHandler）
- 测试运行结果：0 failures, 0 errors
- 无脆性测试（连续运行 3 次结果一致）

### 失败回退
- 前端组件测试失败 → 检查 provide/inject 是否完整注入，使用 `console.log(wrapper.html())` 排查 DOM
- MockMvc 401/403 → 检查 `@WithMockUser` 是否配置正确角色
- `@Transactional` 不生效 → 检查是否在 `@SpringBootTest` 类级别声明，排除 `@Async` 场景
- 测试间相互影响 → 检查是否有 `static` 可变字段，改用 `@BeforeEach` 初始化
- H2 兼容性问题 → 切换到 Testcontainers + 真实 PostgreSQL 镜像