package com.vietspecialty.service;

import com.vietspecialty.dto.request.RegisterRequest;
import com.vietspecialty.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);
}

