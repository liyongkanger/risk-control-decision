package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.compile.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenes/{sceneCode}")
public class FlowAdminController {
    private final SceneFlowRepository repository;
    private final FlowPublicationService publication;
    private final GraphValidator validator;

    public FlowAdminController(SceneFlowRepository repository, FlowPublicationService publication,
                               GraphValidator validator) {
        this.repository = repository;
        this.publication = publication;
        this.validator = validator;
    }

    @PutMapping("/draft")
    public FlowDraft saveDraft(@PathVariable String sceneCode, @RequestParam long expectedRevision,
                               @RequestBody GraphDefinition graph) {
        var result = validator.validateDraft(graph);
        if (!result.valid()) throw new GraphCompilationException(result.issues());
        return repository.saveDraft(sceneCode, graph, expectedRevision);
    }

    @GetMapping("/draft")
    public FlowDraft getDraft(@PathVariable String sceneCode) {
        return repository.findDraft(sceneCode)
                .orElseThrow(() -> new java.util.NoSuchElementException("Draft not found: " + sceneCode));
    }

    @PostMapping("/validate")
    public ResponseEntity<FlowValidation> validate(@PathVariable String sceneCode,
                                                       @RequestBody GraphDefinition graph) {
        FlowValidation result = publication.validate(sceneCode, graph);
        return result.valid() ? ResponseEntity.ok(result)
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(result);
    }

    @PostMapping("/publish")
    public FlowVersion publish(@PathVariable String sceneCode, @RequestBody PublishRequest request) {
        return publication.publish(sceneCode, request.expectedRevision());
    }

    public record PublishRequest(long expectedRevision) {}
}
