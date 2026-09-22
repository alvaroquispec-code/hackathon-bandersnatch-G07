package com.tuckersoft.branchengine.service;

import com.tuckersoft.branchengine.dto.DtoMapper;
import com.tuckersoft.branchengine.dto.RoleUpdateRequest;
import com.tuckersoft.branchengine.dto.UserResponse;
import com.tuckersoft.branchengine.entity.User;
import com.tuckersoft.branchengine.exception.ApiException;
import com.tuckersoft.branchengine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Set<String> ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public UserResponse me() {
        return DtoMapper.toUser(currentUserService.get());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAllByOrderByIdAsc().stream().map(DtoMapper::toUser).toList();
    }

    @Transactional
    public UserResponse changeRole(Long id, RoleUpdateRequest req) {
        if (req == null || req.role() == null || !ROLES.contains(req.role())) {
            throw ApiException.badRequest("El rol debe ser ROLE_USER o ROLE_ADMIN");
        }
        User target = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
        User me = currentUserService.get();
        if (me.getId().equals(target.getId())) {
            throw ApiException.badRequest("Un administrador no puede cambiar su propio rol");
        }
        target.setRole(req.role());
        return DtoMapper.toUser(userRepository.save(target));
    }
}
