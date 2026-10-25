# 机型 / 系统适配层（vendor）

不同系统的系统服务实现类名、需要豁免的包名各不相同。这个包把这些差异收进"每个系统一个适配器"，
共享的 Hook 代码（`hooks/SystemXxxHooker.kt`）不感知当前是哪家系统。

> 本文讲适配层怎么用。怎么在真机上找到某个系统的内部类 / 方法，见 [`xposed/ADAPTATION_GUIDE.md`](../../../../../../../../../ADAPTATION_GUIDE.md)；
> 各系统的实机验证结果记录在仓库根目录的 [`ADAPTATION_PROGRESS.md`](../../../../../../../../../../ADAPTATION_PROGRESS.md)。

目前只有 `AospVendor` 一个适配器（Pixel 实测），所有机型都落到它。

---

## 结构

```
 core-geo（Hook 端与 App 共用，纯 Kotlin）
   VendorProfile ── 设备画像：厂商 / 品牌 / 机型 / 系统属性（反射读取，全程缓存）
   RomRules ─────── 唯一一套识别规则：画像 → RomFamily、系统名与版本号
   HookStatusFiles ─ Hook 运行状态报告的落盘路径

 xposed/hooks/vendor（本目录）
   SystemHookVendor ── 适配器接口；profiles/ 下每个系统一个 object
   VendorRegistry ──── 按 priority 选出命中的适配器，选不到时落 AospVendor
   SystemComponent ─── 需要按系统区分类名的系统服务，以及它所在的进程
   SystemClassLocator ─ 按"当前适配器候选 → AOSP 基线候选"逐层查找组件的实现类，结果记进 HookStatus

 共享 Hook 代码（hooks/SystemXxxHooker.kt）
   SystemClassLocator.locate(组件, …) → 拿到类 → 挂方法
```

---

## 新增一个系统

1. `core-geo/.../vendor/RomFamily.kt`、`RomRules.kt`：家族已经存在就跳过；否则加一个 `RomFamily`，在 `RomRules.familyOf`
   里加识别条件（要读新的系统属性时同步加到 `PROBED_PROPS`），在 `romVersion` 里写版本号的取法。
2. `vendor/profiles/` 下新建适配器 `object`：`matches(profile) = profile.family == family`，`priority` 用正数，
   只覆写与 AOSP 基线不同的部分：
   - `classCandidates(component)`：该系统上的实现类名，`VendorRegistry` 会自动在后面接上 AOSP 基线候选；
   - `removedBaseExemptPackages()`：通用豁免名单里要改为照常模拟的包名（例如原生系统上的 GMS）。
3. 在 `VendorRegistry.ALL` 里注册（`AospVendor` 保持在最后），并更新 `VendorRegistryTest`。
4. 真机验证：装好后重启，打开 App"系统适配 → Hook 运行状态"，确认每个组件都是"已挂载"。
5. 在 `ADAPTATION_PROGRESS.md` 登记结果；"按系统"表格第一列要包含 `RomFamily.progressKeywords` 里的关键字，
   App 才能在"本机状态"里查到这一行。

### 需要按系统区分的是一个新组件

1. `SystemComponent` 加一项，注明所在进程；
2. `AospVendor.classCandidates` 补上它的 AOSP 基线候选（`VendorRegistryTest` 会检查每个组件都有）；
3. 共享 Hook 代码用 `SystemClassLocator.locate(新组件, classLoader, …)` 查找——服务在 APEX 里时传 `threadKeywords` /
   `serviceNames` / `deepScan` / `apexJars` 打开相应的兜底层；通过 `ServiceManager.getService` / `addService` 等途径
   直接拿到服务实例时，调用 `HookStatus.classFound(组件, 实例.javaClass, "途径")` 记录；
4. App 的 `HookStatusScreen.componentName` 与字符串资源里补上显示名。

---

## Hook 运行状态报告

system_server / 电话 / 蓝牙三个进程各自把运行状态写成一份 JSON（路径见 `HookStatusFiles`），
App 通过 root 读取，显示在"系统适配 → Hook 运行状态"页：

- 命中的适配器；
- 每个组件：在哪一层找到了哪个类（`classLoader` / `thread:…` / `ServiceManager[…]` / `LocalServices` / `apex:…` 等），
  还是没找到；
- 每个被挂载的方法实际挂上了几个重载（由 `XposedHelpers.hookAllMethods` 自动记录）——**×0 表示这个系统版本上没有这个方法**，
  通常意味着方法被改名或挪到了别的类；
- 部署过程中抛出的异常。

报告早于本次开机时间时，页面会提示"本次开机模块没有在这个进程里生效"。需要更细的信息时，打开页面底部的
"开机时转储系统服务结构"，重启后各候选类的全部方法和字段会写进 Xposed 日志。

---

## 设计约束

- **适配器无状态**：都是 `object` 单例，会被并发访问，不要持有可变字段；`matches()` 不要抛异常。
- **`AospVendor` 不可删**：`matches` 恒为 `true`、优先级最低，保证任何设备都有基线可用。新 Android 版本导致的原生类名变化
  追加到它的候选里，不要删旧候选。
- **类名只写在适配器里**：共享 Hook 代码不写死系统服务类名，一律通过 `SystemClassLocator` 查找。
- **进程是固定的**：适配器只能改"在哪个类里 Hook"，改不了"在哪个进程里 Hook"。如果某个系统把定位等服务挪到了
  system_server / com.android.phone / com.android.bluetooth 之外的进程，需要同时修改 `SystemProcess`、
  `xposed/src/main/resources/META-INF/xposed/scope.list` 与 `LocationHooker.handleSystemProcess` 的进程判断，
  以及 `HookStatusFiles` 的报告路径。
- **版本判定必须实测**：属性名和取值格式先在目标真机上用 `adb shell getprop` 确认，不要假设。
