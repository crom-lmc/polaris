package com.laimengchao.sso.forum.storage;


import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.laimengchao.sso.forum.storage.impl.ForumStorageServiceImpl;



public interface ForumStorageService {

    public record StoredFile(String storagePath, String mimeType, long size) {
    }

    StoredFile store(MultipartFile file, String extension);

    Resource read(String relativePath);

    void delete(String relativePath);


    static String extension(String filename) {
        return ForumStorageServiceImpl.extension(filename);
    }
}
