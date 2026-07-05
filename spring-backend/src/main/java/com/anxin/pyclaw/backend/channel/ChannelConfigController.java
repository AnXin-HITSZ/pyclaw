package com.anxin.pyclaw.backend.channel;

import com.anxin.pyclaw.backend.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
public class ChannelConfigController {
    private final ChannelConfigRepository repository;
    private final ObjectMapper objectMapper;

    public ChannelConfigController(ChannelConfigRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('channel:manage')")
    public List<ChannelConfigEntity> list() {
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('channel:manage')")
    public ChannelConfigEntity create(@Valid @RequestBody ChannelConfigRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        ChannelConfigEntity entity = new ChannelConfigEntity();
        entity.setId(UUID.randomUUID().toString());
        apply(entity, request);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }

    @PostMapping("/wechat/config")
    @PreAuthorize("hasAuthority('channel:manage')")
    public ChannelConfigEntity createWechat(@RequestBody Map<String, Object> config) {
        return create(new ChannelConfigRequest("wechat", String.valueOf(config.getOrDefault("name", "wechat")), config, null, true));
    }

    @PostMapping("/feishu/config")
    @PreAuthorize("hasAuthority('channel:manage')")
    public ChannelConfigEntity createFeishu(@RequestBody Map<String, Object> config) {
        return create(new ChannelConfigRequest("feishu", String.valueOf(config.getOrDefault("name", "feishu")), config, null, true));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('channel:manage')")
    public ChannelConfigEntity update(@PathVariable String id, @Valid @RequestBody ChannelConfigRequest request) {
        ChannelConfigEntity entity = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Channel config not found"));
        apply(entity, request);
        entity.setUpdatedAt(OffsetDateTime.now());
        return repository.save(entity);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('channel:manage')")
    public void delete(@PathVariable String id) {
        repository.deleteById(id);
    }

    private void apply(ChannelConfigEntity entity, ChannelConfigRequest request) {
        try {
            entity.setChannelType(request.channelType());
            entity.setName(request.name());
            entity.setConfigJson(objectMapper.writeValueAsString(request.config() == null ? Map.of() : request.config()));
            entity.setSecretRef(request.secretRef());
            entity.setEnabled(request.enabled());
        } catch (Exception exc) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid channel config");
        }
    }
}
