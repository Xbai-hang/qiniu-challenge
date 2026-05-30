package com.qiniu.challenge.space;

import com.qiniu.challenge.user.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CalendarSpaceService {

    private final CalendarSpaceRepository calendarSpaceRepository;

    public CalendarSpaceService(CalendarSpaceRepository calendarSpaceRepository) {
        this.calendarSpaceRepository = calendarSpaceRepository;
    }

    public CalendarSpaceResponse createPersonalSpace(User user) {
        return calendarSpaceRepository.createPersonalSpace(user.id(), user.displayName() + " 的个人日历");
    }

    public List<CalendarSpaceResponse> findAccessibleSpaces(long userId) {
        return calendarSpaceRepository.findAccessibleSpaces(userId);
    }
}
