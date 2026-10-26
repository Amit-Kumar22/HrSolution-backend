package com.hrsolution.notification.mapper;

import com.hrsolution.notification.dto.NotificationDtos;
import com.hrsolution.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper
public interface NotificationMapper {
    NotificationDtos.Response toResponse(Notification notification);
}
