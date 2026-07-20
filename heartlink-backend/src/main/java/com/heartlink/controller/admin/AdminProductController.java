package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.entity.Product;
import com.heartlink.service.CategoryService;
import com.heartlink.service.ProductService;
import com.heartlink.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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

import java.net.URI;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Tag(name = "Admin Product")
@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SupplierService supplierService;

    @Operation(summary = "Product page")
    @GetMapping("/page")
    public Result<IPage<Product>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplyType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (supplierId != null) {
            wrapper.eq(Product::getSupplierId, supplierId);
        }
        applySupplyTypeFilter(wrapper, supplyType);
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Product::getName, keyword.trim());
        }
        wrapper.orderByDesc(Product::getCreatedAt);
        return Result.success(productService.page(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "Get product")
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @Operation(summary = "Save product")
    @PostMapping("/save")
    public Result<Product> save(@RequestBody Product product, HttpServletRequest request) {
        Product normalized = normalizeProduct(product, request);
        validateProductConfiguration(normalized);
        if (normalized.getSupplierId() != null && supplierService.getById(normalized.getSupplierId()) == null) {
            throw new RuntimeException("supplier not found");
        }

        if (normalized.getId() != null) {
            productService.updateById(normalized);
        } else {
            productService.save(normalized);
        }
        return Result.success(normalized);
    }

    @Operation(summary = "Update product status")
    @PostMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        productService.updateById(product);
        return Result.success(null);
    }

    @Operation(summary = "Delete product")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success(null);
    }

    private Product normalizeProduct(Product product, HttpServletRequest request) {
        if (product == null) {
            throw new RuntimeException("product is required");
        }

        product.setSourceType(resolveSourceType(product));
        product.setExternalSkuId(trimToNull(product.getExternalSkuId()));
        product.setSourceStatus(defaultIfBlank(product.getSourceStatus(), "ACTIVE"));
        product.setDeliveryMode(resolveDeliveryMode(product));
        product.setTaxRateMode(resolveTaxRateMode(product));
        product.setTaxRate(normalizePercentage(product.getTaxRate()));
        product.setImages(normalizeImageList(product.getImages(), request));

        if (isSelfOperated(product)) {
            product.setSupplierId(null);
            product.setTaxRateMode(null);
            product.setTaxRate(null);
            product.setGrossMargin(calculateGrossMargin(product));
        } else {
            product.setGrossMargin(null);
        }
        return product;
    }

    private String resolveSourceType(Product product) {
        String sourceType = trimToNull(product.getSourceType());
        if (sourceType != null) {
            return sourceType;
        }
        return product.getSupplierId() == null ? "SELF_OPERATED" : "CURATED_SUPPLIER";
    }

    private String resolveDeliveryMode(Product product) {
        String deliveryMode = trimToNull(product.getDeliveryMode());
        if (deliveryMode != null) {
            return deliveryMode;
        }
        return product.getSupplierId() == null ? "PLATFORM_SHIP" : "PLATFORM_FORWARD";
    }

    private String resolveTaxRateMode(Product product) {
        String taxRateMode = trimToNull(product.getTaxRateMode());
        if (taxRateMode != null) {
            return taxRateMode;
        }
        return isSelfOperated(product) ? null : "INHERIT_SUPPLIER";
    }

    private BigDecimal calculateGrossMargin(Product product) {
        if (product.getPrice() == null || product.getPurchasePrice() == null) {
            return null;
        }
        return product.getPrice()
                .subtract(product.getPurchasePrice())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateProductConfiguration(Product product) {
        validatePercentage(product.getTaxRate(), "tax rate");
        if (isSelfOperated(product)) {
            return;
        }

        if (product.getSupplierId() == null) {
            throw new RuntimeException("third-party product must bind a supplier");
        }
        if ("CUSTOM".equalsIgnoreCase(product.getTaxRateMode()) && product.getTaxRate() == null) {
            throw new RuntimeException("custom tax rate is required");
        }
        if (!"CUSTOM".equalsIgnoreCase(product.getTaxRateMode())
                && !"INHERIT_SUPPLIER".equalsIgnoreCase(product.getTaxRateMode())) {
            throw new RuntimeException("invalid tax rate mode");
        }
    }

    private boolean isSelfOperated(Product product) {
        return "SELF_OPERATED".equalsIgnoreCase(product.getSourceType());
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

    private List<String> normalizeImageList(List<String> images, HttpServletRequest request) {
        if (images == null) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String image : images) {
            String candidate = normalizeImageValue(image, request);
            if (StringUtils.hasText(candidate)) {
                normalized.add(candidate);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeImageValue(String value, HttpServletRequest request) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("/upload/")) {
            return normalized;
        }
        if (normalized.startsWith("upload/")) {
            return "/" + normalized;
        }
        if (!normalized.regionMatches(true, 0, "http://", 0, 7)
                && !normalized.regionMatches(true, 0, "https://", 0, 8)) {
            return normalized;
        }

        try {
            URI uri = URI.create(normalized);
            String path = trimToNull(uri.getPath());
            if (path == null) {
                return normalized;
            }
            if (path.startsWith("upload/")) {
                path = "/" + path;
            }
            if (!path.startsWith("/upload/") || !isSameRequestHost(uri, request)) {
                return normalized;
            }
            return path + buildQuerySuffix(uri);
        } catch (IllegalArgumentException ignored) {
            return normalized;
        }
    }

    private boolean isSameRequestHost(URI uri, HttpServletRequest request) {
        String requestHost = resolveRequestHost(request);
        String uriHost = resolveUriHost(uri);
        return StringUtils.hasText(requestHost)
                && StringUtils.hasText(uriHost)
                && requestHost.equalsIgnoreCase(uriHost);
    }

    private String resolveRequestHost(HttpServletRequest request) {
        String forwardedHost = firstHeaderValue(request == null ? null : request.getHeader("X-Forwarded-Host"));
        if (StringUtils.hasText(forwardedHost)) {
            return forwardedHost.toLowerCase(Locale.ROOT);
        }
        if (request == null || !StringUtils.hasText(request.getServerName())) {
            return null;
        }
        String scheme = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        if (!defaultPort && port > 0) {
            host = host + ":" + port;
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private String resolveUriHost(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getHost())) {
            return null;
        }
        String host = uri.getHost();
        int port = uri.getPort();
        boolean defaultPort = ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
        if (!defaultPort && port > 0) {
            host = host + ":" + port;
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private String buildQuerySuffix(URI uri) {
        return StringUtils.hasText(uri.getQuery()) ? "?" + uri.getQuery() : "";
    }

    private String firstHeaderValue(String header) {
        if (!StringUtils.hasText(header)) {
            return "";
        }
        int commaIndex = header.indexOf(',');
        return commaIndex >= 0 ? header.substring(0, commaIndex).trim() : header.trim();
    }

    private void applySupplyTypeFilter(LambdaQueryWrapper<Product> wrapper, String supplyType) {
        String normalized = trimToNull(supplyType);
        if (normalized == null) {
            return;
        }
        if ("SELF_OPERATED".equalsIgnoreCase(normalized)) {
            wrapper.and(item -> item.eq(Product::getSourceType, "SELF_OPERATED")
                    .or()
                    .isNull(Product::getSupplierId));
            return;
        }
        if ("THIRD_PARTY".equalsIgnoreCase(normalized)) {
            wrapper.and(item -> item.ne(Product::getSourceType, "SELF_OPERATED")
                    .or()
                    .isNotNull(Product::getSupplierId));
            return;
        }
        wrapper.eq(Product::getSourceType, normalized);
    }
}
