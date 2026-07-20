package com.clawsaas.runtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PyclawToolResolveRequest(
        String profile,
        List<String> allow,
        List<String> deny,
        @JsonProperty("also_allow") List<String> alsoAllow,
        Boolean readonly
) {}
