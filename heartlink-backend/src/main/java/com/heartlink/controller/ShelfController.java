package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.heartlink.common.Result;
import com.heartlink.entity.FamilyBind;
import com.heartlink.entity.SmartShelf;
import com.heartlink.entity.User;
import com.heartlink.service.FamilyBindService;
import com.heartlink.service.ProductService;
import com.heartlink.service.SmartShelfService;
import com.heartlink.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * 闂佸搫鎳樼紓姘跺礂濡吋瀚荤憸搴ㄦ倶閿曞倸绠崇憸宥夊春濡ゅ懎闂?
 */
@Tag(name = "Smart Shelf")
@RestController
@RequestMapping("/api/shelf")
@RequiredArgsConstructor
public class ShelfController {
    
    private final SmartShelfService smartShelfService;
    private final UserService userService;
    private final FamilyBindService familyBindService;
    private final ProductService productService;
    
    @Operation(summary = "闂佽浜介崝鎴﹀焵椤戣法顦﹂柡鍛灲瀹曨偅鎷呯粙璺ㄥ帓闁荤姵鍓崟顓濇(闁诲孩绋掗崝鏇㈠Χ瀹曞洨鍗?")
    @PostMapping("/push")
    public Result<SmartShelf> pushToShelf(
            @RequestParam Long parentId,
            @RequestParam Long productId,
            @RequestParam(required = false) String voiceMessage) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);
        SmartShelf shelf = smartShelfService.pushToShelf(childId, parentId, productId, voiceMessage);
        return Result.success("pushed to shelf", shelf);
    }
    
    @Operation(summary = "闂佸綊娼х紞濠囧闯濞差亜绠抽柕濞炬杹閸嬫挻鎷呴崨濠冪彍闂?闁诲孩绋掗崝鏇㈠Χ瀹曞洨鍗?")
    @PostMapping("/batch-push")
    public Result<List<SmartShelf>> batchPush(
            @RequestParam Long parentId,
            @RequestBody List<Long> productIds,
            @RequestParam(required = false) String voiceMessage) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);
        List<SmartShelf> shelves = smartShelfService.batchPushToShelf(childId, parentId, productIds, voiceMessage);
        return Result.success("batch push success: " + shelves.size(), shelves);
    }
    
    @Operation(summary = "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柟瀵稿У閻ｉ亶鎮归幇灞藉娴?闂傚倵鍋撻柤鎰佸灣閸欐垹绱?")
    @GetMapping("/my-shelf")
    public Result<IPage<Map<String, Object>>> getMyShelf(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        IPage<Map<String, Object>> result = smartShelfService.getShelfItems(parentId, page, size);
        return Result.success(result);
    }
    
    @Operation(summary = "闂佸摜鍠庡Λ妤呭箹瑜斿畷锝咁吋婢舵ɑ鐤?闂傚倵鍋撻柤鎰佸灣閸欐垹绱?")
    @PostMapping("/{shelfId}/react")
    public Result<SmartShelf> react(
            @PathVariable Long shelfId,
            @RequestParam String reaction) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        SmartShelf shelf = smartShelfService.react(shelfId, parentId, reaction);
        return Result.success(shelf);
    }

    @Operation(summary = "Parent collect product")
    @PostMapping("/collect")
    public Result<SmartShelf> collect(@RequestParam Long productId) {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        if (productService.getById(productId) == null) {
            return Result.error("product not found");
        }
        SmartShelf shelf = smartShelfService.collect(parentId, productId);
        return Result.success("collect success", shelf);
    }
    
    @Operation(summary = "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绐楁繝濠傚閸嬨劑鏌ｉ妸銉ヮ仼闁哄懌鍨藉畷?闁诲孩绋掗崝鏇㈠Χ瀹曞洨鍗氭い鏍ㄧ〒閸欌偓闂?")
    @GetMapping("/liked/{parentId}")
    public Result<List<SmartShelf>> getLikedItems(@PathVariable Long parentId) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);
        List<SmartShelf> items = smartShelfService.getLikedItems(parentId);
        return Result.success(items);
    }

    @Operation(summary = "闂佸吋鍎抽崲鑼躲亹閸ヮ剙绠ｉ柟瀛樼箖閻忔ɑ绻涢崱鎰伄婵炲牊鍨垮畷顖炲幢濡や焦鍎?闂傚倵鍋撻柤鎰佸灣閸欐垹绱?")
    @GetMapping("/my-liked")
    public Result<List<Map<String, Object>>> getMyLikedItems() {
        Long parentId = StpUtil.getLoginIdAsLong();
        assertRole(parentId, "PARENT");
        List<SmartShelf> shelves = smartShelfService.getLikedItems(parentId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SmartShelf shelf : shelves) {
            Map<String, Object> item = new HashMap<>();
            item.put("shelf", shelf);
            item.put("product", productService.getById(shelf.getProductId()));
            result.add(item);
        }
        return Result.success(result);
    }

    @Operation(summary = "闂佸吋鍎抽崲鑼躲亹閸モ斁鍋撳☉娆忓闁靛牊濞婇獮鎺楀Ω閳规儳浜惧ù锝囩摂閸炲墎鎲?闁诲孩绋掗崝鏇㈠Χ瀹曞洨鍗?")
    @GetMapping("/child-list")
    public Result<IPage<Map<String, Object>>> getChildShelfItems(
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String reaction,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        if (parentId != null) {
            assertChildBoundToParent(childId, parentId);
        }
        IPage<Map<String, Object>> result = smartShelfService.getChildShelfItems(childId, parentId, reaction, page, size);
        return Result.success(result);
    }

    @Operation(summary = "Get family shelf by parent id")
    @GetMapping("/family-shelf/{parentId}")
    public Result<IPage<Map<String, Object>>> getFamilyShelf(
            @PathVariable Long parentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long childId = StpUtil.getLoginIdAsLong();
        assertRole(childId, "CHILD");
        assertChildBoundToParent(childId, parentId);
        IPage<Map<String, Object>> result = smartShelfService.getShelfItems(parentId, page, size);
        return Result.success(result);
    }

    private void assertRole(Long userId, String expectedRole) {
        User user = userService.getById(userId);
        if (user == null || !expectedRole.equals(user.getRole())) {
            throw new RuntimeException("no permission to access this endpoint");
        }
    }

    private void assertChildBoundToParent(Long childId, Long parentId) {
        List<FamilyBind> binds = familyBindService.getParentsByChildId(childId);
        boolean bound = binds.stream().anyMatch(bind -> parentId.equals(bind.getParentId()));
        if (!bound) {
            throw new RuntimeException("parent is not bound to this child");
        }
    }
}
