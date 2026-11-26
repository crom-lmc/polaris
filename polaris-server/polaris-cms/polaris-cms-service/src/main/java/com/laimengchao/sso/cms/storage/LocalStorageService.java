package com.laimengchao.sso.cms.storage;


import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface LocalStorageService {

    StoredFile store(MultipartFile file, String folder) throws IOException;

    StoredFile store(InputStream inputStream, String originalName, String contentType, long size, String folder) throws IOException;

    void delete(String url);
}
