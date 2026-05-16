package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.CourseSkillMappingCommand;
import studio.one.platform.skillgraph.application.command.NcsSkillMappingCommand;
import studio.one.platform.skillgraph.application.result.CourseSkillMappingView;
import studio.one.platform.skillgraph.application.result.NcsSkillMappingView;
import studio.one.platform.skillgraph.application.usecase.SkillMappingService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.mapping-base-path:/api/mgmt/skillgraph/mappings}")
@RequiredArgsConstructor
@Validated
public class SkillMappingMgmtController {
    private final SkillMappingService service;

    @GetMapping("/ncs")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<NcsSkillMappingView>>> ncs(
            @RequestParam(value = "ncsUnitId", required = false) @Size(max = 100) String ncsUnitId) {
        return ResponseEntity.ok(ApiResponse.ok(service.findNcsMappings(ncsUnitId)));
    }

    @PostMapping("/ncs")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<NcsSkillMappingView>> saveNcs(@Valid @RequestBody NcsSkillMappingCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveNcsMapping(command)));
    }

    @GetMapping("/courses")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<CourseSkillMappingView>>> courses(
            @RequestParam(value = "courseId", required = false) @Size(max = 100) String courseId) {
        return ResponseEntity.ok(ApiResponse.ok(service.findCourseMappings(courseId)));
    }

    @PostMapping("/courses")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<CourseSkillMappingView>> saveCourse(@Valid @RequestBody CourseSkillMappingCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveCourseMapping(command)));
    }
}
