package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.service.SettlementMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin Settlement")
@RestController
@RequestMapping("/api/admin/settlement")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementMetricsService settlementMetricsService;

    @Operation(summary = "Settlement overview")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) LocalDate startDate,
                                                @RequestParam(required = false) LocalDate endDate,
                                                @RequestParam(required = false) String sourceType,
                                                @RequestParam(required = false) Long supplierId) {
        return Result.success(settlementMetricsService.buildOverview(startDate, endDate, sourceType, supplierId));
    }

    @Operation(summary = "Settlement trend")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(required = false) LocalDate startDate,
                                                   @RequestParam(required = false) LocalDate endDate,
                                                   @RequestParam(defaultValue = "day") String groupBy,
                                                   @RequestParam(required = false) String sourceType,
                                                   @RequestParam(required = false) Long supplierId) {
        return Result.success(settlementMetricsService.buildTrend(startDate, endDate, groupBy, sourceType, supplierId));
    }

    @Operation(summary = "Settlement order page")
    @GetMapping("/orders")
    public Result<IPage<Map<String, Object>>> orders(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) LocalDate startDate,
                                                     @RequestParam(required = false) LocalDate endDate,
                                                     @RequestParam(required = false) String sourceType,
                                                     @RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Boolean refundOnly) {
        return Result.success(settlementMetricsService.pageOrders(
                page,
                size,
                startDate,
                endDate,
                sourceType,
                supplierId,
                keyword,
                refundOnly
        ));
    }
}
