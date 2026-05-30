package com.qiniu.challenge.space;

import com.qiniu.challenge.auth.CurrentUserPrincipal;
import com.qiniu.challenge.common.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spaces")
public class CalendarSpaceController {

    private final CalendarSpaceService calendarSpaceService;

    public CalendarSpaceController(CalendarSpaceService calendarSpaceService) {
        this.calendarSpaceService = calendarSpaceService;
    }

    @GetMapping
    public ApiResponse<List<CalendarSpaceResponse>> mySpaces(@AuthenticationPrincipal CurrentUserPrincipal principal) {
        return ApiResponse.success(calendarSpaceService.findAccessibleSpaces(principal.userId()));
    }
}
