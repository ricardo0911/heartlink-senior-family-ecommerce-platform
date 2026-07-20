package com.heartlink.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heartlink.dto.SaveAddressDTO;
import com.heartlink.entity.Address;
import com.heartlink.mapper.AddressMapper;
import com.heartlink.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    @Override
    @Transactional
    public Address saveAddress(SaveAddressDTO dto, Long userId) {
        Address address;
        if (dto.getId() != null) {
            address = getById(dto.getId());
            if (address == null || !userId.equals(address.getUserId())) {
                throw new RuntimeException("地址不存在");
            }
        } else {
            address = new Address();
            address.setUserId(userId);
        }
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetailAddress(dto.getDetailAddress());

        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            // 先取消所有默认
            update(new LambdaUpdateWrapper<Address>()
                    .eq(Address::getUserId, userId)
                    .set(Address::getIsDefault, 0));
            address.setIsDefault(1);
        } else {
            address.setIsDefault(0);
        }

        saveOrUpdate(address);
        return address;
    }

    @Override
    public List<Address> getAddressList(Long userId) {
        return list(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getUpdatedAt));
    }

    @Override
    public void deleteAddress(Long id, Long userId) {
        Address address = getById(id);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new RuntimeException("地址不存在");
        }
        removeById(id);
    }

    @Override
    @Transactional
    public void setDefault(Long id, Long userId) {
        Address address = getById(id);
        if (address == null || !userId.equals(address.getUserId())) {
            throw new RuntimeException("地址不存在");
        }
        update(new LambdaUpdateWrapper<Address>()
                .eq(Address::getUserId, userId)
                .set(Address::getIsDefault, 0));
        address.setIsDefault(1);
        updateById(address);
    }
}
