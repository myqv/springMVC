package com.gupaoedu.mvcframework.controller;

import com.gupaoedu.mvcframework.annotation.GPAutowired;
import com.gupaoedu.mvcframework.annotation.GPController;
import com.gupaoedu.mvcframework.annotation.GPRequestMapping;
import com.gupaoedu.mvcframework.annotation.GPRequestParam;
import com.gupaoedu.mvcframework.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@GPController
@GPRequestMapping("/demo")
public class DemoController {
    @GPAutowired
    private DemoService demoService;

    @GPRequestMapping("/hello")
    public void hello(HttpServletRequest req, HttpServletResponse resp,
                      @GPRequestParam("name") String name) throws IOException {
        String result = demoService.sayHello();

        resp.getWriter().write(result);
    }

}
