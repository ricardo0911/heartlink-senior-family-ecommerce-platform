package com.heartlink.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heartlink.dto.SaveAddressDTO;
import com.heartlink.entity.Address;
import java.util.List;

public interface AddressService extends IService<Address> {
    Address saveAddress(SaveAddressDTO dto, Long userId);
    List<Address> getAddressList(Long userId);
    void deleteAddress(Long id, Long userId);
    void setDefault(Long id, Long userId);
}
