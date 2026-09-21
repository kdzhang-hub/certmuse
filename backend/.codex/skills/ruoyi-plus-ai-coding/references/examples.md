# 使用案例

## 案例 1：新增标准单表 CRUD

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 在 system 模块新增一个 client 管理的标准 CRUD。
请参考 generator 模板和现有 system 模块写法，补齐 entity、bo、vo、mapper、service、controller。
```

### 期望执行方式

- 先读 generator 的 `domain/bo/vo/service/serviceImpl/controller` 模板。
- 再读 `system` 模块里最接近的现有管理模块。
- 先生成骨架，再补权限、日志、校验、导出等细节。

## 案例 2：修改已有复杂模块

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 修改 workflow/category 的查询和导出逻辑，保持现有模块风格，不要简化成模板式单表 CRUD。
```

### 期望执行方式

- 先读当前 workflow 模块同类代码。
- 判断这是“复杂模块增强”，不是“从零生成”。
- 增量修改原逻辑，不要重写整个 service/controller。

## 案例 3：补唯一性校验与删除前校验

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 为 demo/demo 模块补充新增和修改时的唯一性校验，并补充删除前校验。
```

### 期望执行方式

- 优先修改 `validEntityBeforeSave(...)`。
- 根据模块现有风格补 `ServiceException` 或显式失败返回。
- 删除逻辑只补必要校验，不重构整套 CRUD。

## 案例 4：补数据权限与联表查询

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 为 system 模块某个列表查询增加部门数据权限和联表字段返回，参考现有 user mapper 的 MPJ 与 DataPermission 写法。
```

### 期望执行方式

- 先看 `SysUserMapper` 和相关 service。
- 判断需要 `BaseMapperPlus` 重写还是 MPJ 联表。
- 保持权限注解和联表风格一致。

## 案例 5：新增后端接口并同步前端骨架

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 为 monitor/cache 新增一个导出接口，并同步补齐 Vue 或 React 前端 api/types 调用骨架。
```

### 期望执行方式

- 先补后端 `controller/service`。
- 再根据后端路由和目标前端类型补 `src/api` 或 generator 风格的前端骨架。
- 保证导出接口路径和前端下载调用一致。

## 案例 6：推荐的高质量任务描述

下面这种描述最容易得到稳定结果：

```text
使用 $ruoyi-plus-ai-coding 在 workflow 模块新增一个标准列表管理功能：
1. 需要分页、导出、详情、增删改
2. 查询包含状态和创建时间范围
3. 保持现有 workflow 模块风格
4. 参考 generator 模板生成基础骨架
5. 删除前需要做业务校验
```

## 案例 7：修改 CertMuse 题目领域服务

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 修改 ruoyi-certmuse 的题目修订保存逻辑，保留现有版本冲突、幂等、审计和资源可见性校验。
Controller 依赖 QuestionService 接口，实现放在 service.impl，不生成标准 CRUD 方法簇。
```

### 期望执行方式

- 先读 `references/certmuse-backend.md`，再读题目 Controller、Service、Mapper XML 和相关测试。
- 使用 `QuestionService` 接口 + `QuestionServiceImpl` 实现，接口不加 `I` 前缀。
- 保留 `save` 等领域动作，不添加绕过修订链的 `insertByBo` 或 `updateByBo`。
- 显式资源可见性、`X-Request-Id`、行版本、事务和审计保持闭环。

## 案例 8：调整 CertMuse 知识树入口服务

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 将 ruoyi-certmuse 的知识树入口服务规范化为接口和实现，保持现有 HTTP 路由及查询、删除行为不变。
```

### 期望执行方式

- 使用 `KnowledgeTreeService` 接口 + `KnowledgeTreeServiceImpl` 实现。
- Controller 只注入接口，查询和 `deleteKnowledgeTree` 继续作为领域动作。
- 不修改 Mapper XML、返回结构、权限标识或删除前校验。

## 案例 9：在 CertMuse 新增简单配置 CRUD

### 用户提问示例

```text
使用 $ruoyi-plus-ai-coding 在 ruoyi-certmuse 新增一个单表模板配置管理功能。
该功能没有状态机、修订、幂等和多表写入，需要分页、详情、新增、修改和删除。
```

### 期望执行方式

- 先用 CertMuse 专项规则确认它满足简单 CRUD 条件，再读取通用 backend reference 和 generator 模板。
- 可以采用标准 Entity、BO、VO、Mapper 和 CRUD 方法簇。
- Controller 入口仍使用无 `I` 前缀的 Service 接口 + `Impl` 实现。
- 如果后续加入复杂资源归属或状态流转，重新评估是否应改为领域动作。

## 不推荐的任务描述

下面这种描述太模糊，容易导致产物偏离项目：

```text
帮我加个后端接口
```

更好的写法至少要补充：

- 模块名
- 表或业务名
- 是新增还是修改
- 是否需要分页、导出、权限、数据范围、联表
- 想参考哪个现有模块
