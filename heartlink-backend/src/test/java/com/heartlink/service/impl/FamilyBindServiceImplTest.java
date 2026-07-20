package com.heartlink.service.impl;

import com.heartlink.entity.FamilyBind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FamilyBindServiceImplTest {

    private FamilyBindServiceImpl familyBindService;

    @BeforeEach
    void setUp() {
        familyBindService = spy(new FamilyBindServiceImpl());
        lenient().doReturn(true).when(familyBindService).remove(any());
        lenient().doReturn(0L).when(familyBindService).count(any());
        lenient().doReturn(null).when(familyBindService).getOne(any(), eq(false));
        lenient().doReturn(true).when(familyBindService).save(any(FamilyBind.class));
        lenient().doReturn(true).when(familyBindService).updateById(any(FamilyBind.class));
        lenient().doReturn(true).when(familyBindService).removeById(anyLong());
    }

    @Test
    void generateBindCode_shouldRejectDuplicateMotherRelation() {
        FamilyBind existingMother = new FamilyBind();
        existingMother.setChildId(10L);
        existingMother.setParentId(20L);
        existingMother.setRelation("妈妈");
        existingMother.setStatus("ACTIVE");
        doReturn(existingMother).when(familyBindService).getOne(any(), eq(false));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> familyBindService.generateBindCode(10L, "妈妈")
        );

        assertEquals("已绑定妈妈，不能重复绑定", ex.getMessage());
        verify(familyBindService, never()).save(any(FamilyBind.class));
        verify(familyBindService, never()).updateById(any(FamilyBind.class));
    }

    @Test
    void confirmBind_shouldRejectDuplicateFatherRelationAcrossDifferentParents() {
        FamilyBind pending = new FamilyBind();
        pending.setId(1L);
        pending.setChildId(10L);
        pending.setParentId(0L);
        pending.setBindCode("123456");
        pending.setRelation("爸爸");
        pending.setStatus("PENDING");

        FamilyBind existingFather = new FamilyBind();
        existingFather.setChildId(10L);
        existingFather.setParentId(30L);
        existingFather.setRelation("爸爸");
        existingFather.setStatus("ACTIVE");

        doReturn(pending, null, existingFather).when(familyBindService).getOne(any(), eq(false));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> familyBindService.confirmBind(40L, "123456")
        );

        assertEquals("已绑定爸爸，不能重复绑定", ex.getMessage());
        verify(familyBindService, never()).removeById(1L);
        verify(familyBindService, never()).save(any(FamilyBind.class));
    }

    @Test
    void confirmBind_shouldAllowMultipleNonExclusiveRelations() {
        FamilyBind pending = new FamilyBind();
        pending.setId(2L);
        pending.setChildId(10L);
        pending.setParentId(0L);
        pending.setBindCode("654321");
        pending.setRelation("奶奶");
        pending.setStatus("PENDING");

        doReturn(pending, null).when(familyBindService).getOne(any(), eq(false));

        FamilyBind result = familyBindService.confirmBind(40L, "654321");

        assertEquals(10L, result.getChildId());
        assertEquals(40L, result.getParentId());
        assertEquals("奶奶", result.getRelation());
        assertEquals("ACTIVE", result.getStatus());
        verify(familyBindService).removeById(2L);
        verify(familyBindService).save(any(FamilyBind.class));
    }
}
