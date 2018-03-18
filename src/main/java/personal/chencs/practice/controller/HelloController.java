package personal.chencs.practice.controller;


import personal.chencs.practice.mvc.framework.annotation.Controller;
import personal.chencs.practice.mvc.framework.annotation.RequestMapping;
import personal.chencs.practice.mvc.framework.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class HelloController {

    @RequestMapping("/hello")
    public void hello(HttpServletRequest request, HttpServletResponse response,
                      @RequestParam("name") String name) {
        try {
            response.getWriter().write("hello, " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
