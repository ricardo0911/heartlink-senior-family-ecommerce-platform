package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.entity.User;
import com.heartlink.service.MemberCheckInService;
import com.heartlink.service.MemberPointsConfigService;
import com.heartlink.service.MemberPointsService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "会员积分")
@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberPointsController {

    private final MemberPointsService memberPointsService;
    private final MemberCheckInService memberCheckInService;
    private final MemberPointsConfigService memberPointsConfigService;
    private final UserService userService;

    @Operation(summary = "获取会员信息(子女端)")
    @GetMapping("/info")
    public Result<Map<String, Object>> getMemberInfo() {
        User currentUser = getCurrentUser();
        if (!isChildUser(currentUser)) {
            return memberAccessDenied();
        }

        User user = memberPointsService.getPointsInfo(currentUser.getId());
        int level = user.getMemberLevel() != null ? user.getMemberLevel() : 0;
        int points = user.getPoints() != null ? user.getPoints() : 0;

        Map<String, Object> info = new HashMap<>();
        info.put("points", points);
        info.put("level", level);
        info.put("levelName", memberPointsService.getMemberLevelName(user.getMemberLevel()));
        info.put("discount", resolveDiscountText(level));
        info.put("dailyCheckInPoints", memberPointsConfigService.getDailyCheckInPoints());
        info.put("checkedInToday", memberCheckInService.hasCheckedInToday(currentUser.getId()));

        int nextLevelPoints = 0;
        String nextLevelName = "";
        if (level == 0) {
            nextLevelPoints = 1000 - points;
            nextLevelName = "银卡会员";
        } else if (level == 1) {
            nextLevelPoints = 5000 - points;
            nextLevelName = "金卡会员";
        } else if (level == 2) {
            nextLevelPoints = 20000 - points;
            nextLevelName = "钻石会员";
        }
        info.put("nextLevelPoints", Math.max(0, nextLevelPoints));
        info.put("nextLevelName", nextLevelName);

        return Result.success(info);
    }

    @Operation(summary = "计算商品折扣价(子女端)")
    @GetMapping("/calculate-discount")
    public Result<Map<String, Object>> calculateDiscount(@RequestParam BigDecimal price) {
        User currentUser = getCurrentUser();
        if (!isChildUser(currentUser)) {
            return memberAccessDenied();
        }

        BigDecimal discountPrice = memberPointsService.calculateDiscount(currentUser.getId(), price);
        BigDecimal savedAmount = price.subtract(discountPrice);

        Map<String, Object> result = new HashMap<>();
        result.put("originalPrice", price);
        result.put("discountPrice", discountPrice);
        result.put("savedAmount", savedAmount);
        return Result.success(result);
    }

    @Operation(summary = "使用积分抵扣(子女端)")
    @PostMapping("/use-points")
    public Result<Map<String, Object>> usePoints(@RequestParam Integer points) {
        User currentUser = getCurrentUser();
        if (!isChildUser(currentUser)) {
            return memberAccessDenied();
        }

        User user = memberPointsService.getPointsInfo(currentUser.getId());
        if (user.getPoints() == null || user.getPoints() < points) {
            return Result.error("积分不足");
        }

        BigDecimal discount = memberPointsService.usePointsForDiscount(currentUser.getId(), points);
        User latestUser = memberPointsService.getPointsInfo(currentUser.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("usedPoints", points);
        result.put("discountAmount", discount);
        result.put("remainingPoints", latestUser.getPoints());
        return Result.success("积分抵扣成功", result);
    }

    @Operation(summary = "每日签到获取积分(子女端)")
    @PostMapping("/check-in")
    public Result<Map<String, Object>> checkIn() {
        User currentUser = getCurrentUser();
        if (!isChildUser(currentUser)) {
            return memberAccessDenied();
        }

        int earnedPoints = memberPointsConfigService.getDailyCheckInPoints();
        memberCheckInService.performDailyCheckIn(currentUser.getId(), earnedPoints);
        User user = memberPointsService.getPointsInfo(currentUser.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("earnedPoints", earnedPoints);
        result.put("totalPoints", user.getPoints());
        result.put("levelName", memberPointsService.getMemberLevelName(user.getMemberLevel()));
        result.put("checkedInToday", true);
        return Result.success("签到成功，获得 " + earnedPoints + " 积分", result);
    }

    private String resolveDiscountText(int level) {
        switch (level) {
            case 1:
                return "98折";
            case 2:
                return "95折";
            case 3:
                return "9折";
            default:
                return "无折扣";
        }
    }

    private User getCurrentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        return userService.getById(userId);
    }

    private boolean isChildUser(User user) {
        return user != null && "CHILD".equalsIgnoreCase(user.getRole());
    }

    private <T> Result<T> memberAccessDenied() {
        return Result.error(403, "仅子女端可使用会员功能");
    }
}
