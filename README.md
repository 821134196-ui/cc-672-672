# 客运大巴临时换车座位重排系统

大巴临时更换车型后，调度在新车座位图上重新安排已售座位；**乘客页与司机页读同一份最终名单**，
乘客逐人确认新座位，未确认不能检票；连续换车保留完整座位版本与检票历史；
两名调度并发换车不会把同一个座位分给两个人。

- **后端**：Java 17 + Quarkus 3.17 + Hibernate ORM (Panache) + SQLite
- **前端**：Svelte 4 + Vite 5（调度台 / 乘客页 / 司机页 三视图）
- **外部接口**：车辆调度、短信均为**本地模拟**（落库 + 短信另写 `backend/data/sms-mock.log`）
- **一条命令启动**：`./start.sh`

## 快速开始

```bash
./start.sh          # 启动后打开 http://localhost:5173
./start.sh reset    # 清空数据库重新生成演示数据后启动
./start.sh test     # 运行全部自动化测试（7 个用例）
```

环境要求：JDK 17+、Maven 3.8+、Node.js 18+。
（当前机器无 root 权限时，脚本会自动加载 `~/tools/env.sh` 中用户空间安装的 JDK/Maven。）

## 演示数据

启动时自动生成 13 名已售票乘客与 4 辆车：

| 车辆 | 座位 | 用途 |
|---|---|---|
| `A-38` | 38 座（含 ♿W1 轮椅位、🧒C1 儿童座椅位） | 初始车辆，v1 全员已确认 |
| `B-45` | 45 座（含轮椅位/儿童座椅位，座位更宽松） | 正常换车场景 |
| `C-30` | 30 座，**无轮椅位、无儿童座椅位** | 演示特殊需求无法满足 → **转人工** |
| `E-15` | 15 座窄排小客，普通座被轮椅位隔成 2 座一段 | 演示**同行人被拆散**（家庭组坐不满连续段） |

同行人组：**张家四口**（张伟、李娟、张兰 + 需要儿童座椅的张小明）、**王强-赵敏**。
轮椅乘客：陈建国。

## 推荐演示流程（对应四个重点检查项）

### 1. 同行人座位安排
- 调度台选 `B-45` 执行换车：张家在 1A/1B/1C + 孩子坐相邻的 C1 儿童座椅位，王强赵敏 2A/2B；无拆散问题。
- 再换 `E-15`：普通座最长连续段只有 2，问题列表出现 **GROUP_SPLIT（同行人被拆散）**，调度可见并可处理。
- 换到 `C-30`：张小明（儿童座椅）、陈建国（轮椅位）进入 **UNASSIGNED + 转人工问题**，系统**不会**偷偷把他们改派到普通座。

### 2. 连续换车
- 换车生成新版本（v1→v2→v3…），旧版本标记 SUPERSEDED 但完整保留；
  调度台「查看版本/短信/调度记录」可回看任一历史版本名单。
- 检票记录按（乘客, 版本）保存：v2 检过票的乘客在 v2 快照中永远显示已检票；
  换到 v3 后需在新车上重新确认，检票计数重新累计。

### 3. 未确认乘客检票
- 乘客页并列显示**原座（划线）→ 新座**，点「我确认新座位无误」后状态变 CONFIRMED。
- 司机页未确认乘客按钮为「🚫 禁止检票」；即使直接调 API 也返回 **409「乘客尚未确认新座位」**。

### 4. 并发占座
- 换车请求必须携带 `expectedVersionNo`；两名调度同时基于同一版本提交时，
  一个成功、另一个收到 **409 版本过期，请刷新后重试**。
- 数据库再设双保险：`seat_version.active_flag` 唯一（至多一个活动版本）、
  `seat_assignment(version_id, seat_code)` 唯一（一座一人）；SQLite 连接池固定 1，写事务天然串行。

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/manifest` | 当前活动版本名单（乘客端/司机端共用） |
| GET | `/api/vehicles` | 可调配车辆（模拟车辆调度接口） |
| POST | `/api/seat/swap` | 换车重排 `{vehicleCode, expectedVersionNo, operator, reason}` |
| GET | `/api/passengers` / `/api/passengers/{id}` | 乘客列表 / 乘客视图（原座/新座并列） |
| POST | `/api/passengers/{id}/confirm` | 乘客确认新座位 |
| POST | `/api/boarding/{id}/board` / `unboard` | 司机检票 / 撤销（未确认返回 409） |
| GET | `/api/versions` / `/api/versions/{no}` | 版本历史 / 历史版本快照 |
| GET | `/api/issues`、POST `/api/issues/{id}/state` | 问题列表 / 标记已解决或转人工 |
| GET | `/api/mock/sms`、`/api/mock/dispatch-logs` | 模拟短信/调度工单出站记录 |
| POST | `/api/admin/reset` | 重置演示数据 |

## 工程结构

```
backend/
  src/main/java/com/example/bus/
    domain/        实体：Vehicle/Passenger/TravelGroup/SeatVersion/SeatAssignment/...
    model/         SeatMap 座位图与相邻判定
    service/       ReassignmentPlanner（纯算法）、SeatService（事务编排+并发控制）、Mock*
    web/           JAX-RS 资源
  src/test/        算法单测 5 个 + HTTP 集成测试 2 个（连续换车、并发换车）
frontend/src/
    views/         DispatchView / PassengerView / DriverView
    components/    SeatMap.svelte
    store.js       全局唯一 manifest store（三视图一致的数据源）
start.sh           一键启动 / test / reset
```

## 关键设计决策

1. **一致性先于一切**：乘客确认、司机检票、调度换车全部围绕"当前唯一 ACTIVE 版本"，
   名单只有 `/api/manifest` 一个数据源。
2. **转人工的乘客不自动改派**：轮椅位/儿童座椅无法满足时，乘客保持无座并产生阻断性问题，
   避免给特殊需求乘客安排不合规座位。
3. **拆散判定按物理连通**：同排 order 连续（嵌入排内的轮椅位/儿童座椅位算连通）即为"坐在一起"，
   跨排不算；算法找不到连通座位时选最靠近的方案并报 GROUP_SPLIT。
4. **乐观版本号 + 数据库约束双保险**：应用层快速失败（409），约束层防止任何绕过路径。
