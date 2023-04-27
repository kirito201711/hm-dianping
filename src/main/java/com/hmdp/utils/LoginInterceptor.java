package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;


public class LoginInterceptor implements HandlerInterceptor {


    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1.获取session
//        HttpSession session = request.getSession();
           //1.获取请求头中的token
        String token = request.getHeader("authorization");
        if (token==null){
            response.setStatus(401);  //返回401状态码
            return false;
        }
        //2.获取用token取得redis中的用户
//        Object user = session.getAttribute("user");
        Map<Object, Object>userMap = stringRedisTemplate.opsForHash().entries("login:token" + token);

        //3.判断用户是否存在
        if (userMap.isEmpty()){
            //4.不存在，拦截
            response.setStatus(401);  //返回401状态码

            return false;
        }

        //5.存在，将hashmap对象转为UserDTO对象，并保存用户信息到ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser((UserDTO) userDTO);
        //6.刷新token的有效期
         stringRedisTemplate.expire("login:token" + token,30, TimeUnit.MINUTES);
        //7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
      UserHolder.removeUser();
    }
}
