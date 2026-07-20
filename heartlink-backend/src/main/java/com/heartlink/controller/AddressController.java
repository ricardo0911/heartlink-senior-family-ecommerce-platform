package com.heartlink.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.heartlink.common.Result;
import com.heartlink.dto.SaveAddressDTO;
import com.heartlink.entity.Address;
import com.heartlink.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收货地址")
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "保存地址")
    @PostMapping("/save")
    public Result<Address> save(@RequestBody SaveAddressDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        Address address = addressService.saveAddress(dto, userId);
        return Result.success("保存成功", address);
    }

    @Operation(summary = "地址列表")
    @GetMapping("/list")
    public Result<List<Address>> list() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(addressService.getAddressList(userId));
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        addressService.deleteAddress(id, userId);
        return Result.success();
    }

    @Operation(summary = "设为默认")
    @PostMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        addressService.setDefault(id, userId);
        return Result.success();
    }
}
