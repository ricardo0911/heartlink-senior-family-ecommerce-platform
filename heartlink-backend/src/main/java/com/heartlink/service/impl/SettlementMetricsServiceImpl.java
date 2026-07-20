package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.entity.Order;
import com.heartlink.entity.Product;
import com.heartlink.entity.Supplier;
import com.heartlink.service.OrderService;
import com.heartlink.service.ProductService;
import com.heartlink.service.SettlementMetricsService;
import com.heartlink.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SettlementMetricsServiceImpl implements SettlementMetricsService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String SOURCE_SELF = "SELF_OPERATED";
    private static final String SOURCE_CUSTOM_TAX = "PRODUCT_CUSTOM";
    private static final String SOURCE_SUPPLIER_TAX = "SUPPLIER_DEFAULT";

    private final OrderService orderService;
    private final ProductService productService;
    private final SupplierService supplierService;

    @Override
    public Map<String, Object> buildOverview(LocalDate startDate, LocalDate endDate, String sourceType, Long supplierId) {
        List<SettlementOrderRecord> records = buildRecords(startDate, endDate, sourceType, supplierId, null, false);

        BigDecimal thirdPartyIncome = ZERO;
        BigDecimal selfOperatedSales = ZERO;
        BigDecimal selfOperatedGrossProfit = ZERO;
        BigDecimal refundReversalAmount = ZERO;
        BigDecimal netBusinessIncome = ZERO;
        int missingCostOrderCount = 0;
        int missingTaxRateOrderCount = 0;

        for (SettlementOrderRecord record : records) {
            thirdPartyIncome = thirdPartyIncome.add(record.positiveThirdPartyIncome);
            selfOperatedSales = selfOperatedSales.add(record.positiveSelfSales);
            selfOperatedGrossProfit = selfOperatedGrossProfit.add(record.positiveSelfGrossProfit);
            refundReversalAmount = refundReversalAmount.add(record.refundReversalAmount.abs());
            netBusinessIncome = netBusinessIncome.add(record.netBusinessIncome);
            if (record.missingCostData) {
                missingCostOrderCount++;
            }
            if (record.missingTaxRateData) {
                missingTaxRateOrderCount++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thirdPartyIncome", normalizeMoney(thirdPartyIncome));
        data.put("selfOperatedSales", normalizeMoney(selfOperatedSales));
        data.put("selfOperatedGrossProfit", normalizeMoney(selfOperatedGrossProfit));
        data.put("refundReversalAmount", normalizeMoney(refundReversalAmount));
        data.put("netBusinessIncome", normalizeMoney(netBusinessIncome));
        data.put("missingCostOrderCount", missingCostOrderCount);
        data.put("missingTaxRateOrderCount", missingTaxRateOrderCount);
        data.put("orderCount", records.size());
        return data;
    }

    @Override
    public List<Map<String, Object>> buildTrend(LocalDate startDate,
                                                LocalDate endDate,
                                                String groupBy,
                                                String sourceType,
                                                Long supplierId) {
        LocalDate safeEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate safeStart = startDate == null ? safeEnd.minusDays(6) : startDate;
        String normalizedGroupBy = normalizeGroupBy(groupBy);

        List<SettlementOrderRecord> records = buildRecords(safeStart, safeEnd, sourceType, supplierId, null, false);
        Map<String, TrendBucket> buckets = initTrendBuckets(safeStart, safeEnd, normalizedGroupBy);

        for (SettlementOrderRecord record : records) {
            if (record.completedAt != null && isBetween(record.completedAt.toLocalDate(), safeStart, safeEnd)) {
                TrendBucket bucket = buckets.get(formatBucketKey(record.completedAt.toLocalDate(), normalizedGroupBy));
                if (bucket != null) {
                    bucket.thirdPartyIncome = bucket.thirdPartyIncome.add(record.positiveThirdPartyIncome);
                    bucket.selfOperatedSales = bucket.selfOperatedSales.add(record.positiveSelfSales);
                    bucket.selfOperatedGrossProfit = bucket.selfOperatedGrossProfit.add(record.positiveSelfGrossProfit);
                    bucket.orderCount++;
                }
            }
            if (record.refundAt != null && isBetween(record.refundAt.toLocalDate(), safeStart, safeEnd)) {
                TrendBucket bucket = buckets.get(formatBucketKey(record.refundAt.toLocalDate(), normalizedGroupBy));
                if (bucket != null) {
                    bucket.refundReversalAmount = bucket.refundReversalAmount.add(record.refundReversalAmount.abs());
                }
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, TrendBucket> entry : buckets.entrySet()) {
            TrendBucket bucket = entry.getValue();
            BigDecimal netBusinessIncome = bucket.thirdPartyIncome
                    .add(bucket.selfOperatedGrossProfit)
                    .subtract(bucket.refundReversalAmount);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey());
            item.put("thirdPartyIncome", normalizeMoney(bucket.thirdPartyIncome));
            item.put("selfOperatedSales", normalizeMoney(bucket.selfOperatedSales));
            item.put("selfOperatedGrossProfit", normalizeMoney(bucket.selfOperatedGrossProfit));
            item.put("refundReversalAmount", normalizeMoney(bucket.refundReversalAmount));
            item.put("netBusinessIncome", normalizeMoney(netBusinessIncome));
            item.put("orderCount", bucket.orderCount);
            trend.add(item);
        }
        return trend;
    }

    @Override
    public IPage<Map<String, Object>> pageOrders(int page,
                                                 int size,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 String sourceType,
                                                 Long supplierId,
                                                 String keyword,
                                                 Boolean refundOnly) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);

        List<SettlementOrderRecord> records = buildRecords(startDate, endDate, sourceType, supplierId, keyword, Boolean.TRUE.equals(refundOnly));
        records.sort(Comparator
                .comparing(SettlementOrderRecord::sortAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SettlementOrderRecord::id, Comparator.nullsLast(Comparator.reverseOrder())));

        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SettlementOrderRecord record : records.subList(fromIndex, toIndex)) {
            rows.add(toOrderRow(record));
        }

        Page<Map<String, Object>> result = new Page<>(safePage, safeSize);
        result.setTotal(records.size());
        result.setRecords(rows);
        return result;
    }

    private List<SettlementOrderRecord> buildRecords(LocalDate startDate,
                                                     LocalDate endDate,
                                                     String sourceType,
                                                     Long supplierId,
                                                     String keyword,
                                                     boolean refundOnly) {
        List<Order> orders = orderService.list();
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productMap = loadProducts(orders);
        Map<Long, Supplier> supplierMap = loadSuppliers(orders, productMap);
        List<SettlementOrderRecord> records = new ArrayList<>();

        for (Order order : orders) {
            SettlementContext context = resolveContext(order, productMap, supplierMap);
            if (!matchesSourceFilter(context.sourceType, sourceType)) {
                continue;
            }
            if (supplierId != null && !supplierId.equals(order.getSupplierId())) {
                continue;
            }
            if (!matchesKeyword(order, keyword)) {
                continue;
            }

            SettlementOrderRecord record = buildRecord(order, context, startDate, endDate);
            if (record == null) {
                continue;
            }
            if (refundOnly && record.refundReversalAmount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            records.add(record);
        }

        return records;
    }

    private Map<String, Object> toOrderRow(SettlementOrderRecord record) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", record.id);
        item.put("orderNo", record.orderNo);
        item.put("productName", record.productName);
        item.put("sourceType", record.sourceType);
        item.put("sourceTypeLabel", sourceTypeLabel(record.sourceType));
        item.put("supplierName", record.supplierName);
        item.put("status", record.status);
        item.put("refundStatus", record.refundStatus);
        item.put("quantity", record.quantity);
        item.put("totalAmount", normalizeMoney(record.totalAmount));
        item.put("completedAt", record.completedAt);
        item.put("refundAt", record.refundAt);
        item.put("taxRate", record.taxRate);
        item.put("purchasePrice", record.purchasePrice);
        item.put("taxSource", record.taxSource);
        item.put("taxSourceLabel", taxSourceLabel(record.taxSource));
        item.put("positiveIncome", normalizeMoney(record.positiveIncome));
        item.put("reversalIncome", normalizeMoney(record.reversalIncome));
        item.put("netIncome", normalizeMoney(record.netBusinessIncome));
        item.put("positiveSales", normalizeMoney(record.positiveSelfSales));
        item.put("reversalSales", normalizeMoney(record.reversalSelfSales));
        item.put("netSales", normalizeMoney(record.positiveSelfSales.add(record.reversalSelfSales)));
        item.put("incomeMode", record.incomeMode);
        item.put("historicalBackfill", record.historicalBackfill);
        item.put("missingCostData", record.missingCostData);
        item.put("missingTaxRateData", record.missingTaxRateData);
        return item;
    }

    private Map<Long, Product> loadProducts(List<Order> orders) {
        Set<Long> productIds = new LinkedHashSet<>();
        for (Order order : orders) {
            if (order != null && order.getProductId() != null) {
                productIds.add(order.getProductId());
            }
        }
        if (productIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productService.listByIds(productIds)) {
            if (product != null && product.getId() != null) {
                productMap.put(product.getId(), product);
            }
        }
        return productMap;
    }

    private Map<Long, Supplier> loadSuppliers(List<Order> orders, Map<Long, Product> productMap) {
        Set<Long> supplierIds = new LinkedHashSet<>();
        for (Order order : orders) {
            if (order != null && order.getSupplierId() != null) {
                supplierIds.add(order.getSupplierId());
            }
            Product product = order == null ? null : productMap.get(order.getProductId());
            if (product != null && product.getSupplierId() != null) {
                supplierIds.add(product.getSupplierId());
            }
        }
        if (supplierIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<Long, Supplier> supplierMap = new HashMap<>();
        for (Supplier supplier : supplierService.listByIds(supplierIds)) {
            if (supplier != null && supplier.getId() != null) {
                supplierMap.put(supplier.getId(), supplier);
            }
        }
        return supplierMap;
    }

    private SettlementContext resolveContext(Order order,
                                             Map<Long, Product> productMap,
                                             Map<Long, Supplier> supplierMap) {
        Long productId = order == null ? null : order.getProductId();
        Long supplierId = order == null ? null : order.getSupplierId();

        Product product = productId == null ? null : productMap.get(productId);
        Supplier supplier = supplierId == null ? null : supplierMap.get(supplierId);

        String sourceType = normalizeSourceType(
                trimToNull(order == null ? null : order.getSourceType()),
                product == null ? null : product.getSupplierId()
        );

        BigDecimal purchasePrice = order == null ? null : normalizeMoney(order.getPurchasePriceSnapshot());
        boolean historicalBackfill = false;
        if (purchasePrice == null && product != null && product.getPurchasePrice() != null) {
            purchasePrice = normalizeMoney(product.getPurchasePrice());
            historicalBackfill = true;
        }

        BigDecimal taxRate = order == null ? null : normalizeRate(order.getSettlementTaxRateSnapshot());
        String taxSource = trimToNull(order == null ? null : order.getSettlementTaxSource());
        if (!SOURCE_SELF.equalsIgnoreCase(sourceType) && taxRate == null) {
            BigDecimal fallbackTaxRate = resolveThirdPartyTaxRate(product, supplier);
            if (fallbackTaxRate != null) {
                taxRate = fallbackTaxRate;
                taxSource = resolveTaxSource(product);
                historicalBackfill = true;
            }
        }

        String supplierName = trimToNull(order == null ? null : order.getSupplierName());
        if (supplierName == null && supplier != null) {
            supplierName = trimToNull(supplier.getName());
        }
        if (supplierName == null && SOURCE_SELF.equalsIgnoreCase(sourceType)) {
            supplierName = "平台自营";
        }

        boolean missingCostData = SOURCE_SELF.equalsIgnoreCase(sourceType) && purchasePrice == null;
        boolean missingTaxRateData = !SOURCE_SELF.equalsIgnoreCase(sourceType) && taxRate == null;

        return new SettlementContext(
                sourceType,
                supplierName,
                taxRate,
                purchasePrice,
                taxSource,
                historicalBackfill,
                missingCostData,
                missingTaxRateData
        );
    }

    private SettlementOrderRecord buildRecord(Order order,
                                              SettlementContext context,
                                              LocalDate startDate,
                                              LocalDate endDate) {
        boolean completedIncluded = isCompleted(order)
                && order.getCompletedAt() != null
                && isBetween(order.getCompletedAt().toLocalDate(), startDate, endDate);
        boolean refundIncluded = isRefundCompleted(order)
                && order.getRefundAt() != null
                && isBetween(order.getRefundAt().toLocalDate(), startDate, endDate);

        if (!completedIncluded && !refundIncluded) {
            return null;
        }

        BigDecimal totalAmount = normalizeMoney(order.getTotalAmount());
        int quantity = order.getQuantity() == null || order.getQuantity() <= 0 ? 1 : order.getQuantity();

        BigDecimal positiveThirdPartyIncome = ZERO;
        BigDecimal positiveSelfSales = ZERO;
        BigDecimal positiveSelfGrossProfit = ZERO;
        BigDecimal reversalThirdPartyIncome = ZERO;
        BigDecimal reversalSelfSales = ZERO;
        BigDecimal reversalSelfGrossProfit = ZERO;

        if (completedIncluded) {
            if (SOURCE_SELF.equalsIgnoreCase(context.sourceType)) {
                positiveSelfSales = totalAmount;
                if (context.purchasePrice != null) {
                    positiveSelfGrossProfit = normalizeMoney(totalAmount.subtract(context.purchasePrice.multiply(BigDecimal.valueOf(quantity))));
                }
            } else if (context.taxRate != null) {
                positiveThirdPartyIncome = calculateThirdPartyIncome(totalAmount, context.taxRate);
            }
        }

        if (refundIncluded) {
            if (SOURCE_SELF.equalsIgnoreCase(context.sourceType)) {
                reversalSelfSales = totalAmount.negate();
                if (context.purchasePrice != null) {
                    reversalSelfGrossProfit = normalizeMoney(totalAmount.subtract(context.purchasePrice.multiply(BigDecimal.valueOf(quantity)))).negate();
                }
            } else if (context.taxRate != null) {
                reversalThirdPartyIncome = calculateThirdPartyIncome(totalAmount, context.taxRate).negate();
            }
        }

        BigDecimal positiveIncome = SOURCE_SELF.equalsIgnoreCase(context.sourceType)
                ? positiveSelfGrossProfit
                : positiveThirdPartyIncome;
        BigDecimal reversalIncome = SOURCE_SELF.equalsIgnoreCase(context.sourceType)
                ? reversalSelfGrossProfit
                : reversalThirdPartyIncome;
        BigDecimal refundReversalAmount = reversalIncome.abs();
        BigDecimal netBusinessIncome = positiveIncome.add(reversalIncome);

        return new SettlementOrderRecord(
                order.getId(),
                order.getOrderNo(),
                order.getProductName(),
                context.sourceType,
                context.supplierName,
                order.getStatus(),
                order.getRefundStatus(),
                quantity,
                totalAmount,
                order.getCompletedAt(),
                order.getRefundAt(),
                context.taxRate,
                context.purchasePrice,
                context.taxSource,
                positiveThirdPartyIncome,
                positiveSelfSales,
                positiveSelfGrossProfit,
                reversalThirdPartyIncome,
                reversalSelfSales,
                reversalSelfGrossProfit,
                positiveIncome,
                reversalIncome,
                refundReversalAmount,
                netBusinessIncome,
                SOURCE_SELF.equalsIgnoreCase(context.sourceType) ? "自营销售/毛利" : "第三方抽成",
                context.historicalBackfill,
                context.missingCostData,
                context.missingTaxRateData,
                resolveSortAt(order)
        );
    }

    private BigDecimal resolveThirdPartyTaxRate(Product product, Supplier supplier) {
        if (product == null && supplier == null) {
            return null;
        }
        String taxRateMode = trimToNull(product == null ? null : product.getTaxRateMode());
        if ("CUSTOM".equalsIgnoreCase(taxRateMode) && product != null) {
            return normalizeRate(product.getTaxRate());
        }
        if (supplier != null && supplier.getDefaultTaxRate() != null) {
            return normalizeRate(supplier.getDefaultTaxRate());
        }
        if (product != null && product.getTaxRate() != null) {
            return normalizeRate(product.getTaxRate());
        }
        return null;
    }

    private String resolveTaxSource(Product product) {
        String taxRateMode = trimToNull(product == null ? null : product.getTaxRateMode());
        if ("CUSTOM".equalsIgnoreCase(taxRateMode)) {
            return SOURCE_CUSTOM_TAX;
        }
        return SOURCE_SUPPLIER_TAX;
    }

    private Map<String, TrendBucket> initTrendBuckets(LocalDate startDate, LocalDate endDate, String groupBy) {
        Map<String, TrendBucket> buckets = new LinkedHashMap<>();
        if ("month".equalsIgnoreCase(groupBy)) {
            LocalDate cursor = startDate.withDayOfMonth(1);
            LocalDate lastMonth = endDate.withDayOfMonth(1);
            while (!cursor.isAfter(lastMonth)) {
                buckets.put(formatBucketKey(cursor, groupBy), new TrendBucket());
                cursor = cursor.plusMonths(1);
            }
            return buckets;
        }

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            buckets.put(formatBucketKey(cursor, groupBy), new TrendBucket());
            cursor = cursor.plusDays(1);
        }
        return buckets;
    }

    private String formatBucketKey(LocalDate date, String groupBy) {
        if ("month".equalsIgnoreCase(groupBy)) {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private BigDecimal calculateThirdPartyIncome(BigDecimal totalAmount, BigDecimal taxRate) {
        if (totalAmount == null || taxRate == null) {
            return ZERO;
        }
        return normalizeMoney(totalAmount.multiply(taxRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }

    private boolean matchesSourceFilter(String effectiveSourceType, String sourceTypeFilter) {
        String normalizedFilter = trimToNull(sourceTypeFilter);
        if (normalizedFilter == null) {
            return true;
        }
        if (SOURCE_SELF.equalsIgnoreCase(normalizedFilter)) {
            return SOURCE_SELF.equalsIgnoreCase(effectiveSourceType);
        }
        if ("THIRD_PARTY".equalsIgnoreCase(normalizedFilter)) {
            return !SOURCE_SELF.equalsIgnoreCase(effectiveSourceType);
        }
        return normalizedFilter.equalsIgnoreCase(effectiveSourceType);
    }

    private boolean matchesKeyword(Order order, String keyword) {
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword == null) {
            return true;
        }
        String lowerKeyword = normalizedKeyword.toLowerCase(Locale.ROOT);
        return contains(order == null ? null : order.getOrderNo(), lowerKeyword)
                || contains(order == null ? null : order.getProductName(), lowerKeyword);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean isBetween(LocalDate value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return false;
        }
        if (startDate != null && value.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && value.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    private boolean isCompleted(Order order) {
        return order != null && "COMPLETED".equalsIgnoreCase(order.getStatus());
    }

    private boolean isRefundCompleted(Order order) {
        if (order == null) {
            return false;
        }
        return "COMPLETED".equalsIgnoreCase(order.getRefundStatus())
                || "APPROVED".equalsIgnoreCase(order.getRefundStatus());
    }

    private String normalizeSourceType(String sourceType, Long supplierId) {
        if (sourceType != null) {
            return sourceType;
        }
        return supplierId == null ? SOURCE_SELF : "CURATED_SUPPLIER";
    }

    private String sourceTypeLabel(String sourceType) {
        if (SOURCE_SELF.equalsIgnoreCase(sourceType)) {
            return "自营";
        }
        return "第三方";
    }

    private String taxSourceLabel(String taxSource) {
        if (SOURCE_CUSTOM_TAX.equalsIgnoreCase(taxSource)) {
            return "商品覆盖";
        }
        if (SOURCE_SUPPLIER_TAX.equalsIgnoreCase(taxSource)) {
            return "供应商默认";
        }
        if (SOURCE_SELF.equalsIgnoreCase(taxSource)) {
            return "自营";
        }
        return taxSource == null ? "-" : taxSource;
    }

    private String normalizeGroupBy(String groupBy) {
        return "month".equalsIgnoreCase(trimToNull(groupBy)) ? "month" : "day";
    }

    private LocalDateTime resolveSortAt(Order order) {
        LocalDateTime value = order == null ? null : order.getRefundAt();
        if (value == null && order != null) {
            value = order.getCompletedAt();
        }
        if (value == null && order != null) {
            value = order.getUpdatedAt();
        }
        if (value == null && order != null) {
            value = order.getCreatedAt();
        }
        return value;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeRate(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static final class TrendBucket {
        private BigDecimal thirdPartyIncome = ZERO;
        private BigDecimal selfOperatedSales = ZERO;
        private BigDecimal selfOperatedGrossProfit = ZERO;
        private BigDecimal refundReversalAmount = ZERO;
        private int orderCount = 0;
    }

    private record SettlementContext(
            String sourceType,
            String supplierName,
            BigDecimal taxRate,
            BigDecimal purchasePrice,
            String taxSource,
            boolean historicalBackfill,
            boolean missingCostData,
            boolean missingTaxRateData
    ) {
    }

    private record SettlementOrderRecord(
            Long id,
            String orderNo,
            String productName,
            String sourceType,
            String supplierName,
            String status,
            String refundStatus,
            int quantity,
            BigDecimal totalAmount,
            LocalDateTime completedAt,
            LocalDateTime refundAt,
            BigDecimal taxRate,
            BigDecimal purchasePrice,
            String taxSource,
            BigDecimal positiveThirdPartyIncome,
            BigDecimal positiveSelfSales,
            BigDecimal positiveSelfGrossProfit,
            BigDecimal reversalThirdPartyIncome,
            BigDecimal reversalSelfSales,
            BigDecimal reversalSelfGrossProfit,
            BigDecimal positiveIncome,
            BigDecimal reversalIncome,
            BigDecimal refundReversalAmount,
            BigDecimal netBusinessIncome,
            String incomeMode,
            boolean historicalBackfill,
            boolean missingCostData,
            boolean missingTaxRateData,
            LocalDateTime sortAt
    ) {
    }
}
