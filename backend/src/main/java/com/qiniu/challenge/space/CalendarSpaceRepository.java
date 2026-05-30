package com.qiniu.challenge.space;

import java.util.List;

public interface CalendarSpaceRepository {

    CalendarSpaceResponse createPersonalSpace(long ownerUserId, String name);

    List<CalendarSpaceResponse> findAccessibleSpaces(long userId);
}
