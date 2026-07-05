package com.anxin.pyclaw.backend.usage;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage-records")
public class UsageController {
    private final UsageRecordRepository repository;

    public UsageController(UsageRecordRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public List<UsageRecordEntity> list() {
        return repository.findAll();
    }
}
