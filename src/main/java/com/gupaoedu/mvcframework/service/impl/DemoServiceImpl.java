package com.gupaoedu.mvcframework.service.impl;

import com.gupaoedu.mvcframework.annotation.GPService;
import com.gupaoedu.mvcframework.service.DemoService;

@GPService
public class DemoServiceImpl implements DemoService {
    public String sayHello() {
        return "hello alvin";
    }
}
