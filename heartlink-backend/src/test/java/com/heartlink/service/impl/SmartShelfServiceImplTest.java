package com.heartlink.service.impl;

import com.heartlink.entity.SmartShelf;
import com.heartlink.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SmartShelfServiceImplTest {

    @Mock
    private ProductService productService;

    private SmartShelfServiceImpl smartShelfService;

    @BeforeEach
    void setUp() {
        smartShelfService = spy(new SmartShelfServiceImpl(productService));
    }

    @Test
    void react_shouldThrowWhenReactionInvalid() {
        SmartShelf shelf = new SmartShelf();
        shelf.setId(1L);
        shelf.setParentId(10L);
        doReturn(shelf).when(smartShelfService).getById(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> smartShelfService.react(1L, 10L, "MAYBE"));
        assertEquals("invalid reaction type", ex.getMessage());
        verify(smartShelfService, never()).updateById(any(SmartShelf.class));
    }

    @Test
    void react_shouldThrowWhenParentNotMatch() {
        SmartShelf shelf = new SmartShelf();
        shelf.setId(2L);
        shelf.setParentId(99L);
        doReturn(shelf).when(smartShelfService).getById(2L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> smartShelfService.react(2L, 10L, "LIKE"));
        assertEquals("shelf item not found", ex.getMessage());
    }

    @Test
    void react_shouldFillViewedAtWhenNull() {
        SmartShelf shelf = new SmartShelf();
        shelf.setId(3L);
        shelf.setParentId(10L);
        shelf.setViewedAt(null);
        doReturn(shelf).when(smartShelfService).getById(3L);
        doReturn(true).when(smartShelfService).updateById(any(SmartShelf.class));

        SmartShelf result = smartShelfService.react(3L, 10L, "LIKE");

        assertSame(shelf, result);
        assertEquals("LIKE", result.getReaction());
        assertNotNull(result.getReactedAt());
        assertNotNull(result.getViewedAt());
        verify(smartShelfService).updateById(shelf);
    }

    @Test
    void react_shouldKeepViewedAtWhenAlreadyExists() {
        SmartShelf shelf = new SmartShelf();
        shelf.setId(4L);
        shelf.setParentId(10L);
        LocalDateTime viewedAt = LocalDateTime.now().minusDays(1);
        shelf.setViewedAt(viewedAt);
        doReturn(shelf).when(smartShelfService).getById(4L);
        doReturn(true).when(smartShelfService).updateById(any(SmartShelf.class));

        SmartShelf result = smartShelfService.react(4L, 10L, "DISLIKE");

        assertEquals("DISLIKE", result.getReaction());
        assertEquals(viewedAt, result.getViewedAt());
        assertNotNull(result.getReactedAt());
        verify(smartShelfService).updateById(shelf);
    }
}
