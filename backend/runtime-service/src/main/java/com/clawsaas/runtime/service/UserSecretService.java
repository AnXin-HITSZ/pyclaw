package com.clawsaas.runtime.service;

import com.clawsaas.runtime.dto.UserSecretRequest;
import com.clawsaas.runtime.dto.UserSecretResponse;
import com.clawsaas.runtime.entity.UserSecretEntity;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

public interface UserSecretService {
    List<UserSecretResponse> list(Authentication authentication);
    List<UserSecretResponse> listByClaw(String clawId, Authentication authentication);
    UserSecretResponse create(UserSecretRequest request, Authentication authentication);
    UserSecretResponse update(String id, UserSecretRequest request, Authentication authentication);
    void delete(String id, Authentication authentication);
    UserSecretResponse sync(String id, Authentication authentication);
    Map<String, String> decryptValuesForSecret(UserSecretEntity entity);
}
