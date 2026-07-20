package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.entity.Product;
import com.heartlink.entity.SmartShelf;
import com.heartlink.mapper.SmartShelfMapper;
import com.heartlink.service.ProductService;
import com.heartlink.service.SmartShelfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 閺呴缚鍏樼拹褎鐏﹂張宥呭鐎圭偟骞?
 */
@Service
@RequiredArgsConstructor
public class SmartShelfServiceImpl extends ServiceImpl<SmartShelfMapper, SmartShelf> implements SmartShelfService {
    
    private final ProductService productService;
    
    @Override
    public SmartShelf pushToShelf(Long childId, Long parentId, Long productId, String voiceMessage) {
        // 濡偓閺屻儲妲搁崥锕€鍑￠幒銊┾偓?
        SmartShelf existing = getOne(new LambdaQueryWrapper<SmartShelf>()
                .eq(SmartShelf::getChildId, childId)
                .eq(SmartShelf::getParentId, parentId)
                .eq(SmartShelf::getProductId, productId)
                .eq(SmartShelf::getReaction, "PENDING"));
        
        if (existing != null) {
            return existing;
        }
        
        SmartShelf shelf = new SmartShelf();
        shelf.setChildId(childId);
        shelf.setParentId(parentId);
        shelf.setProductId(productId);
        shelf.setVoiceMessage(voiceMessage);
        shelf.setReaction("PENDING");
        
        save(shelf);
        return shelf;
    }
    
    @Override
    public List<SmartShelf> batchPushToShelf(Long childId, Long parentId, List<Long> productIds, String voiceMessage) {
        List<SmartShelf> result = new ArrayList<>();
        for (Long productId : productIds) {
            result.add(pushToShelf(childId, parentId, productId, voiceMessage));
        }
        return result;
    }
    
    @Override
    public IPage<Map<String, Object>> getShelfItems(Long parentId, Integer page, Integer size) {
        IPage<SmartShelf> shelfPage = page(
                new Page<>(page, size),
                new LambdaQueryWrapper<SmartShelf>()
                        .eq(SmartShelf::getParentId, parentId)
                        .eq(SmartShelf::getReaction, "PENDING")
                        .orderByDesc(SmartShelf::getCreatedAt)
        );
        
        // 缂佸嫯顥婇崯鍡楁惂娣団剝浼?
        List<Map<String, Object>> records = new ArrayList<>();
        for (SmartShelf shelf : shelfPage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("shelf", shelf);
            item.put("product", productService.getById(shelf.getProductId()));
            records.add(item);
        }
        
        IPage<Map<String, Object>> result = new Page<>();
        result.setRecords(records);
        result.setTotal(shelfPage.getTotal());
        result.setCurrent(shelfPage.getCurrent());
        result.setSize(shelfPage.getSize());
        
        return result;
    }
    
    @Override
    public SmartShelf react(Long shelfId, Long parentId, String reaction) {
        SmartShelf shelf = getById(shelfId);
        if (shelf == null || !shelf.getParentId().equals(parentId)) {
            throw new RuntimeException("shelf item not found");
        }
        if (!"LIKE".equals(reaction) && !"DISLIKE".equals(reaction)) {
            throw new RuntimeException("invalid reaction type");
        }
        
        shelf.setReaction(reaction);
        shelf.setReactedAt(LocalDateTime.now());
        
        if (shelf.getViewedAt() == null) {
            shelf.setViewedAt(LocalDateTime.now());
        }
        
        updateById(shelf);
        return shelf;
    }
    
    @Override
    public SmartShelf collect(Long parentId, Long productId) {
        if (productId == null) {
            throw new RuntimeException("productId cannot be null");
        }

        SmartShelf shelf = list(new LambdaQueryWrapper<SmartShelf>()
                .eq(SmartShelf::getParentId, parentId)
                .eq(SmartShelf::getProductId, productId)
                .orderByDesc(SmartShelf::getCreatedAt)
                .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);

        if (shelf == null) {
            shelf = new SmartShelf();
            // DB schema in some environments has child_id as NOT NULL.
            shelf.setChildId(parentId);
            shelf.setParentId(parentId);
            shelf.setProductId(productId);
            shelf.setReaction("LIKE");
            shelf.setViewedAt(LocalDateTime.now());
            shelf.setReactedAt(LocalDateTime.now());
            save(shelf);
            return shelf;
        }

        shelf.setReaction("LIKE");
        if (shelf.getChildId() == null) {
            shelf.setChildId(parentId);
        }
        if (shelf.getViewedAt() == null) {
            shelf.setViewedAt(LocalDateTime.now());
        }
        shelf.setReactedAt(LocalDateTime.now());
        updateById(shelf);
        return shelf;
    }

    @Override
    public List<SmartShelf> getLikedItems(Long parentId) {
        return list(new LambdaQueryWrapper<SmartShelf>()
                .eq(SmartShelf::getParentId, parentId)
                .eq(SmartShelf::getReaction, "LIKE")
                .orderByDesc(SmartShelf::getReactedAt));
    }

    @Override
    public IPage<Map<String, Object>> getChildShelfItems(Long childId, Long parentId, String reaction, Integer page, Integer size) {
        LambdaQueryWrapper<SmartShelf> wrapper = new LambdaQueryWrapper<>();

        if (parentId != null) {
            wrapper.eq(SmartShelf::getParentId, parentId)
                    .and(group -> group.eq(SmartShelf::getChildId, childId)
                            .or()
                            .eq(SmartShelf::getChildId, parentId));
        } else {
            wrapper.eq(SmartShelf::getChildId, childId);
        }
        if (reaction != null && !reaction.isBlank()) {
            wrapper.eq(SmartShelf::getReaction, reaction);
        }

        wrapper.orderByDesc(SmartShelf::getCreatedAt);

        IPage<SmartShelf> shelfPage = page(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = new ArrayList<>();
        for (SmartShelf shelf : shelfPage.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("shelf", shelf);
            item.put("product", productService.getById(shelf.getProductId()));
            records.add(item);
        }

        IPage<Map<String, Object>> result = new Page<>();
        result.setRecords(records);
        result.setTotal(shelfPage.getTotal());
        result.setCurrent(shelfPage.getCurrent());
        result.setSize(shelfPage.getSize());
        return result;
    }
}
