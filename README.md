# 客运大巴临时换车座位重排系统

大巴临时更换车型后，调度重新安排已售座位；乘客确认新座位后才能检票登车；司机拿到与乘客确认一致的最终名单。

## 一条命令启动

```bash
./start.sh
```

启动后访问 http://localhost:8080 （前端由 Quarkus 直接托管）：

| 页面 | 地址 | 说明 |
|---|---|---|
| 调度台 | `/#/dispatcher` | 查看班次、选择备用车换车、查看拆散/转人工预警、座位图、手动调座、短信模拟记录 |
| 乘客 | `/#/passenger` | 输入订单号（演示数据 1–16），原座/新座并列显示，确认新座位 |
| 司机 | `/#/driver` | 最终登车名单（仅已确认乘客）、检票登车 |

> 数据库为 SQLite 文件 `backend/data/bus.db`，每次启动重建并写入演示数据（`backend/src/main/resources/import.sql`）。
> 前端改动后重新执行 `./start.sh` 即可；开发时也可 `cd frontend && npm run dev`（Vite 代理到 8080）。

## 演示流程

1. **调度台**：班次 T1001 当前用 48 座原车。点「换到此车」换到 40 座车（含轮椅位/儿童座椅位）——同行人自动安排同排相邻，赵四分到轮椅位，张小明分到儿童座椅位。
2. **乘客**：输入订单号看到原座 → 新座，点「确认新座位」。
3. **司机**：名单只出现已确认的乘客，点「检票登车」；未确认乘客检票会被拒绝（409）。
4. **再次换车**到 36 座车（无轮椅位）：赵四、张小明转人工（预警 + 短信通知），旧座位版本与检票记录全部保留在「座位版本历史」。

## 技术栈与关键设计

- **后端** Java 17 + Quarkus 3.15（REST + Hibernate ORM Panache），SQLite（`quarkus-jdbc-sqlite`，WAL 模式）
- **前端** Svelte + Vite，构建产物拷入 `META-INF/resources` 由 Quarkus 托管
- **座位版本**：每次换车生成新 `SeatVersion`，旧版本置为 `SUPERSEDED` 保留；检票记录挂在版本上，跨换车保留
- **确认规则**：新版本中座位号与上一已确认版本一致的乘客继承「已确认」，其余为「待确认」；未确认不能检票（409）
- **转人工**：轮椅位/儿童座椅位不足时，该乘客记为 `MANUAL_REVIEW` 并短信通知，调度台可见
- **拆散预警**：同行人无法整组安排同排连续座位时，标记 `groupSplit` 并在调度台红色预警
- **并发占座**：`assignment (version_id, seatNo)` 唯一约束 + SQLite WAL 快照隔离，两名调度同时分同一座位只有一个成功（409）
- **并发换车**：`Trip` 乐观锁（`@Version`），两名调度同时换车只有一个生效（409）
- **本地模拟**：车辆调度接口（`GET /api/dispatcher/trips/{id}/available-vehicles`）、短信接口（发送记录落库，`GET /api/mock/sms` 查看）

## 测试

```bash
cd backend && mvn test
```

覆盖需求点名的四个场景：

| 测试 | 验证点 |
|---|---|
| `CompanionSeatingTest` | 同行人同排相邻、轮椅/儿童座椅需求优先满足、座位不重复分配 |
| `ConsecutiveChangeTest` | 连续两次换车后三个版本都保留、v2 的检票记录仍在、司机名单跨版本显示已检票 |
| `UnconfirmedCheckInTest` | 未确认检票 409 → 确认后 200；转人工乘客不能确认也不能检票；司机名单只含已确认 |
| `ConcurrentAssignmentTest` | 两线程分同一座位 → 一个 200 一个 409 且座位只属一人；两线程同时换车 → 一个生效 |

## 主要 API

```
GET  /api/dispatcher/trips                              班次列表（含待确认/转人工计数）
GET  /api/dispatcher/trips/{id}                         班次座位分配明细
GET  /api/dispatcher/trips/{id}/available-vehicles      可用备用车（车辆调度模拟）
POST /api/dispatcher/trips/{id}/change-vehicle          换车重排 {vehicleId, operator}
GET  /api/dispatcher/trips/{id}/versions                座位版本历史
GET  /api/dispatcher/versions/{id}/seats                座位图
POST /api/dispatcher/versions/{id}/assign               手动调座 {bookingId, seatNo}
GET  /api/passenger/bookings/{id}                       乘客查看原座/新座
POST /api/passenger/bookings/{id}/confirm               乘客确认
POST /api/checkins                                      检票 {bookingId}（未确认 409）
GET  /api/driver/trips/{id}/manifest                    司机最终名单
GET  /api/mock/sms                                      短信发送记录（模拟）
```
