package com.hb.flagmesh.control_plane.controller;

import com.hb.flagmesh.control_plane.model.CreateFlagRequest;
import com.hb.flagmesh.control_plane.model.FeatureFlag;
import com.hb.flagmesh.control_plane.model.FlagAuditLog;
import com.hb.flagmesh.control_plane.model.UpdateFlagRequest;
import com.hb.flagmesh.control_plane.service.FlagMutationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api")
public class FlagController {

    private final FlagMutationService flagMutationService;

    public FlagController(FlagMutationService flagMutationService) {
        this.flagMutationService = flagMutationService;
    }

    @GetMapping("/flags")
    public List<FeatureFlag> getFlags() {
        return flagMutationService.getAllFlags();
    }

    @GetMapping("/flags/{key}")
    public FeatureFlag getFlag(@PathVariable("key") String flagKey) {
        return flagMutationService.getFlag(flagKey);
    }

    @GetMapping("/flags/{key}/audits")
    public List<FlagAuditLog> getFlagAudits(@PathVariable("key") String flagKey) {
        return flagMutationService.getFlagAudits(flagKey);
    }

    @PostMapping("/flags")
    @ResponseStatus(HttpStatus.CREATED)
    public FeatureFlag createFlag(@Valid @RequestBody CreateFlagRequest request,
                                  @RequestHeader(value = "X-Actor",
                                          defaultValue = "system") String actor) {
        return flagMutationService.createFlag(request.flagKey(), request.toUpdateRequest(), actor);
    }

    @PatchMapping("/flags/{key}")
    public FeatureFlag updateFlag(@PathVariable("key") String flagKey,
                                  @Valid @RequestBody UpdateFlagRequest request,
                                  @RequestHeader(value = "X-Actor", defaultValue = "system") String actor) {
        return flagMutationService.updateFlag(flagKey, request, actor);
    }

    @GetMapping("/internal/flags/snapshot")
    public List<FeatureFlag> getSnapshot() {
        return flagMutationService.getAllFlags();
    }
}
