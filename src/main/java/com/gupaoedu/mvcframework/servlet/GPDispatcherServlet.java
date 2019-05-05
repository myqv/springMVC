package com.gupaoedu.mvcframework.servlet;

import com.gupaoedu.mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class GPDispatcherServlet extends HttpServlet {

    private static final  String LOCATION = "contextConfigLocation";

    private Properties p = new Properties();

    //保存所有被扫描到的相关的类名
    private List<String> classNames = new ArrayList<String>();

    //核心IOS容器，保存所有初始化的BEAN
    private Map<String,Object> ioc = new HashMap<String,Object>();

    //保存所有的Url和方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    public GPDispatcherServlet(){super();}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try{
            doDispatch(req,resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500 错误" + Arrays.toString(e.getStackTrace())
                    .replaceAll("\\[|\\]","").replaceAll(",\\s","\r\n"));

        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if(this.handlerMapping.isEmpty()){return;}

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 页面不存在");
        }

        Map<String,String[]> params = req.getParameterMap();
        Method method = this.handlerMapping.get(url);
        //获取方法的参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //获取请求参数
        Map<String,String[]> parameterMap = req.getParameterMap();
        //保存参数值
        Object[] paramValues = new Object[parameterTypes.length];
        //方法的参数列表
        for (int i=0;i<parameterTypes.length;i++){
            //根据参数名称，做某些处理
            Class parameterType = parameterTypes[i];
            if(parameterType==HttpServletRequest.class){
                //参数类型已明确，这边强转类型
                paramValues[i] = req;
                continue;
            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;
                continue;
            }else if(parameterType == String.class){
                for(Map.Entry<String,String[]> param:parameterMap.entrySet()){
                    String value = Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]","")
                            .replaceAll("\\s",",");
                    paramValues[i] = value;
                }
            }
        }

        try{
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            //利用反射机制来调用
            method.invoke(this.ioc.get(beanName),paramValues);
        }catch (Exception e){

        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加截配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2,扫描所有相关的类
        doScanner(p.getProperty("scanPackage"));

        //3.初始化所有相关类的实例，并保存到IOS容器中
        doInstance();

        //4,依赖注入
        diAutowired();

        //5.构造handlerMapping
        initHandlerMapping();

        System.out.println("ioc di mvc 加载成功");
    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){return;}

        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(GPController.class)){continue;}

            String baseUrl = "";
            //获取Controller的url配置
            if(clazz.isAnnotationPresent(GPRequestMapping.class)){
                GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取methods的url配置
            Method[] methods = clazz.getMethods();
            for(Method method : methods){

                //没有加requestMapping注解的直接忽略
                if(!method.isAnnotationPresent(GPRequestMapping.class)){continue;}

                //映射url
                GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);

            }
        }
    }

    private void diAutowired() {
        if(ioc.isEmpty()){return;}

        for(Map.Entry<String,Object> entry:ioc.entrySet()){
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for(Field field:fields){
                if(!field.isAnnotationPresent(GPAutowired.class)){continue;}

                GPAutowired gpAutowired = field.getAnnotation(GPAutowired.class);
                String beanName = gpAutowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);//设置私有属性的访问权限
                try{
                    field.set(entry.getValue(),ioc.get(beanName));
                }catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }

        }
    }

    private void doInstance() {
        if(classNames.size()==0){return;}

        try{
            for(String className:classNames){
                Class<?> clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(GPController.class)){
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(GPService.class)){
                    GPService service = clazz.getAnnotation(GPService.class);
                    String beanName = service.value();
                    if(!"".equals(beanName)){
                        ioc.put(beanName,clazz.newInstance());
                        continue;
                    }

                    //如果自己没设置GPService的VALUE,就按接口名创建实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for(Class<?> i :interfaces){
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                }else{
                    continue;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 把首字母变成小写
     * @param str
     * @return
     */
    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] +=32;
        return String.valueOf(chars);
    }

    private void doScanner(String packageName) {
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.","/"));
        File dir = new File(url.getFile());
        for(File file:dir.listFiles()){
            if(file.isDirectory()){
                doScanner(packageName + "." + file.getName());
            }else{
                classNames.add(packageName+"."+file.getName().replace(".class","").trim());
            }
        }
    }

    private void doLoadConfig(String location) {
        InputStream is = null;
        try {
            is = this.getClass().getClassLoader().getResourceAsStream(location);
            p.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
