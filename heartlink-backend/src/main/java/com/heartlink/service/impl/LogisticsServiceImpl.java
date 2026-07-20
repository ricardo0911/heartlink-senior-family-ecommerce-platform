package com.heartlink.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.heartlink.config.LogisticsProperties;
import com.heartlink.dto.LogisticsTraceItemDTO;
import com.heartlink.dto.OrderLogisticsDTO;
import com.heartlink.entity.Order;
import com.heartlink.service.LogisticsService;
import com.heartlink.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    private static final String PROVIDER_KDNIAO = "KDNIAO";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_SIGNED = "SIGNED";
    private static final String MESSAGE_NOT_SHIPPED = "该订单暂未录入物流单号";
    private static final String MESSAGE_NOT_CONFIGURED = "物流查询未配置，已记录运单信息";
    private static final String MESSAGE_QUERY_FAILED = "物流接口暂时不可用，已返回已保存的运单信息";
    private static final String MOCK_SHIP_ORIGIN_PROVINCE = "山东省";
    private static final String MOCK_SHIP_ORIGIN_CITY = "泰安市";
    private static final String MOCK_SHIP_ORIGIN_DISTRICT = "岱岳区";
    private static final String MOCK_SHIP_ORIGIN_ADDRESS = MOCK_SHIP_ORIGIN_PROVINCE + MOCK_SHIP_ORIGIN_CITY + MOCK_SHIP_ORIGIN_DISTRICT;
    private static final String MOCK_DELIVERED_STATUS = "DELIVERED";

    private final LogisticsProperties properties;
    private final OrderService orderService;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build();

    @Override
    public OrderLogisticsDTO getOrderLogistics(Order order, boolean refresh) {
        if (order == null) {
            throw new RuntimeException("order not found");
        }
        persistIfCompletedSignedOrderNormalized(order);
        if (!hasTracking(order)) {
            return buildResponse(order, false, resolveNoTrackingMessage(order));
        }
        if (isSignedOrder(order)) {
            return buildResponse(order, false, queryEnabled(), null);
        }
        if (!refresh) {
            return buildResponse(order, false, queryEnabled(), queryEnabled() ? null : MESSAGE_NOT_CONFIGURED);
        }

        if (!queryEnabled()) {
            return buildResponse(order, false, MESSAGE_NOT_CONFIGURED);
        }

        try {
            ProviderResult result = queryProvider(order);
            if (!result.successful) {
                return buildResponse(order, false, result.message);
            }
            applyProviderResult(order, result);
            orderService.updateById(order);
            return buildResponse(order, true, result.message);
        } catch (Exception e) {
            log.warn("logistics query failed, orderId={}, trackingNo={}", order.getId(), order.getTrackingNo(), e);
            return buildResponse(order, false, MESSAGE_QUERY_FAILED);
        }
    }

    @Override
    public void tryRefreshOrderLogistics(Order order) {
        if (order == null) {
            return;
        }
        persistIfCompletedSignedOrderNormalized(order);
        if (isSignedOrder(order) || !hasTracking(order) || !queryEnabled() || !properties.isQueryOnShip()) {
            return;
        }
        try {
            ProviderResult result = queryProvider(order);
            if (!result.successful) {
                return;
            }
            applyProviderResult(order, result);
            orderService.updateById(order);
        } catch (Exception e) {
            log.warn("skip refreshing logistics after ship, orderId={}", order.getId(), e);
        }
    }

    private ProviderResult queryProvider(Order order) throws IOException {
        if (properties.isMockProvider()) {
            return queryMock(order);
        }
        return queryKdniao(order);
    }

    private ProviderResult queryKdniao(Order order) throws IOException {
        LogisticsProperties.Kdniao kdniao = properties.getKdniao();

        JSONObject requestData = new JSONObject();
        if (StringUtils.hasText(order.getExpressCompanyCode())) {
            requestData.put("ShipperCode", order.getExpressCompanyCode().trim());
        }
        requestData.put("LogisticCode", order.getTrackingNo().trim());

        String requestDataJson = JSON.toJSONString(requestData);
        FormBody body = new FormBody.Builder()
                .add("RequestData", requestDataJson)
                .add("EBusinessID", kdniao.getEbusinessId().trim())
                .add("RequestType", defaultString(kdniao.getQueryRequestType(), "8002"))
                .add("DataSign", sign(requestDataJson, kdniao.getAppKey().trim()))
                .add("DataType", "2")
                .build();

        Request request = new Request.Builder()
                .url(kdniao.getBaseUrl().trim())
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("kdniao request failed: HTTP " + response.code());
            }

            JSONObject payload = JSON.parseObject(response.body().string());
            if (payload == null) {
                throw new RuntimeException("kdniao response is empty");
            }

            ProviderResult result = new ProviderResult();
            result.message = payload.getString("Reason");
            result.companyCode = defaultString(payload.getString("ShipperCode"), order.getExpressCompanyCode());
            result.companyName = defaultString(payload.getString("ShipperName"), order.getExpressCompanyName());
            result.trackingNo = defaultString(payload.getString("LogisticCode"), order.getTrackingNo());
            result.status = mapStatus(payload.getString("State"));
            result.statusText = mapStatusText(payload.getString("State"));
            result.updatedAt = LocalDateTime.now();

            JSONArray traces = payload.getJSONArray("Traces");
            result.traces = parseTraces(traces);
            result.traceJson = JSON.toJSONString(result.traces);
            result.lastTrace = result.traces.isEmpty() ? defaultLastTrace(order, result.statusText) : joinTrace(result.traces.get(0));

            boolean success = payload.getBooleanValue("Success");
            result.successful = success || !result.traces.isEmpty() || StringUtils.hasText(payload.getString("State"));
            if (!result.successful && !StringUtils.hasText(result.message)) {
                result.message = MESSAGE_QUERY_FAILED;
            }
            if (!StringUtils.hasText(result.message)) {
                result.message = result.traces.isEmpty() ? "暂未查询到最新轨迹" : "物流轨迹已更新";
            }
            return result;
        }
    }

    private void applyProviderResult(Order order, ProviderResult result) {
        order.setLogisticsProvider(defaultString(result.providerCode, properties.resolveProviderCode()));
        order.setExpressCompanyCode(defaultString(result.companyCode, order.getExpressCompanyCode()));
        order.setExpressCompanyName(defaultString(result.companyName, order.getExpressCompanyName()));
        order.setTrackingNo(defaultString(result.trackingNo, order.getTrackingNo()));
        order.setLogisticsStatus(defaultString(result.status, order.getLogisticsStatus()));
        order.setLogisticsStatusText(defaultString(result.statusText, order.getLogisticsStatusText()));
        order.setLogisticsLastTrace(defaultString(result.lastTrace, order.getLogisticsLastTrace()));
        order.setLogisticsTraceJson(result.traceJson);
        order.setLogisticsUpdatedAt(result.updatedAt != null ? result.updatedAt : LocalDateTime.now());
    }

    private ProviderResult queryMock(Order order) {
        LocalDateTime shippedAt = order.getShippedAt() != null ? order.getShippedAt() : LocalDateTime.now().minusHours(6);
        LocalDateTime now = LocalDateTime.now();
        List<MockTraceStage> stages = buildMockStages(order, shippedAt);
        List<LogisticsTraceItemDTO> traces = new ArrayList<>();
        for (MockTraceStage stage : stages) {
            if (stage.time != null && !stage.time.isAfter(now)) {
                traces.add(buildTraceItem(stage.time, stage.station, stage.location));
            }
        }

        ProviderResult result = new ProviderResult();
        result.providerCode = LogisticsProperties.MOCK_PROVIDER;
        result.companyCode = order.getExpressCompanyCode();
        result.companyName = defaultString(order.getExpressCompanyName(), "快递公司");
        result.trackingNo = order.getTrackingNo();
        result.traces = reverseNewestFirst(traces);
        result.updatedAt = result.traces.isEmpty() ? now : parseTraceTime(result.traces.get(0), now);
        result.traceJson = JSON.toJSONString(result.traces);

        if (result.traces.size() >= 6) {
            result.status = MOCK_DELIVERED_STATUS;
            result.statusText = "已送达，待签收";
            result.message = "商品已送达，请提醒收货人确认签收";
        } else if (result.traces.size() >= 2) {
            result.status = "IN_TRANSIT";
            result.statusText = "运输中";
            result.message = "模拟物流轨迹已更新";
        } else if (!result.traces.isEmpty()) {
            result.status = "ACCEPTED";
            result.statusText = "已揽收";
            result.message = "模拟物流轨迹已更新";
        } else {
            result.status = "PENDING";
            result.statusText = "待揽收";
            result.message = "已发货，等待物流公司揽收";
        }

        result.lastTrace = result.traces.isEmpty()
                ? defaultString(order.getExpressCompanyName(), "快递公司") + "：商家已通知快递公司上门揽收"
                : joinTrace(result.traces.get(0));
        result.successful = true;
        return result;
    }

    private List<MockTraceStage> buildMockStages(Order order, LocalDateTime shippedAt) {
        List<String> route = resolveMockRoute(order);
        String companyName = defaultString(order.getExpressCompanyName(), "快递公司");
        String destinationAddress = resolveReceiverDestination(order);
        List<MockTraceStage> stages = new ArrayList<>();
        stages.add(new MockTraceStage(shippedAt.plusSeconds(5), route.get(0), route.get(0) + "已完成揽收，快件由" + companyName + "从" + MOCK_SHIP_ORIGIN_ADDRESS + "装车发出"));
        stages.add(new MockTraceStage(shippedAt.plusSeconds(10), route.get(1), "快件已到达" + route.get(1) + "，正在进行分拣"));
        stages.add(new MockTraceStage(shippedAt.plusSeconds(15), route.get(2), "快件已离开" + route.get(1) + "，发往" + route.get(2)));
        stages.add(new MockTraceStage(shippedAt.plusSeconds(20), route.get(3), "快件已进入" + route.get(3) + "，正在进行末端分发"));
        stages.add(new MockTraceStage(shippedAt.plusSeconds(25), route.get(4), "快件已抵达" + route.get(4) + "，配送员正在派送"));
        stages.add(new MockTraceStage(shippedAt.plusSeconds(30), route.get(5), "快件已送达" + destinationAddress + "，等待收货人确认签收"));
        return stages;
    }

    private List<String> resolveMockRoute(Order order) {
        String receiverAddress = normalizeAddress(order == null ? null : order.getReceiverAddress());
        String province = extractProvince(receiverAddress);
        String city = extractCity(receiverAddress, province);
        String district = extractDistrict(receiverAddress, city);

        String destinationProvince = defaultString(province, MOCK_SHIP_ORIGIN_PROVINCE);
        String destinationCity = defaultString(city, destinationProvince + "分拨中心");
        String destinationDistrict = defaultString(district, destinationCity + "配送站");

        Set<String> route = new LinkedHashSet<>();
        route.add(MOCK_SHIP_ORIGIN_CITY + "揽收营业部");
        route.add(MOCK_SHIP_ORIGIN_CITY + "转运中心");
        route.add(destinationProvince + "干线转运中心");
        route.add(destinationCity + "分拨中心");
        route.add(destinationDistrict + "配送站");
        route.add(resolveReceiverDestination(order));
        return new ArrayList<>(route);
    }

    private String resolveReceiverDestination(Order order) {
        String receiverAddress = normalizeAddress(order == null ? null : order.getReceiverAddress());
        return StringUtils.hasText(receiverAddress) ? receiverAddress : "收货地址";
    }

    private String normalizeAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace(" ", "")
                .replace("　", "")
                .replace(",", "")
                .replace("，", "")
                .trim();
    }

    private String extractProvince(String address) {
        if (!StringUtils.hasText(address)) {
            return "";
        }
        String[] markers = {"省", "自治区", "特别行政区"};
        for (String marker : markers) {
            int index = address.indexOf(marker);
            if (index > 0) {
                return address.substring(0, index + marker.length());
            }
        }
        String[] municipalities = {"北京", "上海", "天津", "重庆"};
        for (String municipality : municipalities) {
            if (address.startsWith(municipality)) {
                return municipality + "市";
            }
        }
        return "";
    }

    private String extractCity(String address, String province) {
        if (!StringUtils.hasText(address)) {
            return "";
        }
        String remainder = address;
        if (StringUtils.hasText(province) && address.startsWith(province)) {
            remainder = address.substring(province.length());
        }
        String[] markers = {"市", "州", "地区", "盟"};
        for (String marker : markers) {
            int index = remainder.indexOf(marker);
            if (index > 0) {
                return remainder.substring(0, index + marker.length());
            }
        }
        return "";
    }

    private String extractDistrict(String address, String city) {
        if (!StringUtils.hasText(address)) {
            return "";
        }
        String remainder = address;
        if (StringUtils.hasText(city)) {
            int index = remainder.indexOf(city);
            if (index >= 0) {
                remainder = remainder.substring(index + city.length());
            }
        }
        String[] markers = {"区", "县", "市", "镇", "乡", "街道"};
        for (String marker : markers) {
            int index = remainder.indexOf(marker);
            if (index > 0) {
                return remainder.substring(0, index + marker.length());
            }
        }
        return "";
    }

    private OrderLogisticsDTO buildResponse(Order order, boolean refreshed, String message) {
        return buildResponse(order, refreshed, queryEnabled(), message);
    }

    private OrderLogisticsDTO buildResponse(Order order, boolean refreshed, boolean queryEnabled, String message) {
        OrderLogisticsDTO dto = new OrderLogisticsDTO();
        String resolvedLastTrace = resolveLastTrace(order);
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setExpressCompanyCode(order.getExpressCompanyCode());
        dto.setExpressCompanyName(order.getExpressCompanyName());
        dto.setTrackingNo(order.getTrackingNo());
        dto.setLogisticsProvider(order.getLogisticsProvider());
        dto.setLogisticsStatus(order.getLogisticsStatus());
        dto.setLogisticsStatusText(resolveStatusText(order));
        dto.setLogisticsLastTrace(resolvedLastTrace);
        dto.setLogisticsUpdatedAt(order.getLogisticsUpdatedAt());
        dto.setHasTracking(hasTracking(order));
        dto.setQueryEnabled(queryEnabled);
        dto.setRefreshed(refreshed);
        dto.setMessage(message);
        dto.setTraces(buildDisplayTraces(order, resolvedLastTrace));
        return dto;
    }

    private List<LogisticsTraceItemDTO> buildDisplayTraces(Order order, String resolvedLastTrace) {
        List<LogisticsTraceItemDTO> traces = parseStoredTraces(order.getLogisticsTraceJson());
        if (!traces.isEmpty() || !hasTracking(order) || !StringUtils.hasText(resolvedLastTrace)) {
            return traces;
        }
        LogisticsTraceItemDTO fallback = new LogisticsTraceItemDTO();
        fallback.setAcceptTime(order.getLogisticsUpdatedAt() != null ? order.getLogisticsUpdatedAt().toString() : null);
        fallback.setAcceptStation(resolvedLastTrace);
        traces.add(fallback);
        return traces;
    }

    private List<LogisticsTraceItemDTO> parseStoredTraces(String traceJson) {
        if (!StringUtils.hasText(traceJson)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(traceJson, LogisticsTraceItemDTO.class);
        } catch (Exception e) {
            log.warn("failed to parse stored logistics trace json");
            return new ArrayList<>();
        }
    }

    private void persistIfCompletedSignedOrderNormalized(Order order) {
        if (normalizeCompletedSignedOrder(order)) {
            orderService.updateById(order);
        }
    }

    private boolean normalizeCompletedSignedOrder(Order order) {
        if (order == null || !STATUS_COMPLETED.equals(order.getStatus())) {
            return false;
        }
        LocalDateTime signedAt = resolveSignedAt(order);
        boolean changed = false;
        if (!STATUS_SIGNED.equals(order.getLogisticsStatus())) {
            order.setLogisticsStatus(STATUS_SIGNED);
            changed = true;
        }
        if (!"\u5df2\u7b7e\u6536".equals(order.getLogisticsStatusText())) {
            order.setLogisticsStatusText("\u5df2\u7b7e\u6536");
            changed = true;
        }
        String signedTrace = formatSignedTrace(signedAt);
        if (!signedTrace.equals(order.getLogisticsLastTrace())) {
            order.setLogisticsLastTrace(signedTrace);
            changed = true;
        }
        if (!signedAt.equals(order.getLogisticsUpdatedAt())) {
            order.setLogisticsUpdatedAt(signedAt);
            changed = true;
        }
        String normalizedTraceJson = prependSignedTrace(order.getLogisticsTraceJson(), signedAt);
        if (!normalizedTraceJson.equals(defaultString(order.getLogisticsTraceJson(), ""))) {
            order.setLogisticsTraceJson(normalizedTraceJson);
            changed = true;
        }
        return changed;
    }

    private boolean isSignedOrder(Order order) {
        if (order == null) {
            return false;
        }
        String logisticsStatus = defaultString(order.getLogisticsStatus(), "").toUpperCase(Locale.ROOT);
        return STATUS_COMPLETED.equals(order.getStatus()) || STATUS_SIGNED.equals(logisticsStatus);
    }

    private LocalDateTime resolveSignedAt(Order order) {
        if (order != null && order.getCompletedAt() != null) {
            return order.getCompletedAt();
        }
        if (order != null && order.getLogisticsUpdatedAt() != null) {
            return order.getLogisticsUpdatedAt();
        }
        return LocalDateTime.now();
    }

    private String formatSignedTrace(LocalDateTime signedAt) {
        return signedAt + " " + "\u6536\u8d27\u4eba\u5df2\u786e\u8ba4\u7b7e\u6536";
    }

    private String prependSignedTrace(String traceJson, LocalDateTime signedAt) {
        List<LogisticsTraceItemDTO> traces = new ArrayList<>(parseStoredTraces(traceJson));
        boolean alreadySigned = traces.stream()
                .anyMatch(item -> item != null && "\u6536\u8d27\u4eba\u5df2\u786e\u8ba4\u7b7e\u6536".equals(item.getAcceptStation()));
        if (!alreadySigned) {
            LogisticsTraceItemDTO signedTrace = new LogisticsTraceItemDTO();
            signedTrace.setAcceptTime(signedAt.toString());
            signedTrace.setAcceptStation("\u6536\u8d27\u4eba\u5df2\u786e\u8ba4\u7b7e\u6536");
            signedTrace.setLocation("\u8ba2\u5355\u5b8c\u6210");
            traces.add(0, signedTrace);
        }
        return JSON.toJSONString(traces);
    }

    private List<LogisticsTraceItemDTO> reverseNewestFirst(List<LogisticsTraceItemDTO> traces) {
        List<LogisticsTraceItemDTO> result = new ArrayList<>();
        for (int i = traces.size() - 1; i >= 0; i--) {
            result.add(traces.get(i));
        }
        return result;
    }

    private LogisticsTraceItemDTO buildTraceItem(LocalDateTime time, String station, String location) {
        LogisticsTraceItemDTO item = new LogisticsTraceItemDTO();
        item.setAcceptTime(time != null ? time.toString() : null);
        item.setAcceptStation(station);
        item.setLocation(location);
        return item;
    }

    private LocalDateTime parseTraceTime(LogisticsTraceItemDTO item, LocalDateTime fallback) {
        if (item == null || !StringUtils.hasText(item.getAcceptTime())) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(item.getAcceptTime());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private List<LogisticsTraceItemDTO> parseTraces(JSONArray traces) {
        List<LogisticsTraceItemDTO> items = new ArrayList<>();
        if (traces == null || traces.isEmpty()) {
            return items;
        }
        for (int i = traces.size() - 1; i >= 0; i--) {
            JSONObject trace = traces.getJSONObject(i);
            if (trace == null) {
                continue;
            }
            LogisticsTraceItemDTO item = new LogisticsTraceItemDTO();
            item.setAcceptTime(trace.getString("AcceptTime"));
            item.setAcceptStation(trace.getString("AcceptStation"));
            item.setLocation(trace.getString("Location"));
            items.add(item);
        }
        return items;
    }

    private String resolveStatusText(Order order) {
        if (!hasTracking(order)) {
            return resolveNoTrackingMessage(order);
        }
        if (StringUtils.hasText(order.getLogisticsStatusText())) {
            return order.getLogisticsStatusText();
        }
        String logisticsStatus = defaultString(order.getLogisticsStatus(), "").toUpperCase(Locale.ROOT);
        if (MOCK_DELIVERED_STATUS.equals(logisticsStatus)) {
            return "已送达，待签收";
        }
        if ("SIGNED".equals(logisticsStatus)) {
            return "已签收";
        }
        if (isManualTrackingOnly(order)) {
            return "已发货，未启用第三方物流查询";
        }
        if ("SHIPPED".equals(order.getStatus())) {
            return "已发货，待物流更新";
        }
        if ("COMPLETED".equals(order.getStatus())) {
            return "订单已完成";
        }
        return "暂无物流信息";
    }

    private String resolveLastTrace(Order order) {
        if (StringUtils.hasText(order.getLogisticsLastTrace())) {
            return order.getLogisticsLastTrace();
        }
        return defaultLastTrace(order, resolveStatusText(order));
    }

    private String defaultLastTrace(Order order, String fallbackText) {
        if (!hasTracking(order)) {
            return resolveNoTrackingMessage(order);
        }
        String company = defaultString(order.getExpressCompanyName(), "快递公司");
        return company + "：" + defaultString(fallbackText, "待物流更新");
    }

    private String resolveNoTrackingMessage(Order order) {
        if (order == null) {
            return MESSAGE_NOT_SHIPPED;
        }
        return switch (defaultString(order.getStatus(), "")) {
            case "PENDING_PAY" -> "该订单待付款，暂无物流信息";
            case "PAID" -> "该订单已付款，待商家发货";
            case "CANCELLED" -> "该订单已取消，无物流信息";
            case "COMPLETED" -> "该订单已完成";
            default -> MESSAGE_NOT_SHIPPED;
        };
    }

    private boolean isManualTrackingOnly(Order order) {
        return order != null
                && hasTracking(order)
                && ("SHIPPED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus()))
                && (LogisticsProperties.MANUAL_PROVIDER.equalsIgnoreCase(defaultString(order.getLogisticsProvider(), ""))
                || LogisticsProperties.MANUAL_PROVIDER.equalsIgnoreCase(defaultString(order.getLogisticsStatus(), ""))
                || !queryEnabled());
    }

    private boolean hasTracking(Order order) {
        return order != null && StringUtils.hasText(order.getTrackingNo());
    }

    private boolean queryEnabled() {
        return properties.isQueryEnabled();
    }

    private String sign(String requestData, String appKey) {
        return Base64.encode(DigestUtil.md5Hex(requestData + appKey));
    }

    private String mapStatus(String state) {
        return switch (defaultString(state, "")) {
            case "0" -> "NO_TRACE";
            case "1" -> "ACCEPTED";
            case "2" -> "IN_TRANSIT";
            case "3" -> "SIGNED";
            case "4" -> "PROBLEM";
            default -> "UNKNOWN";
        };
    }

    private String mapStatusText(String state) {
        return switch (defaultString(state, "")) {
            case "0" -> "暂无轨迹";
            case "1" -> "已揽收";
            case "2" -> "运输中";
            case "3" -> "已签收";
            case "4" -> "异常件";
            default -> "物流更新中";
        };
    }

    private String joinTrace(LogisticsTraceItemDTO item) {
        if (item == null) {
            return null;
        }
        String time = defaultString(item.getAcceptTime(), "");
        String station = defaultString(item.getAcceptStation(), "");
        String text = (time + " " + station).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static class ProviderResult {
        private boolean successful;
        private String providerCode;
        private String companyCode;
        private String companyName;
        private String trackingNo;
        private String status;
        private String statusText;
        private String lastTrace;
        private String traceJson;
        private String message;
        private LocalDateTime updatedAt;
        private List<LogisticsTraceItemDTO> traces = new ArrayList<>();
    }

    private static class MockTraceStage {
        private final LocalDateTime time;
        private final String station;
        private final String location;

        private MockTraceStage(LocalDateTime time, String station, String location) {
            this.time = time;
            this.station = station;
            this.location = location;
        }
    }
}
