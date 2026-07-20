package com.heartlink.controller.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heartlink.common.Result;
import com.heartlink.entity.Product;
import com.heartlink.service.CategoryService;
import com.heartlink.service.ProductService;
import com.heartlink.service.SupplierService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductControllerTest {

    @Mock
    private ProductService productService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private SupplierService supplierService;

    private AdminProductController controller;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);
        controller = new AdminProductController(productService, categoryService, supplierService);
    }

    @Test
    void page_shouldApplySelfOperatedSupplyFilter() {
        Page<Product> page = new Page<>(1, 10);
        page.setRecords(List.of(buildProduct()));
        page.setTotal(1);
        when(productService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Result<IPage<Product>> result = controller.page(1, 10, null, null, "SELF_OPERATED", 1, "燕麦");

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getTotal());

        ArgumentCaptor<LambdaQueryWrapper<Product>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productService).page(any(Page.class), wrapperCaptor.capture());

        LambdaQueryWrapper<Product> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("source_type"));
        assertTrue(sqlSegment.contains("supplier_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("SELF_OPERATED"));
        assertTrue(paramValuesContain(wrapper, "燕麦"));
    }

    @Test
    void page_shouldApplyThirdPartySupplyFilter() {
        Page<Product> page = new Page<>(1, 10);
        page.setRecords(List.of(buildProduct()));
        page.setTotal(1);
        when(productService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Result<IPage<Product>> result = controller.page(1, 10, null, null, "THIRD_PARTY", null, null);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());

        ArgumentCaptor<LambdaQueryWrapper<Product>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productService).page(any(Page.class), wrapperCaptor.capture());

        LambdaQueryWrapper<Product> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();

        assertTrue(sqlSegment.contains("source_type"));
        assertTrue(sqlSegment.contains("supplier_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("SELF_OPERATED"));
    }

    private Product buildProduct() {
        Product product = new Product();
        product.setId(1L);
        product.setName("无糖燕麦片");
        return product;
    }

    private boolean paramValuesContain(LambdaQueryWrapper<Product> wrapper, String expectedFragment) {
        return wrapper.getParamNameValuePairs().values().stream()
                .map(String::valueOf)
                .anyMatch(value -> value.contains(expectedFragment));
    }
}
