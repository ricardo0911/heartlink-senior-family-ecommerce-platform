package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.common.Result;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.HealthProfile;
import com.heartlink.entity.HealthReminder;
import com.heartlink.entity.Order;
import com.heartlink.entity.SmartShelf;
import com.heartlink.entity.User;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.HealthProfileService;
import com.heartlink.service.OrderService;
import com.heartlink.service.ReminderService;
import com.heartlink.service.SmartShelfService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 健康档案控制器
 */
@Tag(name = "健康档案")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final DateTimeFormatter REPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日");

    private final HealthProfileService healthProfileService;
    private final UserService userService;
    private final FamilyBindService familyBindService;
    private final ReminderService reminderService;
    private final SmartShelfService smartShelfService;
    private final OrderService orderService;

    @Operation(summary = "获取长辈健康档案")
    @GetMapping("/{parentId}")
    public Result<HealthProfile> getProfile(@PathVariable Long parentId) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);
        HealthProfile profile = healthProfileService.getOrCreateByParentId(parentId);
        return Result.success(profile);
    }

    @Operation(summary = "保存/更新健康档案(子女端)")
    @PostMapping("/save")
    public Result<HealthProfile> saveProfile(@RequestBody HealthProfile profile) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        if (profile.getParentId() == null) {
            throw new RuntimeException("parentId不能为空");
        }
        assertChildBoundToParent(childId, profile.getParentId());
        HealthProfile saved = healthProfileService.saveOrUpdateProfile(profile);
        return Result.success("保存成功", saved);
    }

    @Operation(summary = "获取我的健康档案(长辈端)")
    @GetMapping("/my")
    public Result<HealthProfile> getMyProfile() {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        HealthProfile profile = healthProfileService.getOrCreateByParentId(parentId);
        return Result.success(profile);
    }

    @Operation(summary = "保存/更新我的健康档案(长辈端)")
    @PostMapping("/my/save")
    public Result<HealthProfile> saveMyProfile(@RequestBody HealthProfile profile) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        profile.setParentId(parentId);
        HealthProfile saved = healthProfileService.saveOrUpdateProfile(profile);
        return Result.success("保存成功", saved);
    }

    @Operation(summary = "获取健康周报")
    @GetMapping("/{parentId}/weekly-report")
    public Result<Map<String, Object>> getWeeklyReport(@PathVariable Long parentId) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);

        HealthProfile profile = healthProfileService.getOrCreateByParentId(parentId);
        User parent = userService.getById(parentId);

        List<HealthReminder> reminders = reminderService.getList(parentId);
        long enabledReminderCount = reminders.stream()
                .filter(item -> Objects.equals(item.getEnabled(), 1))
                .count();

        long likedCount = smartShelfService.count(new LambdaQueryWrapper<SmartShelf>()
                .eq(SmartShelf::getParentId, parentId)
                .eq(SmartShelf::getReaction, "LIKE"));

        long pendingShelfCount = smartShelfService.count(new LambdaQueryWrapper<SmartShelf>()
                .eq(SmartShelf::getParentId, parentId)
                .eq(SmartShelf::getReaction, "PENDING"));

        long paidOrderCount = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getParentId, parentId)
                .in(Order::getStatus, Arrays.asList("PAID", "SHIPPED", "COMPLETED")));

        long pendingPayOrderCount = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getParentId, parentId)
                .eq(Order::getStatus, "PENDING_PAY"));

        String displayName = resolveDisplayName(parent);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("parentId", parentId);
        reportData.put("title", displayName + "本周健康周报");
        reportData.put("period", startDate.format(REPORT_DATE_FORMATTER) + " - " + endDate.format(REPORT_DATE_FORMATTER));
        reportData.put("summary", buildWeeklySummary(displayName, profile, enabledReminderCount, likedCount, pendingShelfCount, paidOrderCount, pendingPayOrderCount));
        reportData.put("stats", buildWeeklyStats(profile, enabledReminderCount, likedCount, paidOrderCount));
        reportData.put("advice", buildWeeklyAdvice(profile, enabledReminderCount, pendingShelfCount, pendingPayOrderCount));
        reportData.put("encourage", buildEncouragement(displayName, likedCount, paidOrderCount, enabledReminderCount));
        reportData.put("generatedAt", java.time.LocalDateTime.now().toString());

        return Result.success(reportData);
    }

    @Operation(summary = "获取健康异常预警")
    @GetMapping("/{parentId}/health-alerts")
    public Result<Map<String, Object>> getHealthAlerts(@PathVariable Long parentId) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);

        HealthProfile profile = healthProfileService.getOrCreateByParentId(parentId);
        User parent = userService.getById(parentId);

        List<HealthReminder> reminders = reminderService.getList(parentId);
        long enabledReminderCount = reminders.stream()
                .filter(item -> Objects.equals(item.getEnabled(), 1))
                .count();

        long pendingShelfCount = smartShelfService.count(new LambdaQueryWrapper<SmartShelf>()
                .eq(SmartShelf::getParentId, parentId)
                .eq(SmartShelf::getReaction, "PENDING"));

        long pendingPayOrderCount = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getParentId, parentId)
                .eq(Order::getStatus, "PENDING_PAY"));

        long paidOrderCount = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getParentId, parentId)
                .in(Order::getStatus, Arrays.asList("PAID", "SHIPPED", "COMPLETED")));

        List<Map<String, Object>> alerts = buildHealthAlerts(
                profile,
                enabledReminderCount,
                pendingShelfCount,
                pendingPayOrderCount,
                paidOrderCount
        );

        long highCount = countAlertsByLevel(alerts, "HIGH");
        long mediumCount = countAlertsByLevel(alerts, "MEDIUM");
        long lowCount = countAlertsByLevel(alerts, "LOW");
        int riskScore = (int) (highCount * 3 + mediumCount * 2 + lowCount);
        String displayName = resolveDisplayName(parent);

        Map<String, Object> data = new HashMap<>();
        data.put("parentId", parentId);
        data.put("displayName", displayName);
        data.put("riskLevel", resolveRiskLevel(highCount, mediumCount, lowCount));
        data.put("riskScore", riskScore);
        data.put("alertCount", alerts.size());
        data.put("summary", buildHealthAlertSummary(displayName, highCount, mediumCount, lowCount, alerts.size()));
        data.put("stats", buildHealthAlertStats(highCount, mediumCount, lowCount));
        data.put("alerts", alerts);
        data.put("generatedAt", java.time.LocalDateTime.now().toString());
        return Result.success(data);
    }

    private String buildWeeklySummary(String displayName,
                                      HealthProfile profile,
                                      long enabledReminderCount,
                                      long likedCount,
                                      long pendingShelfCount,
                                      long paidOrderCount,
                                      long pendingPayOrderCount) {
        List<String> fragments = new ArrayList<>();

        int chronicCount = safeSize(profile.getChronicDiseases());
        if (chronicCount > 0) {
            fragments.add("已记录" + chronicCount + "项重点健康关注");
        } else {
            fragments.add("健康档案已建立");
        }

        if (enabledReminderCount > 0) {
            fragments.add("当前启用" + enabledReminderCount + "条日常提醒");
        }

        if (likedCount > 0) {
            fragments.add("有" + likedCount + "件商品获得正向反馈");
        } else if (pendingShelfCount > 0) {
            fragments.add("仍有" + pendingShelfCount + "件推荐商品等待查看");
        }

        if (paidOrderCount > 0) {
            fragments.add("已完成" + paidOrderCount + "笔相关订单");
        } else if (pendingPayOrderCount > 0) {
            fragments.add("当前有" + pendingPayOrderCount + "笔订单待处理");
        }

        return displayName + "本周状态总体稳定，" + String.join("，", fragments) + "。";
    }

    private List<Map<String, Object>> buildWeeklyStats(HealthProfile profile,
                                                       long enabledReminderCount,
                                                       long likedCount,
                                                       long paidOrderCount) {
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(buildStatItem("慢病关注", safeSize(profile.getChronicDiseases()) + "项"));
        stats.add(buildStatItem("已设提醒", enabledReminderCount + "条"));
        stats.add(buildStatItem("喜欢商品", likedCount + "件"));
        stats.add(buildStatItem("已支付订单", paidOrderCount + "单"));
        return stats;
    }

    private List<String> buildWeeklyAdvice(HealthProfile profile,
                                           long enabledReminderCount,
                                           long pendingShelfCount,
                                           long pendingPayOrderCount) {
        List<String> advice = new ArrayList<>();
        List<String> chronicDiseases = profile.getChronicDiseases();

        if (containsDisease(chronicDiseases, "高血压")) {
            advice.add("建议继续关注控盐与规律作息，选购商品时优先考虑低钠、清淡类产品。");
        }
        if (containsDisease(chronicDiseases, "糖尿病") || containsDisease(chronicDiseases, "高血糖")) {
            advice.add("建议重点关注配料表中的糖分信息，优先选择无糖或低糖商品。");
        }
        if (containsDisease(chronicDiseases, "高血脂")) {
            advice.add("建议优先选择低脂、少油类商品，减少高脂零食和油炸食品。");
        }
        if (safeSize(profile.getAllergies()) > 0) {
            advice.add("已记录过敏信息，购买前建议再次核对成分表，尽量避开高风险原料。");
        }
        if (enabledReminderCount == 0) {
            advice.add("当前还没有启用健康提醒，建议补充用药、测量或作息提醒，方便日常照护。");
        }
        if (pendingShelfCount > 0) {
            advice.add("仍有待查看的推荐商品，建议与长辈及时沟通使用反馈，便于后续选购。");
        }
        if (pendingPayOrderCount > 0) {
            advice.add("存在待处理订单，建议尽快确认支付或取消，避免影响后续收货安排。");
        }
        if (profile.getAge() == null || profile.getHeight() == null || profile.getWeight() == null) {
            advice.add("健康档案中的基础身体信息还可继续完善，以便后续推荐分析更加准确。");
        }

        if (advice.isEmpty()) {
            advice.add("本周数据较为平稳，建议继续保持规律作息，并结合健康档案进行理性选购。");
        }

        return advice.size() > 4 ? advice.subList(0, 4) : advice;
    }

    private List<Map<String, Object>> buildHealthAlerts(HealthProfile profile,
                                                        long enabledReminderCount,
                                                        long pendingShelfCount,
                                                        long pendingPayOrderCount,
                                                        long paidOrderCount) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        List<String> chronicDiseases = profile.getChronicDiseases();

        double bmi = calculateBmi(profile.getHeight(), profile.getWeight());
        if (bmi > 0 && bmi < 18.5d) {
            alerts.add(buildHealthAlertItem(
                    "HIGH",
                    "体重偏轻，需要关注营养",
                    "当前 BMI 约为 " + formatOneDecimal(bmi) + "，建议优先关注日常饮食、蛋白质摄入和基础体重变化。",
                    "PROFILE",
                    "查看健康档案"
            ));
        } else if (bmi >= 28d) {
            alerts.add(buildHealthAlertItem(
                    "HIGH",
                    "体重偏高，建议尽快调整照护策略",
                    "当前 BMI 约为 " + formatOneDecimal(bmi) + "，建议结合清淡饮食、活动安排和健康商品选择进行跟进。",
                    "PROFILE",
                    "查看健康档案"
            ));
        } else if (bmi >= 24d) {
            alerts.add(buildHealthAlertItem(
                    "MEDIUM",
                    "体重已超出理想范围",
                    "当前 BMI 约为 " + formatOneDecimal(bmi) + "，建议留意饮食结构，并持续观察体重变化趋势。",
                    "REPORT",
                    "查看周报"
            ));
        }

        int chronicCount = safeSize(chronicDiseases);
        if (chronicCount > 0 && enabledReminderCount == 0) {
            alerts.add(buildHealthAlertItem(
                    "HIGH",
                    "慢病管理缺少提醒",
                    "已记录 " + chronicCount + " 项慢病或重点关注，但当前没有启用健康提醒，容易出现日常照护遗漏。",
                    "REMINDER",
                    "去设置提醒"
            ));
        } else if (chronicCount >= 3) {
            alerts.add(buildHealthAlertItem(
                    "MEDIUM",
                    "存在多项慢病，需要持续跟进",
                    "当前已记录 " + chronicCount + " 项长期健康关注点，建议固定节奏查看周报并保持提醒启用。",
                    "REPORT",
                    "查看周报"
            ));
        }

        if (profile.getAge() != null && profile.getAge() >= 75 && enabledReminderCount == 0) {
            alerts.add(buildHealthAlertItem(
                    "MEDIUM",
                    "高龄照护尚未建立提醒节奏",
                    "当前年龄已进入重点照护阶段，但未配置提醒，建议补充用药、作息或复查提醒。",
                    "REMINDER",
                    "补充提醒"
            ));
        }

        if (safeSize(profile.getAllergies()) > 0) {
            alerts.add(buildHealthAlertItem(
                    "MEDIUM",
                    "已记录过敏信息，选品前需再次确认",
                    "当前档案包含过敏史，购买食品、保健品或护理用品前建议再次核对配料和材质。",
                    "PROFILE",
                    "核对档案"
            ));
        }

        int missingProfileCount = countMissingProfileFields(profile);
        if (missingProfileCount >= 2) {
            alerts.add(buildHealthAlertItem(
                    "LOW",
                    "健康档案仍不完整",
                    "基础信息缺少 " + missingProfileCount + " 项，可能影响后续周报分析和推荐准确度，建议尽快补齐。",
                    "PROFILE",
                    "完善档案"
            ));
        }

        if (pendingPayOrderCount > 0) {
            alerts.add(buildHealthAlertItem(
                    "LOW",
                    "存在待处理订单",
                    "当前有 " + pendingPayOrderCount + " 笔订单待确认，建议尽快处理，避免影响收货和后续照护安排。",
                    "ORDER",
                    "查看订单"
            ));
        }

        if (pendingShelfCount > 1) {
            alerts.add(buildHealthAlertItem(
                    "LOW",
                    "推荐商品仍待反馈",
                    "当前有 " + pendingShelfCount + " 件已推荐商品未得到反馈，建议和长辈确认使用意愿，提升后续推荐准确性。",
                    "SHELF",
                    "查看心选"
            ));
        }

        if (paidOrderCount == 0 && chronicCount > 0 && pendingShelfCount == 0) {
            alerts.add(buildHealthAlertItem(
                    "LOW",
                    "照护动作偏少，建议补充主动跟进",
                    "已有健康关注点，但最近缺少订单和商品反馈记录，可以结合周报安排一次主动回访。",
                    "REPORT",
                    "查看周报"
            ));
        }

        return alerts;
    }

    private String buildEncouragement(String displayName,
                                      long likedCount,
                                      long paidOrderCount,
                                      long enabledReminderCount) {
        if (likedCount > 0 && paidOrderCount > 0) {
            return "本周已经形成从推荐、反馈到下单的完整协同流程，继续保持这样的沟通节奏会更有助于家庭共同决策。";
        }
        if (enabledReminderCount > 0) {
            return displayName + "的日常照护节奏已经逐步建立，坚持小步积累，会让后续的健康管理更省心。";
        }
        return "本周已完成基础信息整理，后续只要继续补充提醒与反馈记录，系统的协同效果会越来越稳定。";
    }

    private Map<String, Object> buildStatItem(String label, Object value) {
        Map<String, Object> item = new HashMap<>();
        item.put("label", label);
        item.put("value", value);
        return item;
    }

    private Map<String, Object> buildHealthAlertItem(String level,
                                                     String title,
                                                     String description,
                                                     String actionType,
                                                     String actionLabel) {
        Map<String, Object> item = new HashMap<>();
        item.put("level", level);
        item.put("title", title);
        item.put("description", description);
        item.put("actionType", actionType);
        item.put("actionLabel", actionLabel);
        return item;
    }

    private Map<String, Object> buildHealthAlertStats(long highCount, long mediumCount, long lowCount) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("high", highCount);
        stats.put("medium", mediumCount);
        stats.put("low", lowCount);
        return stats;
    }

    private String buildHealthAlertSummary(String displayName,
                                           long highCount,
                                           long mediumCount,
                                           long lowCount,
                                           int totalCount) {
        if (totalCount == 0) {
            return displayName + " 当前没有明显异常预警，建议继续保持提醒和档案更新节奏。";
        }
        if (highCount > 0) {
            return displayName + " 当前识别到 " + totalCount + " 项需关注问题，其中高优先级 " + highCount + " 项，建议优先处理提醒缺失或身体指标异常。";
        }
        if (mediumCount > 0) {
            return displayName + " 当前有 " + totalCount + " 项照护关注点，建议本周内完成档案核对、提醒补充或过敏信息复查。";
        }
        return displayName + " 当前有 " + lowCount + " 项待跟进事项，建议结合周报逐项处理，避免问题积压。";
    }

    private String resolveRiskLevel(long highCount, long mediumCount, long lowCount) {
        if (highCount > 0) {
            return "HIGH";
        }
        if (mediumCount > 0) {
            return "MEDIUM";
        }
        if (lowCount > 0) {
            return "LOW";
        }
        return "STABLE";
    }

    private long countAlertsByLevel(List<Map<String, Object>> alerts, String level) {
        return alerts.stream()
                .filter(item -> level.equals(item.get("level")))
                .count();
    }

    private int countMissingProfileFields(HealthProfile profile) {
        int count = 0;
        if (profile.getAge() == null) {
            count++;
        }
        if (profile.getHeight() == null) {
            count++;
        }
        if (profile.getWeight() == null) {
            count++;
        }
        if (safeSize(profile.getChronicDiseases()) == 0) {
            count++;
        }
        return count;
    }

    private double calculateBmi(Double height, Double weight) {
        if (height == null || weight == null || height <= 0 || weight <= 0) {
            return 0d;
        }
        double meters = height / 100d;
        if (meters <= 0d) {
            return 0d;
        }
        return weight / (meters * meters);
    }

    private String formatOneDecimal(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private String resolveDisplayName(User parent) {
        if (parent == null) {
            return "家人";
        }
        if (parent.getNickname() != null && !parent.getNickname().isBlank()) {
            return parent.getNickname().trim();
        }
        if (parent.getPhone() != null && !parent.getPhone().isBlank()) {
            return parent.getPhone().trim();
        }
        return "家人";
    }

    private boolean containsDisease(List<String> diseases, String expected) {
        if (diseases == null || diseases.isEmpty()) {
            return false;
        }
        return diseases.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(expected::equals);
    }

    private int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("无权限访问该接口");
        }
    }

    private void assertChildBoundToParent(Long childId, Long parentId) {
        List<FamilyBind> binds = familyBindService.getParentsByChildId(childId);
        boolean bound = binds.stream().anyMatch(bind -> parentId.equals(bind.getParentId()));
        if (!bound) {
            throw new RuntimeException("未绑定该长辈，无法操作健康档案");
        }
    }
}
