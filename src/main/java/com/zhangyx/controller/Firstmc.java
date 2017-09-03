package com.zhangyx.controller;

import com.zhangyx.com.zhangyx.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class Firstmc {
    @Autowired
    private UserService userService;

    @RequestMapping(value = "/home", method = RequestMethod.GET)
    @ResponseBody
    public String home() {
        return userService.queryName();
    }
}
