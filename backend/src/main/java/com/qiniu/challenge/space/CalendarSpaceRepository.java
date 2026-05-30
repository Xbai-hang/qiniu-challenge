package com.qiniu.challenge.space;

import java.util.List;

public interface CalendarSpaceRepository {

    CalendarSpaceResponse createPersonalSpace(long ownerUserId, String name);

    void ensurePersonalSpaceExists(long ownerUserId);

    List<CalendarSpaceResponse> findAccessibleSpaces(long userId);
}
