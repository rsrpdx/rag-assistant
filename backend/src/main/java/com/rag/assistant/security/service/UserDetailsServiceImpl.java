package com.rag.assistant.security.service;

import com.rag.assistant.module.user.mapper.UserMapper;
import com.rag.assistant.module.user.model.entity.User;
import com.rag.assistant.security.model.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new UsernameNotFoundException("账号已被禁用");
        }
        return new SecurityUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getStatus() == 1,
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
    }
}
