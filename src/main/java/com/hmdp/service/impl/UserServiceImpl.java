package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SendSms;
import com.hmdp.utils.ValidateCodeUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
 @Resource
 private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) throws Exception {
        //1.校验手机号
        if( RegexUtils.isPhoneInvalid(phone))
        //2.不符合，返回错误信息
         return  Result.fail("手机号格式错误");
        //3.符合,生成验证码
        String code = String.valueOf(ValidateCodeUtils.generateValidateCode(4));
        //4.保存验证码到session
//       session.setAttribute("code",code);
        //4.2保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES); //两分钟过期;
        //5.发送验证码
        SendSms.sendMessages(phone,code);
        log.debug("发送验证码成功！验证码为"+code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
             String phone = loginForm.getPhone();
        //1.校验手机号和验证码
             if( RegexUtils.isPhoneInvalid(phone))
            //2.不符合，返回错误信息

             return  Result.fail("手机号格式错误");
//        Object cachecode = session.getAttribute("code");

        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cachecode==null){
            log.debug("ca为空");
        }
        log.debug("验证码为"+cachecode.toString());

        String code = loginForm.getCode();
        if (cachecode==null||!cachecode.equals(code)){
            return Result.fail("验证码错误！");
        }

        //3.根据手机号查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(User::getPhone,phone);
        User user = this.getOne(queryWrapper);
        //4.判断用户是否存在
        if (user==null){

          user  =  creatUserWithPhone(phone);
        }
           //5.不存在,创建新用户并保存

           //6.保存用户信息到session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
           //6.2保存用户信息到redis中！
             //1.随机生成token,作为登录令牌
          String token = UUID.randomUUID().toString(true);
        //2.将user转为hashMap储存
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO);
        usermap.forEach((key, value) -> {
            if (null != value) usermap.put(key, String.valueOf(value));
        });
        stringRedisTemplate.opsForHash().putAll("login:token"+token,usermap);
        stringRedisTemplate.expire("login:token"+token,30,TimeUnit.MINUTES);
           //3.储存
           //7.存在,登陆成功

        return Result.ok(token);
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
