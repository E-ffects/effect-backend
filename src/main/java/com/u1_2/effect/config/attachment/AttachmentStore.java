package com.u1_2.effect.config.attachment;

import java.io.IOException;
import java.util.Optional;
import org.springframework.core.io.Resource;

interface AttachmentStore {

    void write(String key, Resource source, long size) throws IOException;

    Optional<Resource> find(String key);

    void delete(String key) throws IOException;
}
