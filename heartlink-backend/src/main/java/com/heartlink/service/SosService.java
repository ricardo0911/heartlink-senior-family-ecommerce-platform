package com.heartlink.service;

public interface SosService {
    void triggerSos(Long parentId);
    String getContacts(Long userId);
    void saveContacts(Long userId, String contacts);
}
