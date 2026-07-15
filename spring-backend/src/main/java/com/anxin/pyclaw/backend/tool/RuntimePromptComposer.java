package com.anxin.pyclaw.backend.tool;

import com.anxin.pyclaw.backend.pyclaw.PyclawPromptFragment;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RuntimePromptComposer {
    public String compose(String agentSystemPrompt, List<PyclawPromptFragment> fragments) {
        StringBuilder prompt = new StringBuilder();
        if (agentSystemPrompt != null && !agentSystemPrompt.isBlank()) {
            prompt.append(agentSystemPrompt.trim());
        }
        if (fragments != null) {
            for (PyclawPromptFragment fragment : fragments) {
                if (fragment == null || fragment.content() == null || fragment.content().isBlank()) {
                    continue;
                }
                if (prompt.length() > 0) {
                    prompt.append("\n\n");
                }
                prompt.append(fragment.content().trim());
            }
        }
        return prompt.toString();
    }
}
