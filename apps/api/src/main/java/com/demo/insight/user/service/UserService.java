package com.demo.insight.user.service;

import com.demo.insight.auth.service.RequestActorService;
import com.demo.insight.user.dto.ChangePasswordRequestDto;
import com.demo.insight.user.dto.CreateUserRequestDto;
import com.demo.insight.user.dto.UpdateUserRequestDto;
import com.demo.insight.user.dto.UserDto;

import java.util.List;

public interface UserService {

    List<UserDto> getUsers(RequestActorService.AuthenticatedActor actor);

    UserDto getUser(String empcode, RequestActorService.AuthenticatedActor actor);

    UserDto createUser(CreateUserRequestDto request, RequestActorService.AuthenticatedActor actor);

    UserDto updateUser(String empcode, UpdateUserRequestDto request, RequestActorService.AuthenticatedActor actor);

    void deleteUser(String empcode, RequestActorService.AuthenticatedActor actor);

    void changePassword(String empcode, ChangePasswordRequestDto request, RequestActorService.AuthenticatedActor actor);
}
