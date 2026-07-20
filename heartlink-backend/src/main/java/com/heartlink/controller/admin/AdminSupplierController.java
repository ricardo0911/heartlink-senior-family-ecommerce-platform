package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heartlink.common.Result;
import com.heartlink.entity.Supplier;
import com.heartlink.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Tag(name = "Admin Supplier")
@RestController
@RequestMapping("/api/admin/supplier")
@RequiredArgsConstructor
public class AdminSupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "Supplier list")
    @GetMapping("/list")
    public Result<List<Supplier>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Supplier::getStatus, status);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Supplier::getType, type.trim());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(Supplier::getName, keyword.trim())
                    .or()
                    .like(Supplier::getCode, keyword.trim()));
        }
        wrapper.orderByDesc(Supplier::getCreatedAt);
        return Result.success(supplierService.list(wrapper));
    }

    @Operation(summary = "Supplier detail")
    @GetMapping("/{id}")
    public Result<Supplier> getById(@PathVariable Long id) {
        return Result.success(supplierService.getById(id));
    }

    @Operation(summary = "Save supplier")
    @PostMapping("/save")
    public Result<Supplier> save(@RequestBody Supplier supplier) {
        Supplier normalized = normalizeSupplier(supplier);
        validateSupplier(normalized);
        if (normalized.getId() != null) {
            supplierService.updateById(normalized);
        } else {
            supplierService.save(normalized);
        }
        return Result.success(normalized);
    }

    @Operation(summary = "Update supplier status")
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setStatus(status);
        supplierService.updateById(supplier);
        return Result.success(null);
    }

    @Operation(summary = "Delete supplier")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.removeById(id);
        return Result.success(null);
    }

    private Supplier normalizeSupplier(Supplier supplier) {
        if (supplier == null) {
            throw new RuntimeException("supplier is required");
        }
        supplier.setName(trimToNull(supplier.getName()));
        supplier.setCode(trimToNull(supplier.getCode()));
        supplier.setType(defaultIfBlank(supplier.getType(), "CURATED_SUPPLIER"));
        supplier.setContactName(trimToNull(supplier.getContactName()));
        supplier.setContactPhone(trimToNull(supplier.getContactPhone()));
        supplier.setSettlementMode(defaultIfBlank(supplier.getSettlementMode(), "MANUAL"));
        supplier.setDefaultTaxRate(normalizePercentage(supplier.getDefaultTaxRate()));
        supplier.setApiConfigJson(trimToNull(supplier.getApiConfigJson()));
        supplier.setRemark(trimToNull(supplier.getRemark()));
        if (supplier.getStatus() == null) {
            supplier.setStatus(1);
        }
        return supplier;
    }

    private void validateSupplier(Supplier supplier) {
        if (!StringUtils.hasText(supplier.getName())) {
            throw new RuntimeException("supplier name is required");
        }
        if (!StringUtils.hasText(supplier.getCode())) {
            throw new RuntimeException("supplier code is required");
        }
        validatePercentage(supplier.getDefaultTaxRate(), "default tax rate");

        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCode, supplier.getCode());
        if (supplier.getId() != null) {
            wrapper.ne(Supplier::getId, supplier.getId());
        }
        if (supplierService.count(wrapper) > 0) {
            throw new RuntimeException("supplier code already exists");
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal normalizePercentage(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePercentage(BigDecimal value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException(fieldName + " must be between 0 and 100");
        }
    }
}
